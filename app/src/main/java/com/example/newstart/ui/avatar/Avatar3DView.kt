package com.example.newstart.ui.avatar

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.opengl.Matrix
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.TextureView
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.example.newstart.util.PerformanceTelemetry
import com.google.android.filament.Renderer
import com.google.android.filament.View
import com.google.android.filament.gltfio.Animator
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.utils.Float3
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Persistent global 3D avatar view.
 * Supports profile selection (HQ/Lite), profile fallback, and role-based animation playback.
 */
class Avatar3DView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "Avatar3DView"
        private const val MANIFEST_PATH = "3d_avatar/avatar_manifest.json"
        private const val COMPAT_STATIC_ASSET_PATH = "3d_avatar/guide_avatar.glb"
        private const val COMPAT_LITE_ASSET_PATH = "3d_avatar/guide_avatar_lite.glb"
        private const val VM_COMPAT_ANIMATED_ASSET_PATH = "3d_avatar/guide_avatar_lite.glb"
        private const val VM_COMPAT_STATIC_ASSET_PATH = "3d_avatar/guide_avatar.glb"
        private const val DEFAULT_MODEL_OFFSET_Y = -0.46f
        private const val DEFAULT_MODEL_OFFSET_Z = -2.05f
        private const val DEFAULT_MODEL_ROTATE_Y = 180f
        private val SUPPRESS_NAME_KEYWORDS = listOf(
            "background",
            "floor",
            "stage",
            "plane",
            "helper",
            "camera",
            "light"
        )
    }

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val textureView = TextureView(context)

    private var modelViewer: ModelViewer? = null
    private var manifest: AvatarManifest = AvatarManifest.default()
    private var activeProfile: AvatarProfile? = null
    private var profileTimeoutJob: Job? = null
    private var rendererEnabled = true
    private var rendererInitialized = false

    private var avatarTapListener: (() -> Unit)? = null
    private var avatarDragListener: ((deltaX: Float, deltaY: Float, released: Boolean) -> Unit)? = null

    private var isRenderLoopRunning = false
    private var animationStartNanos = 0L
    private var currentAnimationIndex = 0
    private var isAnimationLooping = true
    private var currentRole = AvatarAnimRole.IDLE
    private var idleVariantIndices: List<Int> = emptyList()
    private var idleVariantCursor = 0
    private var idleVariantCycleNanos = 3_200_000_000L
    private var idleVariantLastSwitchNanos = 0L

    private var loadStartElapsedMs = 0L
    private var isFirstFrameReported = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f
    private var isDraggingAvatar = false
    private val tmpLocalMatrix = FloatArray(16)
    private val tmpRotateMatrix = FloatArray(16)
    private val tmpTransformMatrix = FloatArray(16)

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val viewer = modelViewer
            if (!isRenderLoopRunning || viewer == null) return

            if (animationStartNanos == 0L) {
                animationStartNanos = frameTimeNanos
            }

            val animator = viewer.animator
            if (animator != null && animator.animationCount > 0) {
                maybeAdvanceIdleAnimation(animator, frameTimeNanos)
                val elapsedSeconds = (frameTimeNanos - animationStartNanos) / 1_000_000_000.0f
                val validIndex = currentAnimationIndex.coerceIn(0, animator.animationCount - 1)
                animator.applyAnimation(validIndex, elapsedSeconds)
                animator.updateBoneMatrices()

                val duration = animator.getAnimationDuration(validIndex)
                if (!isAnimationLooping && elapsedSeconds >= duration) {
                    playRole(AvatarAnimRole.IDLE)
                }
            }

            if (!isFirstFrameReported && loadStartElapsedMs > 0L) {
                isFirstFrameReported = true
                profileTimeoutJob?.cancel()
                PerformanceTelemetry.recordDuration(
                    metric = "avatar_load_tti_ms",
                    startElapsedMs = loadStartElapsedMs,
                    attributes = mapOf("profile" to (activeProfile?.id ?: "unknown"))
                )
            }

            viewer.render(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        textureView.isOpaque = false
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!isDraggingAvatar) {
                    avatarTapListener?.invoke()
                }
                return true
            }
        })

        textureView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchRawX = event.rawX
                    lastTouchRawY = event.rawY
                    isDraggingAvatar = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - lastTouchRawX
                    val deltaY = event.rawY - lastTouchRawY
                    if (isDraggingAvatar || kotlin.math.hypot(deltaX.toDouble(), deltaY.toDouble()) >= touchSlop) {
                        isDraggingAvatar = true
                        avatarDragListener?.invoke(deltaX, deltaY, false)
                        lastTouchRawX = event.rawX
                        lastTouchRawY = event.rawY
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingAvatar) {
                        avatarDragListener?.invoke(0f, 0f, true)
                    }
                    isDraggingAvatar = false
                }
            }
            gestureDetector.onTouchEvent(event)
            true
        }

    }

    private fun initializeRenderer() {
        if (rendererInitialized || !rendererEnabled) {
            return
        }
        try {
            Utils.init()
            modelViewer = ModelViewer(textureView)

            val viewer = modelViewer ?: return
            viewer.cameraFocalLength = 45f
            runCatching { viewer.view.blendMode = View.BlendMode.TRANSLUCENT }
            applyTransparentRendererClear(viewer)

            loadFromManifest(loadManifestFromAssets())
            rendererInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize avatar renderer", e)
            rendererInitialized = false
        }
    }

    fun setRendererEnabled(enabled: Boolean) {
        rendererEnabled = enabled
        if (!enabled) {
            profileTimeoutJob?.cancel()
            stopRenderLoop()
            animationStartNanos = 0L
            idleVariantLastSwitchNanos = 0L
            idleVariantCursor = 0
            return
        }
        initializeRenderer()
        if (isAttachedToWindow) {
            startRenderLoop()
        }
    }

    fun setOnAvatarTapListener(listener: (() -> Unit)?) {
        avatarTapListener = listener
    }

    fun setOnAvatarDragListener(listener: ((deltaX: Float, deltaY: Float, released: Boolean) -> Unit)?) {
        avatarDragListener = listener
    }

    fun loadFromManifest(manifest: AvatarManifest) {
        this.manifest = manifest
        if (isCompatibilityPreferredDevice()) {
            Log.i(TAG, "Compatibility preferred device detected, prefer compatibility avatar profile")
            if (loadCompatibilityPreferredProfile()) {
                return
            }
            Log.w(TAG, "Compatibility avatar profile failed on compatibility preferred device")
        }
        if (isVirtualizedDevice()) {
            Log.i(TAG, "Virtualized device detected, prefer compatibility avatar profile")
            if (loadCompatibilityProfile()) {
                return
            }
            Log.w(TAG, "Compatibility avatar profile failed, fall back to manifest selection")
        }
        val selected = selectProfile(
            deviceRamGb = getDeviceRamGb(),
            isLowEndGpu = isLowEndGpu()
        )
        if (loadProfile(selected, reason = "auto_select")) {
            if (selected.id == "hq") {
                scheduleHqTimeoutFallback(manifest.selectionPolicy.firstFrameTimeoutMs)
            }
        } else {
            fallbackToLite("primary_load_failed")
        }
    }

    private fun loadCompatibilityProfile(): Boolean {
        val compatibilityProfiles = listOf(
            AvatarProfile(
                id = "vm_compat_animated",
                assetPath = VM_COMPAT_ANIMATED_ASSET_PATH
            ),
            AvatarProfile(
                id = "vm_compat_static",
                assetPath = VM_COMPAT_STATIC_ASSET_PATH
            )
        )
        for (profile in compatibilityProfiles) {
            if (loadProfile(profile, reason = "virtualized_device")) {
                return true
            }
        }
        return false
    }

    private fun loadCompatibilityPreferredProfile(): Boolean {
        val compatibilityProfiles = listOf(
            AvatarProfile(
                id = "compat_static",
                assetPath = COMPAT_STATIC_ASSET_PATH
            ),
            AvatarProfile(
                id = "compat_lite",
                assetPath = COMPAT_LITE_ASSET_PATH
            )
        )
        for (profile in compatibilityProfiles) {
            if (loadProfile(profile, reason = "compatibility_preferred_device")) {
                return true
            }
        }
        return false
    }

    fun selectProfile(deviceRamGb: Int, isLowEndGpu: Boolean): AvatarProfile {
        val enoughRam = deviceRamGb >= manifest.selectionPolicy.ramThresholdGb
        return if (enoughRam && !isLowEndGpu) {
            manifest.profiles.hq
        } else {
            manifest.profiles.lite
        }
    }

    fun playRole(role: AvatarAnimRole) {
        val animator = modelViewer?.animator ?: return
        if (animator.animationCount <= 0) return

        currentRole = role
        val targetName = when (role) {
            AvatarAnimRole.IDLE -> manifest.animations.idleName
            AvatarAnimRole.SPEAK -> manifest.animations.speakName
            AvatarAnimRole.EMPHASIS -> manifest.animations.emphasisName
        }

        val index = resolveAnimationIndex(animator, targetName, role) ?: return
        val loop = role != AvatarAnimRole.EMPHASIS
        val resolvedIndex = if (role == AvatarAnimRole.IDLE && idleVariantIndices.isNotEmpty()) {
            val idleAnchor = findAnimationIndexByName(animator, manifest.animations.idleName)
                ?: idleVariantIndices.first()
            val anchorIndex = idleVariantIndices.indexOf(idleAnchor).takeIf { it >= 0 } ?: 0
            idleVariantCursor = anchorIndex
            idleVariantLastSwitchNanos = 0L
            idleVariantIndices[idleVariantCursor]
        } else {
            index
        }
        if (currentAnimationIndex == resolvedIndex && isAnimationLooping == loop) return

        currentAnimationIndex = resolvedIndex
        isAnimationLooping = loop
        animationStartNanos = 0L
    }

    /**
     * Legacy compatibility API. Prefer [playRole].
     */
    fun playAnimation(index: Int, loop: Boolean = true) {
        val animator = modelViewer?.animator ?: return
        if (animator.animationCount <= 0) return

        currentAnimationIndex = index.coerceIn(0, animator.animationCount - 1)
        isAnimationLooping = loop
        animationStartNanos = 0L
    }

    fun playAnimationByName(nameFragment: String, loop: Boolean = true) {
        val animator = modelViewer?.animator ?: return
        for (i in 0 until animator.animationCount) {
            val animationName = animator.getAnimationName(i) ?: continue
            if (animationName.contains(nameFragment, ignoreCase = true)) {
                playAnimation(i, loop)
                return
            }
        }
        Log.w(TAG, "Animation containing '$nameFragment' not found")
    }

    fun startSpeaking() {
        playRole(AvatarAnimRole.SPEAK)
    }

    fun stopSpeaking() {
        playRole(AvatarAnimRole.IDLE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        initializeRenderer()
        startRenderLoop()
    }

    override fun onDetachedFromWindow() {
        stopRenderLoop()
        profileTimeoutJob?.cancel()
        super.onDetachedFromWindow()
    }

    private fun loadManifestFromAssets(): AvatarManifest {
        return try {
            val text = context.assets.open(MANIFEST_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
            AvatarManifest.fromJson(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load avatar manifest, using default manifest", e)
            AvatarManifest.default()
        }
    }

    private fun loadProfile(profile: AvatarProfile, reason: String): Boolean {
        if (!rendererEnabled) {
            Log.i(TAG, "Skip loading avatar profile=${profile.id}, renderer disabled")
            return false
        }
        return try {
            val bytes = context.assets.open(profile.assetPath).use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buffer.put(bytes)
            buffer.flip()

            modelViewer?.loadModelGlb(buffer)
            modelViewer?.transformToUnitCube(Float3(0.0f, DEFAULT_MODEL_OFFSET_Y, DEFAULT_MODEL_OFFSET_Z))
            modelViewer?.let { applyTransparentRendererClear(it) }
            val loadedAsset = modelViewer?.asset
            val suppressedCount = suppressBlacklistedRenderables(loadedAsset)
            if (suppressedCount > 0) {
                PerformanceTelemetry.record(
                    metric = "avatar_black_bg_detected_count",
                    value = suppressedCount.toDouble(),
                    unit = "count",
                    attributes = mapOf(
                        "profile" to profile.id,
                        "reason" to "blacklisted_renderable_hidden"
                    )
                )
            }
            val fallbackCount = applyAvatarMaterialSafety(loadedAsset)
            if (fallbackCount > 0) {
                PerformanceTelemetry.record(
                    metric = "avatar_material_fallback_count",
                    value = fallbackCount.toDouble(),
                    unit = "count",
                    attributes = mapOf("profile" to profile.id)
                )
            }
            applyAvatarOrientation(loadedAsset)

            activeProfile = profile
            loadStartElapsedMs = PerformanceTelemetry.nowElapsedMs()
            isFirstFrameReported = false

            val animator = modelViewer?.animator
            val count = animator?.animationCount ?: 0
            Log.i(TAG, "Loaded profile=${profile.id}, asset=${profile.assetPath}, animations=$count")
            for (i in 0 until count) {
                Log.d(TAG, "Animation[$i] ${animator?.getAnimationName(i)}")
            }
            animator?.let { rebuildIdleVariants(it) }
            recordRetargetSignals(animator, profile)

            PerformanceTelemetry.record(
                metric = "avatar_profile_selected",
                value = 1.0,
                unit = "count",
                attributes = mapOf(
                    "profile" to profile.id,
                    "reason" to reason
                )
            )

            startRenderLoop()
            playRole(AvatarAnimRole.IDLE)
            visibility = VISIBLE
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading profile=${profile.id}, path=${profile.assetPath}", e)
            false
        }
    }

    private fun fallbackToLite(reason: String) {
        if (activeProfile?.id == manifest.profiles.lite.id) {
            return
        }
        PerformanceTelemetry.record(
            metric = "avatar_fallback_to_lite_count",
            value = 1.0,
            unit = "count",
            attributes = mapOf("reason" to reason)
        )

        if (!loadProfile(manifest.profiles.lite, reason)) {
            Log.e(TAG, "Lite profile load failed, hide avatar")
            visibility = GONE
        }
    }

    private fun scheduleHqTimeoutFallback(timeoutMs: Long) {
        profileTimeoutJob?.cancel()
        profileTimeoutJob = uiScope.launch {
            delay(timeoutMs.coerceAtLeast(300L))
            if (!isFirstFrameReported && activeProfile?.id == manifest.profiles.hq.id) {
                Log.w(TAG, "HQ first frame timeout, switching to lite")
                fallbackToLite("first_frame_timeout")
            }
        }
    }

    private fun resolveAnimationIndex(
        animator: Animator,
        targetName: String,
        role: AvatarAnimRole
    ): Int? {
        val exact = findAnimationIndexByName(animator, targetName)
        if (exact != null) {
            return exact
        }

        PerformanceTelemetry.record(
            metric = "avatar_animation_miss_count",
            value = 1.0,
            unit = "count",
            attributes = mapOf(
                "profile" to (activeProfile?.id ?: "unknown"),
                "role" to role.name,
                "target" to targetName
            )
        )

        val idleFallback = if (role != AvatarAnimRole.IDLE) {
            findAnimationIndexByName(animator, manifest.animations.idleName)
        } else {
            null
        }

        return idleFallback ?: if (animator.animationCount > 0) 0 else null
    }

    private fun recordRetargetSignals(animator: Animator?, profile: AvatarProfile) {
        val required = listOf(
            manifest.animations.idleName,
            manifest.animations.speakName,
            manifest.animations.emphasisName
        ).filter { it.isNotBlank() }.distinct()
        val hitCount = if (animator == null) {
            0
        } else {
            required.count { findAnimationIndexByName(animator, it) != null }
        }
        val success = if (required.isNotEmpty() && hitCount == required.size) 1.0 else 0.0
        PerformanceTelemetry.record(
            metric = "avatar_retarget_success",
            value = success,
            unit = "count",
            attributes = mapOf(
                "profile" to profile.id,
                "hit" to hitCount.toString(),
                "required" to required.size.toString()
            )
        )
        profile.retargetBoneCoverage?.let { coverage ->
            PerformanceTelemetry.record(
                metric = "avatar_retarget_bone_coverage",
                value = coverage,
                unit = "percent",
                attributes = mapOf("profile" to profile.id)
            )
        }
    }

    private fun findAnimationIndexByName(animator: Animator, targetName: String): Int? {
        if (targetName.isBlank()) return null
        for (i in 0 until animator.animationCount) {
            val animationName = animator.getAnimationName(i) ?: continue
            if (animationName.equals(targetName, ignoreCase = true)) {
                return i
            }
        }
        for (i in 0 until animator.animationCount) {
            val animationName = animator.getAnimationName(i) ?: continue
            if (animationName.contains(targetName, ignoreCase = true)) {
                return i
            }
        }
        return null
    }

    private fun rebuildIdleVariants(animator: Animator) {
        val variants = manifest.animations.idleVariants.ifEmpty { listOf(manifest.animations.idleName) }
        val indices = variants.mapNotNull { findAnimationIndexByName(animator, it) }.distinct().toMutableList()
        if (indices.isEmpty() && animator.animationCount > 0) {
            indices += (findAnimationIndexByName(animator, manifest.animations.idleName) ?: 0)
        }
        idleVariantIndices = indices
        idleVariantCursor = 0
        idleVariantCycleNanos = (manifest.animations.idleCycleMs.coerceAtLeast(800L)) * 1_000_000L
        idleVariantLastSwitchNanos = 0L
    }

    private fun maybeAdvanceIdleAnimation(animator: Animator, frameTimeNanos: Long) {
        if (currentRole != AvatarAnimRole.IDLE || !isAnimationLooping) return
        if (idleVariantIndices.size <= 1) return
        if (idleVariantLastSwitchNanos == 0L) {
            idleVariantLastSwitchNanos = frameTimeNanos
            return
        }
        if (frameTimeNanos - idleVariantLastSwitchNanos < idleVariantCycleNanos) return
        idleVariantCursor = (idleVariantCursor + 1) % idleVariantIndices.size
        currentAnimationIndex = idleVariantIndices[idleVariantCursor]
        animationStartNanos = frameTimeNanos
        idleVariantLastSwitchNanos = frameTimeNanos
    }

    private fun applyTransparentRendererClear(viewer: ModelViewer) {
        runCatching {
            val clearOptions = Renderer.ClearOptions().apply {
                clear = true
                clearColor = floatArrayOf(0f, 0f, 0f, 0f)
            }
            viewer.renderer.clearOptions = clearOptions
            viewer.view.blendMode = View.BlendMode.TRANSLUCENT
            textureView.isOpaque = false
            setBackgroundColor(Color.TRANSPARENT)
        }.onFailure { error ->
            Log.w(TAG, "Failed to apply transparent clear", error)
        }
    }

    private fun suppressBlacklistedRenderables(asset: FilamentAsset?): Int {
        val viewer = modelViewer ?: return 0
        asset ?: return 0
        val renderableManager = viewer.engine.renderableManager
        var suppressed = 0
        for (entity in asset.renderableEntities) {
            val instance = renderableManager.getInstance(entity)
            if (instance == 0) continue
            val name = asset.getName(entity).orEmpty().lowercase(Locale.US)
            if (SUPPRESS_NAME_KEYWORDS.any { token -> name.contains(token) }) {
                renderableManager.setLayerMask(instance, 0xFF, 0x00)
                suppressed += 1
            }
        }
        return suppressed
    }

    private fun applyAvatarMaterialSafety(asset: FilamentAsset?): Int {
        val viewer = modelViewer ?: return 0
        asset ?: return 0
        val renderableManager = viewer.engine.renderableManager
        var fallbackCount = 0
        for (entity in asset.renderableEntities) {
            val instance = renderableManager.getInstance(entity)
            if (instance == 0) continue
            val primitiveCount = renderableManager.getPrimitiveCount(instance)
            for (primitive in 0 until primitiveCount) {
                val materialInstance = runCatching {
                    renderableManager.getMaterialInstanceAt(instance, primitive)
                }.getOrNull() ?: continue
                val material = materialInstance.material
                var touched = false
                if (material.hasParameter("roughnessFactor")) {
                    materialInstance.setParameter("roughnessFactor", 0.52f)
                    touched = true
                }
                if (material.hasParameter("metallicFactor")) {
                    materialInstance.setParameter("metallicFactor", 0.0f)
                    touched = true
                }
                if (material.hasParameter("emissiveFactor")) {
                    materialInstance.setParameter("emissiveFactor", 0.02f, 0.02f, 0.02f)
                    touched = true
                }
                if (!touched) {
                    fallbackCount += 1
                }
            }
        }
        return fallbackCount
    }

    private fun applyAvatarOrientation(asset: FilamentAsset?) {
        val viewer = modelViewer ?: return
        asset ?: return
        val rootEntity = asset.root
        if (rootEntity == 0) return
        val transformManager = viewer.engine.transformManager
        val rootInstance = transformManager.getInstance(rootEntity)
        if (rootInstance == 0) return
        runCatching {
            transformManager.getTransform(rootInstance, tmpLocalMatrix)
            Matrix.setIdentityM(tmpRotateMatrix, 0)
            Matrix.rotateM(tmpRotateMatrix, 0, DEFAULT_MODEL_ROTATE_Y, 0f, 1f, 0f)
            Matrix.multiplyMM(tmpTransformMatrix, 0, tmpLocalMatrix, 0, tmpRotateMatrix, 0)
            transformManager.setTransform(rootInstance, tmpTransformMatrix)
        }.onFailure { error ->
            Log.w(TAG, "Failed to apply avatar orientation correction", error)
        }
    }

    private fun getDeviceRamGb(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val ramBytes = memoryInfo.totalMem.coerceAtLeast(0L)
        return (ramBytes / (1024L * 1024L * 1024L)).toInt()
    }

    private fun isLowEndGpu(): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val reqGlEsVersion = activityManager.deviceConfigurationInfo?.reqGlEsVersion ?: 0
            reqGlEsVersion < 0x30000
        } catch (e: Exception) {
            false
        }
    }

    private fun isVirtualizedDevice(): Boolean {
        val signals = listOf(
            Build.FINGERPRINT,
            Build.MODEL,
            Build.MANUFACTURER,
            Build.BRAND,
            Build.PRODUCT,
            Build.DEVICE,
            Build.HARDWARE,
            Build.HOST
        ).joinToString(" ").lowercase(Locale.US)
        return listOf(
            "generic",
            "emulator",
            "sdk_gphone",
            "goldfish",
            "ranchu",
            "vbox",
            "virtualbox",
            "genymotion",
            "vmware",
            "simulator"
        ).any { token -> signals.contains(token) }
    }

    private fun isCompatibilityPreferredDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase(Locale.US)
        val model = Build.MODEL.orEmpty().lowercase(Locale.US)
        return manufacturer == "oppo" && model == "opd2405"
    }

    private fun startRenderLoop() {
        if (!rendererEnabled || isRenderLoopRunning || modelViewer == null) return
        isRenderLoopRunning = true
        animationStartNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopRenderLoop() {
        if (!isRenderLoopRunning) return
        isRenderLoopRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }
}
