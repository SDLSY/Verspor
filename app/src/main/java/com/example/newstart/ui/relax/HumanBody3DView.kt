package com.example.newstart.ui.relax

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.opengl.Matrix
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.TextureView
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.example.newstart.util.PerformanceTelemetry
import com.google.android.filament.Box
import com.google.android.filament.TransformManager
import com.google.android.filament.Renderer
import com.google.android.filament.View
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.utils.Float3
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class HumanBody3DView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class BodyZone {
        HEAD,
        NECK,
        CHEST,
        UPPER_BACK,
        ABDOMEN,
        LOWER_BACK,
        LEFT_ARM,
        RIGHT_ARM,
        LEFT_LEG,
        RIGHT_LEG;

                fun defaultLabel(): String {
            return when (this) {
                HEAD -> "头部"
                NECK -> "颈部"
                CHEST -> "胸部"
                UPPER_BACK -> "上背部"
                ABDOMEN -> "腹部"
                LOWER_BACK -> "下背部"
                LEFT_ARM -> "左臂"
                RIGHT_ARM -> "右臂"
                LEFT_LEG -> "左腿"
                RIGHT_LEG -> "右腿"
            }
        }
    }

    enum class ZonePickSource {
        RAY_PICK,
        FALLBACK,
        BUTTON,
        DETAIL_SHEET
    }

    enum class FocusAnimState {
        IDLE,
        FOCUSING,
        HOLDING,
        RETURNING
    }

    interface Callback {
        fun onZoneSelected(zone: BodyZone, source: ZonePickSource)
        fun onRendererUnavailable()
        fun onStageReady() {}
        fun onFocusStart(zone: BodyZone) {}
        fun onFocusEnd(zone: BodyZone) {}
    }

    data class ZoneFocusPreset(
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
        val holdMs: Long
    )

    data class CameraDefaults(
        val baseFocalLength: Float,
        val focusDurationMs: Long,
        val returnDurationMs: Long
    )

    data class CameraPose(
        val orbitHome: FloatArray,
        val lookAt: FloatArray
    )

    data class VisualTheme(
        val clearColor: Int,
        val directLight: Float,
        val emissiveStrength: Float,
        val preserveMaterial: Boolean
    )

    data class OrganLayerConfig(
        val layerCode: String,
        val displayName: String,
        val meshTokens: List<String>,
        val highlightColor: Int,
        val passiveAlpha: Float,
        val activeEmissive: Float,
        val anchorNodes: List<String>
    )

    private data class ZoneConfig(
        val zone: BodyZone,
        val displayName: String,
        val meshKeywords: List<String>,
        val anchorNodes: List<String>,
        val layerCode: String?,
        val highlightColor: Int,
        val focusPreset: ZoneFocusPreset
    )

    private data class ModelManifest(
        val assetPath: String,
        val unitCubeOffset: FloatArray,
        val defaultScale: Float,
        val defaultYawDeg: Float,
        val autoPlayAnimation: Boolean,
        val defaultAnimationName: String?,
        val autoPlayAnimationNames: List<String>,
        val autoPlaySwitchMs: Long,
        val visualTheme: VisualTheme,
        val cameraDefaults: CameraDefaults,
        val cameraPose: CameraPose,
        val organLayers: List<OrganLayerConfig>,
        val zones: List<ZoneConfig>
    )

    private data class PickResolution(
        val zone: BodyZone,
        val source: ZonePickSource,
        val highlightX: Float,
        val highlightY: Float
    )

    private val textureView = TextureView(context)
    private val overlayView = ZoneHighlightOverlayView(context)
    private var modelViewer: ModelViewer? = null
    private var callback: Callback? = null
    private var rendererEnabled = true
    private var avatarTapListener: (() -> Unit)? = null
    private var avatarDragListener: ((deltaX: Float, deltaY: Float, released: Boolean) -> Unit)? = null
    private var pickEnabled: Boolean = true
    private var avatarRoleOverrideIndex: Int? = null
    private var avatarRoleLooping: Boolean = true
    private var avatarRoleStartNanos: Long = 0L
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f
    private var isDraggingAvatar = false

    private var isRenderLoopRunning = false
    private var animationStartNanos = 0L
    private var initStartElapsedMs = 0L
    private var firstFrameRendered = false
    private var fpsWindowStartNanos = 0L
    private var fpsFrameCount = 0

    private var modelManifest: ModelManifest = defaultManifest()
    private var manifestAssetPath: String = MANIFEST_PATH
    private var autoFocusOnPick: Boolean = true
    private var autoPlayAnimationIndex: Int = 0
    private var autoPlayAnimationIndices: IntArray = intArrayOf(0)
    private var autoPlayAnimationCursor: Int = 0
    private var autoPlayLastSwitchNanos: Long = 0L
    private val zoneConfigMap = linkedMapOf<BodyZone, ZoneConfig>()
    private val organLayerMap = linkedMapOf<String, OrganLayerConfig>()
    private val renderableToZone = mutableMapOf<Int, BodyZone>()
    private val renderableToLayer = mutableMapOf<Int, String>()
    private val zoneRenderableEntities = mutableMapOf<BodyZone, MutableSet<Int>>()
    private val layerRenderableEntities = mutableMapOf<String, MutableSet<Int>>()
    private val zoneAnchorEntities = mutableMapOf<BodyZone, List<Int>>()
    private val layerAnchorEntities = mutableMapOf<String, List<Int>>()
    private val renderableNames = mutableMapOf<Int, String>()
    private var selectedZone: BodyZone? = null
    private var followProjectedAnchor = false
    private var interactionLocked = false
    private var hasExplicitLeftMarkers = false
    private var hasExplicitRightMarkers = false
    private var focusAnimState = FocusAnimState.IDLE
    private var focusAnimStateStartNanos = 0L
    private var focusZone: BodyZone? = null
    private var focusFromMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private var focusToMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private var rootBaseMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private var focusFromOrbit = floatArrayOf(0f, 0f, 3f)
    private var focusToOrbit = floatArrayOf(0f, 0f, 3f)
    private var focusFromLookAt = floatArrayOf(0f, 0f, 0f)
    private var focusToLookAt = floatArrayOf(0f, 0f, 0f)
    private var focusFromFocalLength = 44f
    private var focusToFocalLength = 44f
    private var viewportFocusPoint = PointF()
    private var focusEntry = "unknown"
    private var focusLatencyStartElapsedMs = 0L
    private var stageModeTag = "primary_3d"

    private val tmpViewMatrix = FloatArray(16)
    private val tmpProjectionMatrix = FloatArray(16)
    private val tmpVpMatrix = FloatArray(16)
    private val tmpWorld = FloatArray(4)
    private val tmpClip = FloatArray(4)
    private val tmpScaleMatrix = FloatArray(16)
    private val tmpRotateMatrix = FloatArray(16)
    private val tmpTranslateMatrix = FloatArray(16)
    private val tmpTransformMatrix = FloatArray(16)
    private val tmpRootMatrix = FloatArray(16)
    private val tmpAnimMatrix = FloatArray(16)
    private val tmpLerpMatrix = FloatArray(16)
    private val tmpRenderableAabb = Box()

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                avatarTapListener?.let {
                    if (!isDraggingAvatar) {
                        it.invoke()
                    }
                    return true
                }
                if (!pickEnabled) {
                    return true
                }
                return pickBodyZone(e)
            }
        }
    )

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val viewer = modelViewer
            if (!isRenderLoopRunning || viewer == null) {
                return
            }

            if (!firstFrameRendered) {
                firstFrameRendered = true
                recordDurationTelemetry(
                    metric = "3d_tti",
                    startElapsedMs = initStartElapsedMs,
                    attributes = mapOf("mode" to "filament")
                )
                callback?.onStageReady()
            }

            viewer.animator?.let { animator ->
                if (animator.animationCount > 0) {
                    val overrideIndex = avatarRoleOverrideIndex
                    if (overrideIndex != null) {
                        if (avatarRoleStartNanos == 0L) {
                            avatarRoleStartNanos = frameTimeNanos
                        }
                        val elapsedSeconds =
                            (frameTimeNanos - avatarRoleStartNanos) / 1_000_000_000.0f
                        val animationIndex = overrideIndex.coerceIn(0, animator.animationCount - 1)
                        animator.applyAnimation(animationIndex, elapsedSeconds)
                        animator.updateBoneMatrices()
                        if (!avatarRoleLooping) {
                            val duration = animator.getAnimationDuration(animationIndex)
                            if (elapsedSeconds >= duration) {
                                playRole("IDLE")
                            }
                        }
                    } else if (modelManifest.autoPlayAnimation) {
                        if (animationStartNanos == 0L) {
                            animationStartNanos = frameTimeNanos
                            autoPlayLastSwitchNanos = frameTimeNanos
                        }
                        val switchMs = modelManifest.autoPlaySwitchMs.coerceAtLeast(800L)
                        if (autoPlayAnimationIndices.size > 1 &&
                            frameTimeNanos - autoPlayLastSwitchNanos >= switchMs * 1_000_000L
                        ) {
                            autoPlayAnimationCursor =
                                (autoPlayAnimationCursor + 1) % autoPlayAnimationIndices.size
                            autoPlayAnimationIndex = autoPlayAnimationIndices[autoPlayAnimationCursor]
                            animationStartNanos = frameTimeNanos
                            autoPlayLastSwitchNanos = frameTimeNanos
                        }
                        val elapsedSeconds =
                            (frameTimeNanos - animationStartNanos) / 1_000_000_000.0f
                        val animationIndex = autoPlayAnimationIndex.coerceIn(0, animator.animationCount - 1)
                        animator.applyAnimation(animationIndex, elapsedSeconds)
                        animator.updateBoneMatrices()
                    }
                }
            }

            updateFocusAnimation(frameTimeNanos)
            viewer.render(frameTimeNanos)
            if (followProjectedAnchor) {
                selectedZone?.let { zone ->
                    projectZoneAnchor(zone)?.let { p ->
                        overlayView.updatePosition(p.x, p.y)
                    }
                }
            }
            captureFps(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        textureView.isOpaque = false
        addView(
            textureView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(
            overlayView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        textureView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchRawX = event.rawX
                    lastTouchRawY = event.rawY
                    isDraggingAvatar = false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (avatarDragListener != null) {
                        val deltaX = event.rawX - lastTouchRawX
                        val deltaY = event.rawY - lastTouchRawY
                        if (isDraggingAvatar || hypot(deltaX.toDouble(), deltaY.toDouble()) >= touchSlop) {
                            isDraggingAvatar = true
                            avatarDragListener?.invoke(deltaX, deltaY, false)
                            lastTouchRawX = event.rawX
                            lastTouchRawY = event.rawY
                        }
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
            if (interactionLocked) {
                true
            } else {
                gestureDetector.onTouchEvent(event)
                if (!isDraggingAvatar) {
                    modelViewer?.onTouchEvent(event)
                }
                true
            }
        }
    }

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    fun setOnAvatarTapListener(listener: (() -> Unit)?) {
        avatarTapListener = listener
    }

    fun setOnAvatarDragListener(listener: ((deltaX: Float, deltaY: Float, released: Boolean) -> Unit)?) {
        avatarDragListener = listener
    }

    fun setPickEnabled(enabled: Boolean) {
        pickEnabled = enabled
    }

    fun setStageModeTag(tag: String) {
        stageModeTag = if (tag.isBlank()) "primary_3d" else tag
    }

    fun setManifestAssetPath(path: String) {
        val normalized = path.trim().ifBlank { MANIFEST_PATH }
        if (manifestAssetPath == normalized) {
            return
        }
        Log.d(TAG, "setManifestAssetPath: $manifestAssetPath -> $normalized")
        manifestAssetPath = normalized
        post {
            if (modelViewer == null) return@post
            runCatching {
                reloadManifestAndModel(resetSelection = true)
            }.onFailure { error ->
                Log.e(TAG, "setManifestAssetPath failed: $normalized", error)
                callback?.onRendererUnavailable()
            }
        }
    }

    fun setAutoFocusOnPick(enabled: Boolean) {
        autoFocusOnPick = enabled
    }

    fun playRole(roleName: String) {
        val animator = modelViewer?.animator ?: return
        if (animator.animationCount <= 0) return

        val normalizedRole = roleName.trim().uppercase(Locale.US)
        if (normalizedRole == "IDLE") {
            avatarRoleOverrideIndex = null
            avatarRoleLooping = true
            avatarRoleStartNanos = 0L
            animationStartNanos = 0L
            autoPlayLastSwitchNanos = 0L
            return
        }

        val candidates = when (normalizedRole) {
            "SPEAK" -> listOf(
                "Guide Left_7",
                "Pointing Forward_1",
                "Friendly Wave_4",
                modelManifest.defaultAnimationName.orEmpty()
            )
            "EMPHASIS" -> listOf(
                "Cheer Pose_6",
                "Jumping Down_3",
                "Friendly Wave_4",
                "Waving Gesture_2"
            )
            else -> emptyList()
        }.filter { it.isNotBlank() }

        val resolved = resolveRoleAnimationIndex(animator, candidates) ?: return
        avatarRoleOverrideIndex = resolved
        avatarRoleLooping = normalizedRole != "EMPHASIS"
        avatarRoleStartNanos = 0L
    }

    fun playAnimationByName(nameFragment: String, loop: Boolean = true) {
        val animator = modelViewer?.animator ?: return
        if (animator.animationCount <= 0) return
        val fragment = nameFragment.trim()
        if (fragment.isBlank()) return

        for (index in 0 until animator.animationCount) {
            val animationName = animator.getAnimationName(index) ?: continue
            if (animationName.contains(fragment, ignoreCase = true)) {
                avatarRoleOverrideIndex = index
                avatarRoleLooping = loop
                avatarRoleStartNanos = 0L
                return
            }
        }
        Log.w(TAG, "Animation containing '$fragment' not found for stageMode=$stageModeTag")
    }

    fun focusZone(zone: BodyZone, useProjectedAnchor: Boolean = false) {
        post {
            selectedZone = zone
            followProjectedAnchor = useProjectedAnchor
            viewportFocusPoint = resolveOverlayPoint(zone, preferProjected = useProjectedAnchor)
            showZoneOverlay(zone, preferProjected = useProjectedAnchor, labelOverride = null)
            applyZoneHighlight(zone)
            if (useProjectedAnchor) {
                applyViewportFocusTransform(zone, 1f)
            } else {
                resetViewportFocusTransform()
            }
        }
    }

    fun showFixedCloseup(zone: BodyZone, labelOverride: String? = null) {
        post {
            Log.d(TAG, "showFixedCloseup zone=$zone labelOverride=$labelOverride manifest=$manifestAssetPath")
            selectedZone = zone
            followProjectedAnchor = false
            focusAnimState = FocusAnimState.IDLE
            focusZone = null
            interactionLocked = false
            val point = fixedOverlayPoint(zone)
            viewportFocusPoint = point
            overlayView.show(
                label = labelOverride ?: zone.defaultLabel(),
                color = zoneConfigMap[zone]?.highlightColor ?: DEFAULT_HIGHLIGHT_COLOR,
                x = point.x,
                y = point.y
            )
            applyZoneHighlight(zone)
            applyFixedViewportCloseup(zone)
        }
    }

    fun focusZoneCinematic(zone: BodyZone, source: ZonePickSource) {
        post {
            selectedZone = zone
            followProjectedAnchor = true
            val config = zoneConfigMap[zone]
            val preset = config?.focusPreset ?: defaultFocusPreset(zone)
            val labelOverride = if (source == ZonePickSource.BUTTON && zone == BodyZone.LEFT_LEG) {
                "四肢"
            } else {
                null
            }
            viewportFocusPoint = resolveOverlayPoint(zone, preferProjected = true)
            showZoneOverlay(zone, preferProjected = true, labelOverride = labelOverride)
            applyZoneHighlight(zone)

            val viewer = modelViewer ?: return@post
            val asset = viewer.asset ?: return@post
            val rootInstance = getRootInstance(viewer, asset) ?: return@post

            val nowElapsed = PerformanceTelemetry.nowElapsedMs()
            if (focusAnimState != FocusAnimState.IDLE) {
                recordTelemetry(
                    metric = "3d_focus_cancel_count",
                    value = 1.0,
                    unit = "count",
                    attributes = mapOf("zone" to (focusZone?.name ?: "UNKNOWN"), "entry" to focusEntry)
                )
            }

            viewer.engine.transformManager.getWorldTransform(rootInstance, tmpRootMatrix)
            focusFromMatrix = tmpRootMatrix.copyOf()
            val base = rootBaseMatrix.copyOf()
            focusToMatrix = composeFocusMatrix(base, preset)
            focusFromOrbit = modelManifest.cameraPose.orbitHome.copyOf()
            focusFromLookAt = modelManifest.cameraPose.lookAt.copyOf()
            focusToOrbit = focusFromOrbit.copyOf()
            focusToLookAt = focusFromLookAt.copyOf()
            focusFromFocalLength = max(modelManifest.cameraDefaults.baseFocalLength, 44.0f)
            focusToFocalLength = focusFromFocalLength

            focusAnimState = FocusAnimState.FOCUSING
            focusAnimStateStartNanos = System.nanoTime()
            focusZone = zone
            focusEntry = entryFromSource(source)
            focusLatencyStartElapsedMs = nowElapsed
            interactionLocked = true

            callback?.onFocusStart(zone)
            recordTelemetry(
                metric = "3d_focus_start_count",
                value = 1.0,
                unit = "count",
                attributes = mapOf("zone" to zone.name, "entry" to focusEntry)
            )
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!rendererEnabled) {
            return
        }
        initializeRendererIfNeeded()
        startRenderLoop()
    }

    override fun onDetachedFromWindow() {
        stopRenderLoop()
        selectedZone = null
        followProjectedAnchor = false
        interactionLocked = false
        focusAnimState = FocusAnimState.IDLE
        overlayView.hide()
        // Avoid native assertion on some devices during rapid fragment transitions.
        modelViewer = null
        super.onDetachedFromWindow()
    }

    fun setRendererEnabled(enabled: Boolean) {
        rendererEnabled = enabled
        if (!enabled) {
            stopRenderLoop()
            selectedZone = null
            followProjectedAnchor = false
            interactionLocked = false
            focusAnimState = FocusAnimState.IDLE
            overlayView.hide()
            modelViewer = null
            return
        }
        if (isAttachedToWindow) {
            initializeRendererIfNeeded()
            startRenderLoop()
        }
    }

    private fun initializeRendererIfNeeded() {
        if (modelViewer != null) {
            return
        }
        initStartElapsedMs = PerformanceTelemetry.nowElapsedMs()
        try {
            Utils.init()
            modelViewer = ModelViewer(textureView)
            reloadManifestAndModel(resetSelection = true)
        } catch (t: Throwable) {
            Log.e(TAG, "3D renderer init failed", t)
            recordTelemetry(
                metric = "3d_init_failed",
                value = 1.0,
                unit = "count"
            )
            callback?.onRendererUnavailable()
        }
    }

    private fun reloadManifestAndModel(resetSelection: Boolean) {
        modelManifest = normalizeManifestForRuntime(loadModelManifest(manifestAssetPath))
        zoneConfigMap.clear()
        organLayerMap.clear()
        modelManifest.zones.forEach { zoneConfigMap[it.zone] = it }
        modelManifest.organLayers.forEach { organLayerMap[it.layerCode] = it }
        if (resetSelection) {
            selectedZone = null
            overlayView.hide()
        }
        followProjectedAnchor = false
        interactionLocked = false
        focusAnimState = FocusAnimState.IDLE
        focusZone = null
        initStartElapsedMs = PerformanceTelemetry.nowElapsedMs()
        firstFrameRendered = false
        loadModelFromManifest()
    }

    private fun loadModelFromManifest() {
        val bytes = context.assets.open(modelManifest.assetPath).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buffer.put(bytes)
        buffer.flip()
        val viewer = modelViewer ?: return
        viewer.loadModelGlb(buffer)
        autoPlayAnimationIndex = resolveAutoPlayAnimationIndex(viewer.animator, modelManifest.defaultAnimationName)
        autoPlayAnimationIndices = resolveAutoPlayAnimationIndices(
            animator = viewer.animator,
            animationNames = modelManifest.autoPlayAnimationNames,
            fallbackIndex = autoPlayAnimationIndex
        )
        autoPlayAnimationCursor = 0
        autoPlayAnimationIndex = autoPlayAnimationIndices.firstOrNull() ?: autoPlayAnimationIndex
        autoPlayLastSwitchNanos = 0L
        Log.i(
            TAG,
            "loadModelFromManifest asset=${modelManifest.assetPath}, animations=${viewer.animator?.animationCount ?: 0}, renderables=${viewer.asset?.renderableEntities?.size ?: 0}, preserveMaterial=${modelManifest.visualTheme.preserveMaterial}"
        )
        val unitCubeOffset = modelManifest.unitCubeOffset
        viewer.transformToUnitCube(
            Float3(
                unitCubeOffset.getOrElse(0) { 0.0f },
                unitCubeOffset.getOrElse(1) { -0.06f },
                unitCubeOffset.getOrElse(2) { -2.55f }
            )
        )
        applyManifestVisualTheme(viewer)
        applyDefaultScale(viewer, viewer.asset)
        applyCameraPose(viewer)
        viewer.cameraFocalLength = max(modelManifest.cameraDefaults.baseFocalLength, 44.0f)
        captureRootBaseTransform()
        rebuildZoneMappings(viewer.asset)
        applyMedicalVisualStyle(viewer.asset)
        applyZoneHighlight(selectedZone)
    }

    private fun applyManifestVisualTheme(viewer: ModelViewer) {
        val effectiveClearColor = effectiveClearColor()
        val blendMode = if (Color.alpha(effectiveClearColor) < 255) {
            View.BlendMode.TRANSLUCENT
        } else {
            View.BlendMode.OPAQUE
        }
        runCatching { viewer.view.blendMode = blendMode }
        setBackgroundColor(effectiveClearColor)
        runCatching { textureView.setBackgroundColor(effectiveClearColor) }
            .onFailure { Log.d(TAG, "TextureView background color is not supported on this device") }
        applyRendererClearColor(viewer, effectiveClearColor)
    }

    private fun effectiveClearColor(): Int {
        return if (stageModeTag == "global_avatar") {
            Color.TRANSPARENT
        } else {
            modelManifest.visualTheme.clearColor
        }
    }

    private fun applyRendererClearColor(viewer: ModelViewer, color: Int) {
        runCatching {
            val translucent = Color.alpha(color) < 255
            val clearOptions = Renderer.ClearOptions().apply {
                clear = true
                clearColor =
                floatArrayOf(
                    Color.red(color) / 255f,
                    Color.green(color) / 255f,
                    Color.blue(color) / 255f,
                    Color.alpha(color) / 255f
                )
            }
            viewer.renderer.clearOptions = clearOptions
            runCatching {
                viewer.view.blendMode = if (translucent) {
                    View.BlendMode.TRANSLUCENT
                } else {
                    View.BlendMode.OPAQUE
                }
            }
            textureView.isOpaque = !translucent
            if (translucent || !modelManifest.visualTheme.preserveMaterial) {
                runCatching { viewer.scene.skybox = null }
            }
            if (translucent) {
                setBackgroundColor(Color.TRANSPARENT)
                runCatching { textureView.setBackgroundColor(Color.TRANSPARENT) }
            }
        }.onFailure {
            Log.d(TAG, "applyRendererClearColor skipped", it)
        }
    }

    private fun applyDefaultScale(viewer: ModelViewer, asset: FilamentAsset?) {
        val scale = modelManifest.defaultScale.coerceIn(0.8f, 1.8f)
        val yawDeg = modelManifest.defaultYawDeg
        if (scale == 1f) {
            if (abs(yawDeg) < 0.01f) return
        }
        val safeAsset = asset ?: return
        val rootInstance = getRootInstance(viewer, safeAsset) ?: return
        val tm = viewer.engine.transformManager
        tm.getWorldTransform(rootInstance, tmpRootMatrix)
        Matrix.setIdentityM(tmpScaleMatrix, 0)
        Matrix.scaleM(tmpScaleMatrix, 0, scale, scale, scale)
        Matrix.multiplyMM(tmpTransformMatrix, 0, tmpRootMatrix, 0, tmpScaleMatrix, 0)
        if (abs(yawDeg) > 0.01f) {
            Matrix.setIdentityM(tmpRotateMatrix, 0)
            Matrix.rotateM(tmpRotateMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.multiplyMM(tmpLerpMatrix, 0, tmpTransformMatrix, 0, tmpRotateMatrix, 0)
            tm.setTransform(rootInstance, tmpLerpMatrix)
        } else {
            tm.setTransform(rootInstance, tmpTransformMatrix)
        }
    }

    private fun applyCameraPose(viewer: ModelViewer) {
        val orbit = modelManifest.cameraPose.orbitHome
        val lookAt = modelManifest.cameraPose.lookAt
        applyCameraPose(viewer, orbit, lookAt)
    }

    private fun applyCameraPose(viewer: ModelViewer, orbit: FloatArray, lookAt: FloatArray) {
        if (orbit.size < 3 || lookAt.size < 3) return
        runCatching {
            viewer.camera.lookAt(
                orbit[0].toDouble(),
                orbit[1].toDouble(),
                orbit[2].toDouble(),
                lookAt[0].toDouble(),
                lookAt[1].toDouble(),
                lookAt[2].toDouble(),
                0.0,
                1.0,
                0.0
            )
        }.onFailure {
            Log.w(TAG, "applyCameraPose failed, fallback to default camera", it)
        }
    }

    private fun applyInterpolatedCameraPose(
        viewer: ModelViewer,
        fromOrbit: FloatArray,
        toOrbit: FloatArray,
        fromLookAt: FloatArray,
        toLookAt: FloatArray,
        t: Float
    ) {
        if (fromOrbit.size < 3 || toOrbit.size < 3 || fromLookAt.size < 3 || toLookAt.size < 3) return
        val orbit = floatArrayOf(
            fromOrbit[0] + (toOrbit[0] - fromOrbit[0]) * t,
            fromOrbit[1] + (toOrbit[1] - fromOrbit[1]) * t,
            fromOrbit[2] + (toOrbit[2] - fromOrbit[2]) * t
        )
        val lookAt = floatArrayOf(
            fromLookAt[0] + (toLookAt[0] - fromLookAt[0]) * t,
            fromLookAt[1] + (toLookAt[1] - fromLookAt[1]) * t,
            fromLookAt[2] + (toLookAt[2] - fromLookAt[2]) * t
        )
        applyCameraPose(viewer, orbit, lookAt)
    }

    private fun startRenderLoop() {
        if (isRenderLoopRunning) return
        if (modelViewer == null) return
        isRenderLoopRunning = true
        animationStartNanos = 0L
        firstFrameRendered = false
        fpsWindowStartNanos = 0L
        fpsFrameCount = 0
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopRenderLoop() {
        if (!isRenderLoopRunning) return
        isRenderLoopRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun captureFps(frameTimeNanos: Long) {
        if (fpsWindowStartNanos == 0L) {
            fpsWindowStartNanos = frameTimeNanos
            fpsFrameCount = 0
            return
        }
        fpsFrameCount += 1
        val delta = frameTimeNanos - fpsWindowStartNanos
        if (delta >= 2_000_000_000L) {
            val fps = fpsFrameCount * 1_000_000_000.0 / delta.toDouble()
            recordTelemetry(
                metric = "3d_fps",
                value = fps,
                unit = "fps"
            )
            fpsWindowStartNanos = frameTimeNanos
            fpsFrameCount = 0
        }
    }

    private fun pickBodyZone(event: MotionEvent): Boolean {
        if (interactionLocked) return true
        val viewer = modelViewer ?: return false
        val startMs = SystemClock.elapsedRealtime()
        val touchX = event.x.coerceIn(0f, max(width - 1, 0).toFloat())
        val touchY = event.y.coerceIn(0f, max(height - 1, 0).toFloat())
        val pickX = touchX.toInt()
        val pickY = (height - touchY).toInt().coerceIn(0, max(height - 1, 0))

        viewer.view.pick(
            pickX,
            pickY,
            Any(),
            object : View.OnPickCallback {
                override fun onPick(result: View.PickingQueryResult) {
                    post {
                        val resolved = resolvePickResult(result, touchX, touchY)
                        if (autoFocusOnPick) {
                            selectedZone = resolved.zone
                            followProjectedAnchor = true
                            focusZoneCinematic(resolved.zone, resolved.source)
                        }
                        recordPickMetrics(startMs, resolved.source)
                        callback?.onZoneSelected(resolved.zone, resolved.source)
                    }
                }
            }
        )
        return true
    }

    private fun resolvePickResult(
        result: View.PickingQueryResult,
        touchX: Float,
        touchY: Float
    ): PickResolution {
        val fallbackZone = fallbackZoneByTouchPoint(touchX, touchY)
        val fragX = result.fragCoords[0].coerceIn(0f, width.toFloat())
        val fragY = (height - result.fragCoords[1]).coerceIn(0f, height.toFloat())

        if (result.renderable > 0) {
            resolveZoneFromRenderable(result.renderable, touchX)?.let { zone ->
                return PickResolution(zone, ZonePickSource.RAY_PICK, fragX, fragY)
            }
            resolveZoneFromNearestAnchor(fragX, fragY)?.let { zone ->
                return PickResolution(zone, ZonePickSource.RAY_PICK, fragX, fragY)
            }
        }

        return PickResolution(
            zone = fallbackZone,
            source = ZonePickSource.FALLBACK,
            highlightX = touchX,
            highlightY = touchY
        )
    }

    private fun resolveZoneFromRenderable(entity: Int, touchX: Float): BodyZone? {
        if (entity <= 0) return null
        val name = renderableNames[entity].orEmpty()
        renderableToZone[entity]?.let { cached ->
            return applySideOverride(cached, name, touchX)
        }

        val matched = matchZoneByName(name) ?: return null
        return applySideOverride(matched, name, touchX)
    }

    private fun resolveZoneFromNearestAnchor(screenX: Float, screenY: Float): BodyZone? {
        var best: BodyZone? = null
        var bestDistance = Float.MAX_VALUE
        for (zone in modelManifest.zones.map { it.zone }) {
            val p = projectZoneAnchor(zone) ?: continue
            val d = hypot(p.x - screenX, p.y - screenY)
            if (d < bestDistance) {
                bestDistance = d
                best = zone
            }
        }

        val threshold = min(width, height) * 0.35f
        if (best == null || bestDistance > threshold) {
            return null
        }
        return when (best) {
            BodyZone.RIGHT_ARM -> if (screenX < width * 0.5f) BodyZone.LEFT_ARM else BodyZone.RIGHT_ARM
            BodyZone.RIGHT_LEG -> if (screenX < width * 0.5f) BodyZone.LEFT_LEG else BodyZone.RIGHT_LEG
            BodyZone.LEFT_ARM -> if (screenX >= width * 0.5f) BodyZone.RIGHT_ARM else BodyZone.LEFT_ARM
            BodyZone.LEFT_LEG -> if (screenX >= width * 0.5f) BodyZone.RIGHT_LEG else BodyZone.LEFT_LEG
            else -> best
        }
    }

    private fun fallbackZoneByTouchPoint(touchX: Float, touchY: Float): BodyZone {
        val ratio = touchY / max(height.toFloat(), 1f)
        return when {
            ratio <= 0.12f -> BodyZone.HEAD
            ratio <= 0.20f -> BodyZone.NECK
            ratio <= 0.37f -> BodyZone.CHEST
            ratio <= 0.50f -> BodyZone.UPPER_BACK
            ratio <= 0.62f -> BodyZone.ABDOMEN
            ratio <= 0.72f -> BodyZone.LOWER_BACK
            ratio <= 0.82f -> if (touchX < width * 0.5f) BodyZone.LEFT_ARM else BodyZone.RIGHT_ARM
            else -> if (touchX < width * 0.5f) BodyZone.LEFT_LEG else BodyZone.RIGHT_LEG
        }
    }

    private fun projectZoneAnchor(zone: BodyZone): PointF? {
        resolveLayerCodeForZone(zone)?.let { layerCode ->
            projectLayerAnchor(layerCode)?.let { return it }
        }
        projectZoneAnchorDirect(zone)?.let { return it }
        return projectMirroredAnchor(zone)
    }

    private fun projectLayerAnchor(layerCode: String): PointF? {
        val anchors = layerAnchorEntities[layerCode].orEmpty()
        for (entity in anchors) {
            val p = projectEntityToScreen(entity)
            if (p != null) {
                return p
            }
        }
        return null
    }

    private fun showZoneOverlay(zone: BodyZone, preferProjected: Boolean, labelOverride: String?) {
        val config = zoneConfigMap[zone]
        val point = resolveOverlayPoint(zone, preferProjected)
        val safePoint = clampToViewport(point)
        overlayView.show(
            label = labelOverride ?: config?.displayName ?: zone.defaultLabel(),
            color = config?.highlightColor ?: DEFAULT_HIGHLIGHT_COLOR,
            x = safePoint.x,
            y = safePoint.y
        )
    }

    private fun resolveOverlayPoint(zone: BodyZone, preferProjected: Boolean): PointF {
        val fallbackPoint = defaultZonePoint(zone)
        if (!preferProjected) return fallbackPoint
        val projectedPoint = projectZoneAnchor(zone) ?: return fallbackPoint
        return if (isProjectedAnchorPlausible(zone, projectedPoint, fallbackPoint)) {
            projectedPoint
        } else {
            fallbackPoint
        }
    }

    private fun isProjectedAnchorPlausible(zone: BodyZone, projected: PointF, fallback: PointF): Boolean {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val maxDx = when (zone) {
            BodyZone.LEFT_ARM, BodyZone.RIGHT_ARM -> w * 0.22f
            BodyZone.LEFT_LEG, BodyZone.RIGHT_LEG -> w * 0.20f
            else -> w * 0.18f
        }
        val maxDy = when (zone) {
            BodyZone.HEAD -> h * 0.12f
            BodyZone.NECK -> h * 0.12f
            BodyZone.CHEST, BodyZone.UPPER_BACK -> h * 0.14f
            BodyZone.ABDOMEN, BodyZone.LOWER_BACK -> h * 0.14f
            BodyZone.LEFT_ARM, BodyZone.RIGHT_ARM -> h * 0.16f
            BodyZone.LEFT_LEG, BodyZone.RIGHT_LEG -> h * 0.18f
        }
        return abs(projected.x - fallback.x) <= maxDx && abs(projected.y - fallback.y) <= maxDy
    }

    private fun projectZoneAnchorDirect(zone: BodyZone): PointF? {
        val anchors = zoneAnchorEntities[zone].orEmpty()
        for (entity in anchors) {
            val p = projectEntityToScreen(entity)
            if (p != null) {
                return p
            }
        }
        return null
    }

    private fun projectMirroredAnchor(zone: BodyZone): PointF? {
        val opposite = oppositeZone(zone) ?: return null
        val oppositePoint = projectZoneAnchorDirect(opposite) ?: return null
        val center = projectZoneAnchorDirect(BodyZone.CHEST)
            ?: projectZoneAnchorDirect(BodyZone.ABDOMEN)
            ?: PointF(width * 0.5f, height * 0.5f)
        val mirroredX = (2f * center.x - oppositePoint.x).coerceIn(0f, width.toFloat())
        return PointF(mirroredX, oppositePoint.y)
    }

    private fun projectEntityToScreen(entity: Int): PointF? {
        val viewer = modelViewer ?: return null
        val tm = viewer.engine.transformManager
        val instance = tm.getInstance(entity)
        if (instance == 0) {
            return null
        }

        val worldTransform = FloatArray(16)
        tm.getWorldTransform(instance, worldTransform)
        var worldX = worldTransform[12]
        var worldY = worldTransform[13]
        var worldZ = worldTransform[14]

        val renderableManager = viewer.engine.renderableManager
        if (renderableManager.hasComponent(entity)) {
            val renderableInstance = renderableManager.getInstance(entity)
            if (renderableInstance != 0) {
                runCatching {
                    val aabb = renderableManager.getAxisAlignedBoundingBox(renderableInstance, tmpRenderableAabb)
                    val center = aabb.center
                    val localX = center[0]
                    val localY = center[1]
                    val localZ = center[2]
                    worldX = worldTransform[0] * localX +
                        worldTransform[4] * localY +
                        worldTransform[8] * localZ +
                        worldTransform[12]
                    worldY = worldTransform[1] * localX +
                        worldTransform[5] * localY +
                        worldTransform[9] * localZ +
                        worldTransform[13]
                    worldZ = worldTransform[2] * localX +
                        worldTransform[6] * localY +
                        worldTransform[10] * localZ +
                        worldTransform[14]
                }
            }
        }

        val viewMatrix = viewer.camera.getViewMatrix(tmpViewMatrix)
        val projectionD = viewer.camera.getProjectionMatrix(null)
        for (i in 0 until 16) {
            tmpProjectionMatrix[i] = projectionD[i].toFloat()
        }
        Matrix.multiplyMM(tmpVpMatrix, 0, tmpProjectionMatrix, 0, viewMatrix, 0)

        tmpWorld[0] = worldX
        tmpWorld[1] = worldY
        tmpWorld[2] = worldZ
        tmpWorld[3] = 1f
        Matrix.multiplyMV(tmpClip, 0, tmpVpMatrix, 0, tmpWorld, 0)

        val w = tmpClip[3]
        if (w == 0f) {
            return null
        }

        val ndcX = tmpClip[0] / w
        val ndcY = tmpClip[1] / w
        val screenX = ((ndcX * 0.5f) + 0.5f) * width
        val screenY = (1f - ((ndcY * 0.5f) + 0.5f)) * height

        if (!screenX.isFinite() || !screenY.isFinite()) {
            return null
        }
        return clampToViewport(PointF(screenX, screenY))
    }

    private fun clampToViewport(point: PointF): PointF {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val safeX = point.x.coerceIn(w * 0.06f, w * 0.94f)
        val safeY = point.y.coerceIn(h * 0.06f, h * 0.92f)
        return PointF(safeX, safeY)
    }

    private fun oppositeZone(zone: BodyZone): BodyZone? {
        return when (zone) {
            BodyZone.LEFT_ARM -> BodyZone.RIGHT_ARM
            BodyZone.RIGHT_ARM -> BodyZone.LEFT_ARM
            BodyZone.LEFT_LEG -> BodyZone.RIGHT_LEG
            BodyZone.RIGHT_LEG -> BodyZone.LEFT_LEG
            else -> null
        }
    }

    private fun defaultZonePoint(zone: BodyZone): PointF {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val x = when (zone) {
            BodyZone.LEFT_ARM, BodyZone.LEFT_LEG -> w * 0.32f
            BodyZone.RIGHT_ARM, BodyZone.RIGHT_LEG -> w * 0.68f
            else -> w * 0.5f
        }
        val y = when (zone) {
            BodyZone.HEAD -> h * 0.16f
            BodyZone.NECK -> h * 0.24f
            BodyZone.CHEST -> h * 0.36f
            BodyZone.UPPER_BACK -> h * 0.44f
            BodyZone.ABDOMEN -> h * 0.54f
            BodyZone.LOWER_BACK -> h * 0.64f
            BodyZone.LEFT_ARM, BodyZone.RIGHT_ARM -> h * 0.52f
            BodyZone.LEFT_LEG, BodyZone.RIGHT_LEG -> h * 0.78f
        }
        return PointF(x, y)
    }

    private fun applySideOverride(baseZone: BodyZone, renderableName: String, touchX: Float): BodyZone {
        if (baseZone !in SIDE_SENSITIVE_ZONES) {
            return baseZone
        }

        val lowerName = renderableName.lowercase(Locale.US)
        return when {
            hasSideMarker(lowerName, "l", "left") -> when (baseZone) {
                BodyZone.RIGHT_ARM -> BodyZone.LEFT_ARM
                BodyZone.RIGHT_LEG -> BodyZone.LEFT_LEG
                else -> baseZone
            }
            hasSideMarker(lowerName, "r", "right") -> when (baseZone) {
                BodyZone.LEFT_ARM -> BodyZone.RIGHT_ARM
                BodyZone.LEFT_LEG -> BodyZone.RIGHT_LEG
                else -> baseZone
            }
            hasExplicitRightMarkers && !hasExplicitLeftMarkers -> {
                val isLeftTap = touchX < width * 0.5f
                when (baseZone) {
                    BodyZone.RIGHT_ARM, BodyZone.LEFT_ARM -> if (isLeftTap) BodyZone.LEFT_ARM else BodyZone.RIGHT_ARM
                    BodyZone.RIGHT_LEG, BodyZone.LEFT_LEG -> if (isLeftTap) BodyZone.LEFT_LEG else BodyZone.RIGHT_LEG
                    else -> baseZone
                }
            }
            else -> baseZone
        }
    }

    private fun hasSideMarker(name: String, token: String, word: String): Boolean {
        return name.contains(".$token") || name.contains("_$token") || name.contains(" $word")
    }

    private fun matchZoneByName(name: String): BodyZone? {
        if (name.isBlank()) {
            return null
        }
        val lowerName = name.lowercase(Locale.US)
        var bestZone: BodyZone? = null
        var bestScore = 0
        var tie = false

        for ((zone, config) in zoneConfigMap) {
            val score = scoreForZoneMatch(lowerName, config)
            if (score > bestScore) {
                bestZone = zone
                bestScore = score
                tie = false
            } else if (score > 0 && score == bestScore && bestZone != zone) {
                tie = true
            }
        }

        if (bestScore <= 0 || tie) {
            return null
        }
        return bestZone
    }

    private fun matchLayerByName(name: String): String? {
        if (name.isBlank()) return null
        val lowerName = name.lowercase(Locale.US)
        var bestLayer: String? = null
        var bestScore = 0
        var tie = false
        for ((layerCode, config) in organLayerMap) {
            val score = scoreForLayerMatch(lowerName, config)
            if (score > bestScore) {
                bestLayer = layerCode
                bestScore = score
                tie = false
            } else if (score > 0 && score == bestScore && bestLayer != layerCode) {
                tie = true
            }
        }
        if (bestScore <= 0 || tie) {
            return null
        }
        return bestLayer
    }

    private fun scoreForLayerMatch(name: String, config: OrganLayerConfig): Int {
        var score = 0
        for (token in config.meshTokens) {
            val t = token.trim().lowercase(Locale.US)
            if (t.isBlank()) continue
            if (name == t) {
                score = max(score, 100)
                continue
            }
            if (name.startsWith("$t ") || name.endsWith(" $t") || name.startsWith("$t.") || name.endsWith(".$t")) {
                score = max(score, 92)
                continue
            }
            if (name.contains(t)) {
                score = max(score, 74)
            }
        }
        return score
    }

    private fun resolveLayerCodeForZone(zone: BodyZone): String? {
        zoneConfigMap[zone]?.layerCode?.takeIf { it.isNotBlank() }?.let { return it }
        return when (zone) {
            BodyZone.HEAD, BodyZone.NECK -> "layer_head_neck"
            BodyZone.CHEST, BodyZone.UPPER_BACK -> "layer_thorax"
            BodyZone.ABDOMEN -> "layer_abdomen"
            BodyZone.LOWER_BACK -> "layer_pelvis"
            BodyZone.LEFT_ARM,
            BodyZone.RIGHT_ARM,
            BodyZone.LEFT_LEG,
            BodyZone.RIGHT_LEG -> "layer_limbs"
        }
    }

    private fun scoreForZoneMatch(name: String, config: ZoneConfig): Int {
        var score = 0
        for (keyword in config.meshKeywords) {
            val k = keyword.trim().lowercase(Locale.US)
            if (k.isBlank()) continue
            if (name == k) {
                score = max(score, 100)
                continue
            }
            if (name.startsWith("$k ") || name.endsWith(" $k") || name.startsWith("$k.") || name.endsWith(".$k")) {
                score = max(score, 90)
                continue
            }
            if (name.contains(k)) {
                score = max(score, 70)
            }
        }
        return score
    }

    private fun recordPickMetrics(startMs: Long, source: ZonePickSource) {
        recordDurationTelemetry(
            metric = "3d_pick_latency_ms",
            startElapsedMs = startMs,
            attributes = mapOf("source" to source.name.lowercase())
        )
        val hitRate = if (source == ZonePickSource.RAY_PICK) 1.0 else 0.0
        recordTelemetry(
            metric = "3d_pick_hit_rate",
            value = hitRate,
            unit = "ratio",
            attributes = mapOf("source" to source.name.lowercase())
        )
        if (source == ZonePickSource.FALLBACK) {
            recordTelemetry(
                metric = "3d_pick_fallback_count",
                value = 1.0,
                unit = "count"
            )
        }
    }

    private fun recordTelemetry(
        metric: String,
        value: Double,
        unit: String,
        attributes: Map<String, String> = emptyMap()
    ) {
        PerformanceTelemetry.record(
            metric = metric,
            value = value,
            unit = unit,
            attributes = withStageMode(attributes)
        )
    }

    private fun recordDurationTelemetry(
        metric: String,
        startElapsedMs: Long,
        attributes: Map<String, String> = emptyMap()
    ) {
        PerformanceTelemetry.recordDuration(
            metric = metric,
            startElapsedMs = startElapsedMs,
            attributes = withStageMode(attributes)
        )
    }

    private fun withStageMode(attributes: Map<String, String>): Map<String, String> {
        return attributes + ("stage_mode" to stageModeTag)
    }

    private fun updateFocusAnimation(frameTimeNanos: Long) {
        if (focusAnimState == FocusAnimState.IDLE) return
        val viewer = modelViewer ?: return
        val asset = viewer.asset ?: return
        val rootInstance = getRootInstance(viewer, asset) ?: return

        val focusDurationNanos = modelManifest.cameraDefaults.focusDurationMs * 1_000_000L
        val returnDurationNanos = modelManifest.cameraDefaults.returnDurationMs * 1_000_000L
        val zone = focusZone
        val preset = zone?.let { zoneConfigMap[it]?.focusPreset } ?: defaultFocusPreset(zone ?: BodyZone.CHEST)

        when (focusAnimState) {
            FocusAnimState.FOCUSING -> {
                val t = normalizedProgress(frameTimeNanos, focusAnimStateStartNanos, focusDurationNanos)
                val eased = easeInOutCubic(t)
                lerpMatrix(focusFromMatrix, focusToMatrix, eased, tmpAnimMatrix)
                setRootTransform(rootInstance, tmpAnimMatrix)
                applyInterpolatedCameraPose(viewer, focusFromOrbit, focusToOrbit, focusFromLookAt, focusToLookAt, eased)
                viewer.cameraFocalLength = focusFromFocalLength + (focusToFocalLength - focusFromFocalLength) * eased
                applyViewportFocusTransform(zone ?: BodyZone.CHEST, eased)
                if (t >= 1f) {
                    focusAnimState = FocusAnimState.HOLDING
                    focusAnimStateStartNanos = frameTimeNanos
                }
            }

            FocusAnimState.HOLDING -> {
                setRootTransform(rootInstance, focusToMatrix)
                applyCameraPose(viewer, focusToOrbit, focusToLookAt)
                viewer.cameraFocalLength = focusToFocalLength
                applyViewportFocusTransform(zone ?: BodyZone.CHEST, 1f)
                val holdDurationNanos = preset.holdMs.coerceAtLeast(100L) * 1_000_000L
                if (frameTimeNanos - focusAnimStateStartNanos >= holdDurationNanos) {
                    focusAnimState = FocusAnimState.RETURNING
                    focusAnimStateStartNanos = frameTimeNanos
                }
            }

            FocusAnimState.RETURNING -> {
                val t = normalizedProgress(frameTimeNanos, focusAnimStateStartNanos, returnDurationNanos)
                val eased = easeInOutCubic(t)
                lerpMatrix(focusToMatrix, rootBaseMatrix, eased, tmpAnimMatrix)
                setRootTransform(rootInstance, tmpAnimMatrix)
                applyInterpolatedCameraPose(viewer, focusToOrbit, focusFromOrbit, focusToLookAt, focusFromLookAt, eased)
                viewer.cameraFocalLength = focusToFocalLength + (focusFromFocalLength - focusToFocalLength) * eased
                applyViewportFocusTransform(zone ?: BodyZone.CHEST, 1f - eased)
                if (t >= 1f) {
                    setRootTransform(rootInstance, rootBaseMatrix)
                    applyCameraPose(viewer, focusFromOrbit, focusFromLookAt)
                    viewer.cameraFocalLength = focusFromFocalLength
                    textureView.scaleX = 1f
                    textureView.scaleY = 1f
                    textureView.translationX = 0f
                    textureView.translationY = 0f
                    resetViewportFocusTransform()
                    focusAnimState = FocusAnimState.IDLE
                    focusAnimStateStartNanos = 0L
                    interactionLocked = false
                    zone?.let { finishedZone ->
                        callback?.onFocusEnd(finishedZone)
                        recordTelemetry(
                            metric = "3d_focus_complete_count",
                            value = 1.0,
                            unit = "count",
                            attributes = mapOf("zone" to finishedZone.name, "entry" to focusEntry)
                        )
                        recordDurationTelemetry(
                            metric = "3d_focus_anim_latency_ms",
                            startElapsedMs = focusLatencyStartElapsedMs,
                            attributes = mapOf("zone" to finishedZone.name, "entry" to focusEntry)
                        )
                    }
                }
            }

            FocusAnimState.IDLE -> Unit
        }
    }

    private fun normalizedProgress(nowNanos: Long, startNanos: Long, durationNanos: Long): Float {
        val safeDuration = durationNanos.coerceAtLeast(1L)
        val raw = (nowNanos - startNanos).toFloat() / safeDuration.toFloat()
        return raw.coerceIn(0f, 1f)
    }

    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) {
            4f * t * t * t
        } else {
            1f - ((-2f * t + 2f).let { it * it * it }) / 2f
        }
    }

    private fun composeFocusMatrix(baseMatrix: FloatArray, preset: ZoneFocusPreset): FloatArray {
        Matrix.setIdentityM(tmpScaleMatrix, 0)
        Matrix.scaleM(tmpScaleMatrix, 0, preset.scale, preset.scale, preset.scale)
        Matrix.setIdentityM(tmpTranslateMatrix, 0)
        Matrix.translateM(tmpTranslateMatrix, 0, preset.offsetX, preset.offsetY, 0f)
        Matrix.multiplyMM(tmpTransformMatrix, 0, baseMatrix, 0, tmpScaleMatrix, 0)
        Matrix.multiplyMM(tmpLerpMatrix, 0, tmpTranslateMatrix, 0, tmpTransformMatrix, 0)
        return tmpLerpMatrix.copyOf()
    }

    private fun captureRootBaseTransform() {
        val viewer = modelViewer ?: return
        val asset = viewer.asset ?: return
        val rootInstance = getRootInstance(viewer, asset) ?: return
        viewer.engine.transformManager.getWorldTransform(rootInstance, rootBaseMatrix)
    }

    private fun setRootTransform(rootInstance: Int, matrix: FloatArray) {
        val viewer = modelViewer ?: return
        viewer.engine.transformManager.setTransform(rootInstance, matrix)
    }

    private fun getRootInstance(viewer: ModelViewer, asset: FilamentAsset): Int? {
        val rootEntity = asset.root
        if (rootEntity == 0) return null
        val tm: TransformManager = viewer.engine.transformManager
        val instance = tm.getInstance(rootEntity)
        return if (instance == 0) null else instance
    }

    private fun entryFromSource(source: ZonePickSource): String {
        return when (source) {
            ZonePickSource.BUTTON -> "macro_button"
            ZonePickSource.DETAIL_SHEET -> "micro_sheet"
            ZonePickSource.RAY_PICK -> "ray_pick"
            ZonePickSource.FALLBACK -> "fallback"
        }
    }

    private fun defaultFocusPreset(zone: BodyZone): ZoneFocusPreset {
        return when (zone) {
            BodyZone.HEAD -> ZoneFocusPreset(1.28f, 0f, 0.24f, 1200L)
            BodyZone.NECK -> ZoneFocusPreset(1.24f, 0f, 0.16f, 1100L)
            BodyZone.CHEST -> ZoneFocusPreset(1.22f, 0f, 0.02f, 1200L)
            BodyZone.UPPER_BACK -> ZoneFocusPreset(1.20f, 0f, 0.02f, 1200L)
            BodyZone.ABDOMEN -> ZoneFocusPreset(1.22f, 0f, -0.04f, 1200L)
            BodyZone.LOWER_BACK -> ZoneFocusPreset(1.18f, 0f, -0.10f, 1200L)
            BodyZone.LEFT_ARM -> ZoneFocusPreset(1.20f, 0.22f, 0.00f, 1200L)
            BodyZone.RIGHT_ARM -> ZoneFocusPreset(1.20f, -0.22f, 0.00f, 1200L)
            BodyZone.LEFT_LEG -> ZoneFocusPreset(1.20f, 0.14f, -0.18f, 1200L)
            BodyZone.RIGHT_LEG -> ZoneFocusPreset(1.20f, -0.14f, -0.18f, 1200L)
        }
    }

    private fun defaultCameraFocusPreset(zone: BodyZone): CameraPose {
        return when (zone) {
            BodyZone.HEAD -> CameraPose(
                orbitHome = floatArrayOf(0.0f, 1.55f, 1.18f),
                lookAt = floatArrayOf(0.0f, 1.02f, 0.0f)
            )
            BodyZone.NECK -> CameraPose(
                orbitHome = floatArrayOf(0.0f, 1.18f, 1.34f),
                lookAt = floatArrayOf(0.0f, 0.68f, 0.0f)
            )
            BodyZone.CHEST, BodyZone.UPPER_BACK -> CameraPose(
                orbitHome = floatArrayOf(0.0f, -0.28f, 1.92f),
                lookAt = floatArrayOf(0.0f, -0.04f, 0.0f)
            )
            BodyZone.ABDOMEN, BodyZone.LOWER_BACK -> CameraPose(
                orbitHome = floatArrayOf(0.0f, 0.12f, 2.02f),
                lookAt = floatArrayOf(0.0f, 0.22f, 0.0f)
            )
            BodyZone.LEFT_ARM -> CameraPose(
                orbitHome = floatArrayOf(0.38f, -0.24f, 1.98f),
                lookAt = floatArrayOf(0.22f, 0.00f, 0.0f)
            )
            BodyZone.RIGHT_ARM -> CameraPose(
                orbitHome = floatArrayOf(-0.38f, -0.24f, 1.98f),
                lookAt = floatArrayOf(-0.22f, 0.00f, 0.0f)
            )
            BodyZone.LEFT_LEG -> CameraPose(
                orbitHome = floatArrayOf(0.18f, 0.34f, 2.12f),
                lookAt = floatArrayOf(0.08f, 0.62f, 0.0f)
            )
            BodyZone.RIGHT_LEG -> CameraPose(
                orbitHome = floatArrayOf(-0.18f, 0.34f, 2.12f),
                lookAt = floatArrayOf(-0.08f, 0.62f, 0.0f)
            )
        }
    }

    private fun defaultCameraFocusFocalLength(zone: BodyZone): Float {
        return when (zone) {
            BodyZone.HEAD -> 92f
            BodyZone.NECK -> 82f
            BodyZone.CHEST, BodyZone.UPPER_BACK -> 68f
            BodyZone.ABDOMEN, BodyZone.LOWER_BACK -> 60f
            BodyZone.LEFT_ARM, BodyZone.RIGHT_ARM -> 62f
            BodyZone.LEFT_LEG, BodyZone.RIGHT_LEG -> 58f
        }
    }

    private fun applyViewportFocusTransform(zone: BodyZone, progress: Float) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        val (targetScale, targetXNorm, targetYNorm) = when (zone) {
            BodyZone.HEAD -> listOf(1.78f, 0.50f, 0.48f)
            BodyZone.NECK -> listOf(1.56f, 0.50f, 0.44f)
            BodyZone.CHEST, BodyZone.UPPER_BACK -> listOf(1.38f, 0.50f, 0.42f)
            BodyZone.ABDOMEN, BodyZone.LOWER_BACK -> listOf(1.32f, 0.50f, 0.48f)
            BodyZone.LEFT_ARM -> listOf(1.26f, 0.42f, 0.46f)
            BodyZone.RIGHT_ARM -> listOf(1.26f, 0.58f, 0.46f)
            BodyZone.LEFT_LEG -> listOf(1.22f, 0.46f, 0.60f)
            BodyZone.RIGHT_LEG -> listOf(1.54f, 0.54f, 0.60f)
        }
        val pivotX = viewportFocusPoint.x
        val pivotY = viewportFocusPoint.y
        val scale = 1f + (targetScale - 1f) * clampedProgress
        val targetX = width * targetXNorm
        val targetY = height * targetYNorm
        val deltaX = (targetX - viewportFocusPoint.x) * clampedProgress
        val deltaY = (targetY - viewportFocusPoint.y) * clampedProgress

        textureView.pivotX = pivotX
        textureView.pivotY = pivotY
        textureView.scaleX = scale
        textureView.scaleY = scale
        textureView.translationX = deltaX
        textureView.translationY = deltaY

        overlayView.pivotX = pivotX
        overlayView.pivotY = pivotY
        overlayView.scaleX = scale
        overlayView.scaleY = scale
        overlayView.translationX = deltaX
        overlayView.translationY = deltaY
    }

    private fun resetViewportFocusTransform() {
        textureView.scaleX = 1f
        textureView.scaleY = 1f
        textureView.translationX = 0f
        textureView.translationY = 0f
        overlayView.scaleX = 1f
        overlayView.scaleY = 1f
        overlayView.translationX = 0f
        overlayView.translationY = 0f
    }

    private fun applyFixedViewportCloseup(zone: BodyZone) {
        val (scale, tx, ty) = when (zone) {
            BodyZone.HEAD -> Triple(1.34f, 0f, height * 2.68f)
            BodyZone.NECK -> Triple(1.40f, 0f, height * 1.42f)
            BodyZone.CHEST, BodyZone.UPPER_BACK -> Triple(1.74f, 0f, height * 0.54f)
            BodyZone.ABDOMEN, BodyZone.LOWER_BACK -> Triple(1.56f, 0f, height * 0.30f)
            BodyZone.LEFT_ARM -> Triple(1.40f, width * 0.18f, height * 0.20f)
            BodyZone.RIGHT_ARM -> Triple(1.40f, -width * 0.18f, height * 0.20f)
            BodyZone.LEFT_LEG, BodyZone.RIGHT_LEG -> Triple(1.38f, 0f, -height * 0.02f)
        }
        textureView.pivotX = width * 0.5f
        textureView.pivotY = height * 0.5f
        textureView.scaleX = scale
        textureView.scaleY = scale
        textureView.translationX = tx
        textureView.translationY = ty

        overlayView.scaleX = 1f
        overlayView.scaleY = 1f
        overlayView.translationX = 0f
        overlayView.translationY = 0f
    }

    private fun fixedOverlayPoint(zone: BodyZone): PointF {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        return when (zone) {
            BodyZone.HEAD, BodyZone.NECK -> PointF(w * 0.50f, h * 0.24f)
            BodyZone.CHEST, BodyZone.UPPER_BACK -> PointF(w * 0.50f, h * 0.40f)
            BodyZone.ABDOMEN, BodyZone.LOWER_BACK -> PointF(w * 0.50f, h * 0.56f)
            BodyZone.LEFT_ARM -> PointF(w * 0.34f, h * 0.46f)
            BodyZone.RIGHT_ARM -> PointF(w * 0.66f, h * 0.46f)
            BodyZone.LEFT_LEG -> PointF(w * 0.42f, h * 0.72f)
            BodyZone.RIGHT_LEG -> PointF(w * 0.58f, h * 0.72f)
        }
    }

    private fun lerpMatrix(from: FloatArray, to: FloatArray, t: Float, out: FloatArray) {
        for (i in 0 until 16) {
            out[i] = from[i] + (to[i] - from[i]) * t
        }
    }

    private fun rebuildZoneMappings(asset: FilamentAsset?) {
        renderableToZone.clear()
        renderableToLayer.clear()
        zoneRenderableEntities.clear()
        layerRenderableEntities.clear()
        zoneAnchorEntities.clear()
        layerAnchorEntities.clear()
        renderableNames.clear()
        hasExplicitLeftMarkers = false
        hasExplicitRightMarkers = false
        asset ?: return

        val renderables = asset.renderableEntities
        renderables.forEach { entity ->
            val name = asset.getName(entity).orEmpty()
            renderableNames[entity] = name
            val lowerName = name.lowercase(Locale.US)
            if (hasSideMarker(lowerName, "l", "left")) {
                hasExplicitLeftMarkers = true
            }
            if (hasSideMarker(lowerName, "r", "right")) {
                hasExplicitRightMarkers = true
            }
            matchZoneByName(name)?.let { zone ->
                renderableToZone[entity] = zone
                zoneRenderableEntities.getOrPut(zone) { linkedSetOf() }.add(entity)
            }
            matchLayerByName(name)?.let { layerCode ->
                renderableToLayer[entity] = layerCode
                layerRenderableEntities.getOrPut(layerCode) { linkedSetOf() }.add(entity)
            }
        }

        for ((entity, name) in renderableNames) {
            val lowerName = name.lowercase(Locale.US)
            for ((zone, config) in zoneConfigMap) {
                if (config.meshKeywords.any { keyword ->
                        val k = keyword.lowercase(Locale.US)
                        k.isNotBlank() && lowerName.contains(k)
                    }
                ) {
                    zoneRenderableEntities.getOrPut(zone) { linkedSetOf() }.add(entity)
                }
            }
            for ((layerCode, layer) in organLayerMap) {
                if (layer.meshTokens.any { token ->
                        val t = token.lowercase(Locale.US)
                        t.isNotBlank() && lowerName.contains(t)
                    }
                ) {
                    renderableToLayer.putIfAbsent(entity, layerCode)
                    layerRenderableEntities.getOrPut(layerCode) { linkedSetOf() }.add(entity)
                }
            }
        }

        modelManifest.zones.forEach { zone ->
            val anchors = mutableListOf<Int>()
            zone.anchorNodes.forEach { nodeName ->
                anchors += asset.getEntitiesByName(nodeName).asList()
            }
            if (anchors.isEmpty()) {
                zone.meshKeywords.forEach { keyword ->
                    anchors += asset.getEntitiesByPrefix(keyword).asList()
                }
            }
            if (anchors.isNotEmpty()) {
                zoneAnchorEntities[zone.zone] = anchors.distinct()
            }
        }

        modelManifest.organLayers.forEach { layer ->
            val anchors = mutableListOf<Int>()
            layer.anchorNodes.forEach { nodeName ->
                anchors += asset.getEntitiesByName(nodeName).asList()
            }
            if (anchors.isEmpty()) {
                layer.meshTokens.forEach { token ->
                    anchors += asset.getEntitiesByPrefix(token).asList()
                }
            }
            if (anchors.isNotEmpty()) {
                layerAnchorEntities[layer.layerCode] = anchors.distinct()
            }
        }

        Log.d(
            TAG,
            "rebuildZoneMappings: renderables=${renderables.size}, zoneMapped=${renderableToZone.size}, layerMapped=${renderableToLayer.size}, zoneRenderableMaps=${zoneRenderableEntities.size}, layerRenderableMaps=${layerRenderableEntities.size}, leftMarkers=$hasExplicitLeftMarkers, rightMarkers=$hasExplicitRightMarkers"
        )
    }

    private fun applyMedicalVisualStyle(asset: FilamentAsset?) {
        val viewer = modelViewer ?: return
        asset ?: return
        val renderableManager = viewer.engine.renderableManager
        var styledPrimitiveCount = 0
        var suppressedCount = 0
        val emissiveStrength = modelManifest.visualTheme.emissiveStrength.coerceIn(0.3f, 2.2f)
        val preserveMaterial = modelManifest.visualTheme.preserveMaterial

        for (entity in asset.renderableEntities) {
            val instance = renderableManager.getInstance(entity)
            if (instance == 0) continue
            val renderableName = renderableNames[entity].orEmpty().lowercase(Locale.US)
            if (shouldSuppressRenderable(renderableName)) {
                renderableManager.setLayerMask(instance, 0xFF, 0x00)
                suppressedCount += 1
                continue
            }
            if (preserveMaterial) {
                val primitiveCount = renderableManager.getPrimitiveCount(instance)
                for (primitive in 0 until primitiveCount) {
                    val materialInstance = runCatching {
                        renderableManager.getMaterialInstanceAt(instance, primitive)
                    }.getOrNull() ?: continue
                    val material = materialInstance.material
                    if (material.hasParameter("baseColorFactor")) {
                        materialInstance.setParameter("baseColorFactor", 1.0f, 1.0f, 1.0f, 0.98f)
                    }
                    if (material.hasParameter("roughnessFactor")) {
                        materialInstance.setParameter("roughnessFactor", 0.52f)
                    }
                    if (material.hasParameter("metallicFactor")) {
                        materialInstance.setParameter("metallicFactor", 0.0f)
                    }
                    if (material.hasParameter("emissiveFactor")) {
                        materialInstance.setParameter("emissiveFactor", 0.05f, 0.05f, 0.05f)
                    }
                    styledPrimitiveCount += 1
                }
                continue
            }
            val layerCode = renderableToLayer[entity].orEmpty()
            val isShell = layerCode == "layer_shell" || renderableName.contains("layer_shell")
            val baseColor = if (isShell) MEDICAL_SHELL_BASE_COLOR else MEDICAL_BONE_BASE_COLOR
            val emissiveColor = if (isShell) MEDICAL_SHELL_EMISSIVE_COLOR else MEDICAL_BONE_EMISSIVE_COLOR
            val scaledEmissive = floatArrayOf(
                (emissiveColor[0] * emissiveStrength).coerceIn(0f, 1f),
                (emissiveColor[1] * emissiveStrength).coerceIn(0f, 1f),
                (emissiveColor[2] * emissiveStrength).coerceIn(0f, 1f)
            )
            val roughness = if (isShell) 0.14f else 0.34f
            val metallic = if (isShell) 0.03f else 0.06f
            val primitiveCount = renderableManager.getPrimitiveCount(instance)
            for (primitive in 0 until primitiveCount) {
                val materialInstance = runCatching {
                    renderableManager.getMaterialInstanceAt(instance, primitive)
                }.getOrNull() ?: continue
                val material = materialInstance.material

                if (material.hasParameter("baseColorFactor")) {
                    materialInstance.setParameter(
                        "baseColorFactor",
                        baseColor[0],
                        baseColor[1],
                        baseColor[2],
                        baseColor[3]
                    )
                }
                if (material.hasParameter("emissiveFactor")) {
                    materialInstance.setParameter(
                        "emissiveFactor",
                        scaledEmissive[0],
                        scaledEmissive[1],
                        scaledEmissive[2]
                    )
                }
                if (material.hasParameter("roughnessFactor")) {
                    materialInstance.setParameter("roughnessFactor", roughness)
                }
                if (material.hasParameter("metallicFactor")) {
                    materialInstance.setParameter("metallicFactor", metallic)
                }
                styledPrimitiveCount += 1
            }
        }

        Log.d(TAG, "applyMedicalVisualStyle: styledPrimitives=$styledPrimitiveCount suppressed=$suppressedCount")
    }

    private fun applyZoneHighlight(zone: BodyZone?) {
        if (zone == null) return
        val layerCode = resolveLayerCodeForZone(zone)
        if (layerCode != null && applyLayerHighlight(layerCode, zone)) {
            recordTelemetry(
                metric = "3d_layer_match_rate",
                value = 1.0,
                unit = "ratio",
                attributes = mapOf("zone" to zone.name, "layer" to layerCode)
            )
            return
        }
        recordTelemetry(
            metric = "3d_layer_match_rate",
            value = 0.0,
            unit = "ratio",
            attributes = mapOf("zone" to zone.name, "layer" to (layerCode ?: "UNKNOWN"))
        )
        recordTelemetry(
            metric = "3d_layer_missing_fallback_count",
            value = 1.0,
            unit = "count",
            attributes = mapOf("zone" to zone.name, "layer" to (layerCode ?: "UNKNOWN"))
        )

        val viewer = modelViewer ?: return
        val asset = viewer.asset ?: return
        val renderableManager = viewer.engine.renderableManager
        val preserveMaterial = modelManifest.visualTheme.preserveMaterial
        val selectedEntities = zoneRenderableEntities[zone].orEmpty().ifEmpty {
            asset.renderableEntities.toSet()
        }.toSet()
        val selectedColor = zoneConfigMap[zone]?.highlightColor ?: DEFAULT_HIGHLIGHT_COLOR
        val selectedRgb = colorToUnitRgb(selectedColor)
        val baseGlow = modelManifest.visualTheme.emissiveStrength.coerceIn(0.3f, 2.2f)
        val selectedGlow = (baseGlow * if (preserveMaterial) 0.68f else 1.05f).coerceIn(0.2f, 1.4f)
        val passiveGlow = (baseGlow * if (preserveMaterial) 0.12f else 0.22f).coerceIn(0.02f, 0.4f)

        for (entity in asset.renderableEntities) {
            val instance = renderableManager.getInstance(entity)
            if (instance == 0) continue
            if (shouldSuppressRenderable(renderableNames[entity].orEmpty().lowercase(Locale.US))) continue
            val isSelected = selectedEntities.contains(entity)
            val primitiveCount = renderableManager.getPrimitiveCount(instance)

            for (primitive in 0 until primitiveCount) {
                val materialInstance = runCatching {
                    renderableManager.getMaterialInstanceAt(instance, primitive)
                }.getOrNull() ?: continue
                val material = materialInstance.material
                val glow = if (isSelected) selectedGlow else passiveGlow

                if (material.hasParameter("emissiveFactor")) {
                    materialInstance.setParameter(
                        "emissiveFactor",
                        (selectedRgb[0] * glow).coerceIn(0f, 1f),
                        (selectedRgb[1] * glow).coerceIn(0f, 1f),
                        (selectedRgb[2] * glow).coerceIn(0f, 1f)
                    )
                }

                if (material.hasParameter("baseColorFactor")) {
                    when {
                        isSelected -> {
                            materialInstance.setParameter("baseColorFactor", 1.0f, 1.0f, 1.0f, 1.0f)
                        }
                        preserveMaterial -> {
                            materialInstance.setParameter("baseColorFactor", 0.95f, 0.98f, 1.0f, 0.90f)
                        }
                        else -> {
                            materialInstance.setParameter("baseColorFactor", 0.90f, 0.94f, 1.0f, 0.82f)
                        }
                    }
                }

                if (material.hasParameter("roughnessFactor")) {
                    materialInstance.setParameter("roughnessFactor", if (isSelected) 0.10f else 0.30f)
                }
            }
        }
    }

    private fun applyLayerHighlight(layerCode: String, zone: BodyZone): Boolean {
        val viewer = modelViewer ?: return false
        val asset = viewer.asset ?: return false
        val layerConfig = organLayerMap[layerCode] ?: return false
        val selectedEntities = layerRenderableEntities[layerCode].orEmpty()
        if (selectedEntities.isEmpty()) {
            return false
        }

        val startMs = PerformanceTelemetry.nowElapsedMs()
        val renderableManager = viewer.engine.renderableManager
        val selectedColor = layerConfig.highlightColor
        val selectedRgb = colorToUnitRgb(selectedColor)
        val preserveMaterial = modelManifest.visualTheme.preserveMaterial
        val baseGlow = modelManifest.visualTheme.emissiveStrength.coerceIn(0.3f, 2.4f)
        val selectedGlow = (baseGlow * layerConfig.activeEmissive.coerceIn(0.6f, 2.4f)).coerceIn(0.3f, 2.4f)
        val passiveGlow = (baseGlow * 0.13f).coerceIn(0.02f, 0.45f)
        val passiveAlpha = layerConfig.passiveAlpha.coerceIn(0.12f, 0.95f)

        for (entity in asset.renderableEntities) {
            val instance = renderableManager.getInstance(entity)
            if (instance == 0) continue
            if (shouldSuppressRenderable(renderableNames[entity].orEmpty().lowercase(Locale.US))) continue
            val isSelected = selectedEntities.contains(entity)
            val primitiveCount = renderableManager.getPrimitiveCount(instance)
            val entityLayer = renderableToLayer[entity]
            val isShell = entityLayer == "layer_shell"
            for (primitive in 0 until primitiveCount) {
                val materialInstance = runCatching {
                    renderableManager.getMaterialInstanceAt(instance, primitive)
                }.getOrNull() ?: continue
                val material = materialInstance.material

                if (material.hasParameter("emissiveFactor")) {
                    val glow = if (isSelected) selectedGlow else passiveGlow
                    val rgb = if (isSelected) selectedRgb else SHELL_PASSIVE_RGB
                    materialInstance.setParameter(
                        "emissiveFactor",
                        (rgb[0] * glow).coerceIn(0f, 1f),
                        (rgb[1] * glow).coerceIn(0f, 1f),
                        (rgb[2] * glow).coerceIn(0f, 1f)
                    )
                }

                if (material.hasParameter("baseColorFactor")) {
                    when {
                        isSelected -> {
                            materialInstance.setParameter(
                                "baseColorFactor",
                                selectedRgb[0].coerceIn(0f, 1f),
                                selectedRgb[1].coerceIn(0f, 1f),
                                selectedRgb[2].coerceIn(0f, 1f),
                                1.0f
                            )
                        }
                        isShell -> {
                            materialInstance.setParameter(
                                "baseColorFactor",
                                0.95f,
                                0.98f,
                                1.0f,
                                SHELL_PASSIVE_ALPHA
                            )
                        }
                        preserveMaterial -> {
                            materialInstance.setParameter("baseColorFactor", 0.93f, 0.97f, 1.0f, passiveAlpha)
                        }
                        else -> {
                            materialInstance.setParameter("baseColorFactor", 0.85f, 0.92f, 1.0f, passiveAlpha)
                        }
                    }
                }

                if (material.hasParameter("roughnessFactor")) {
                    materialInstance.setParameter("roughnessFactor", if (isSelected) 0.14f else 0.36f)
                }
                if (material.hasParameter("metallicFactor")) {
                    materialInstance.setParameter("metallicFactor", if (isSelected) 0.04f else 0.02f)
                }
            }
        }

        recordDurationTelemetry(
            metric = "3d_layer_highlight_latency_ms",
            startElapsedMs = startMs,
            attributes = mapOf("zone" to zone.name, "layer" to layerCode)
        )
        return true
    }

    private fun shouldSuppressRenderable(renderableName: String): Boolean {
        val lowered = renderableName.lowercase(Locale.US)
        val keywords = if (modelManifest.visualTheme.preserveMaterial) {
            AVATAR_SUPPRESSED_RENDERABLE_KEYWORDS
        } else {
            SUPPRESSED_RENDERABLE_KEYWORDS
        }
        return keywords.any { keyword -> lowered.contains(keyword) }
    }

    private fun colorToUnitRgb(color: Int): FloatArray {
        return floatArrayOf(
            Color.red(color) / 255f,
            Color.green(color) / 255f,
            Color.blue(color) / 255f
        )
    }

    private fun loadModelManifest(path: String): ModelManifest {
        return runCatching {
            val text = context.assets.open(path).bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val model = root.optJSONObject("model")
            val assetPath = model?.optString("assetPath", DEFAULT_MODEL_ASSET_PATH).orEmpty().ifBlank {
                DEFAULT_MODEL_ASSET_PATH
            }
            val unitCubeOffset = parseVector3(
                jsonArray = model?.optJSONArray("unitCubeOffset"),
                fallback = floatArrayOf(0.0f, -0.06f, -2.55f)
            )
            val defaultScale = model?.optDouble("defaultScale", 1.22)?.toFloat() ?: 1.22f
            val defaultYawDeg = model?.optDouble("defaultYawDeg", 0.0)?.toFloat() ?: 0.0f
            val autoPlayAnimation = model?.optBoolean("autoPlayAnimation", false) ?: false
            val defaultAnimationName = model?.optString("defaultAnimationName").orEmpty().ifBlank { null }
            val autoPlayAnimationNames = model?.optJSONArray("autoPlayAnimationNames").toStringList()
            val autoPlaySwitchMs = model?.optLong("autoPlaySwitchMs", 3200L) ?: 3200L
            val visualThemeJson = model?.optJSONObject("visualTheme")
            val visualTheme = VisualTheme(
                clearColor = parseColorOrDefault(
                    visualThemeJson?.optString("clearColor"),
                    DEFAULT_CLEAR_COLOR
                ),
                directLight = visualThemeJson?.optDouble("directLight", 60_000.0)?.toFloat()
                    ?: 60_000f,
                emissiveStrength = visualThemeJson?.optDouble("emissiveStrength", 1.0)?.toFloat()
                    ?: 1.0f,
                preserveMaterial = visualThemeJson?.optBoolean("preserveMaterial", false) ?: false
            )
            val cameraDefaultsJson = model?.optJSONObject("cameraDefaults")
            val cameraDefaults = CameraDefaults(
                baseFocalLength = cameraDefaultsJson?.optDouble("baseFocalLength", 34.0)?.toFloat()
                    ?: 34.0f,
                focusDurationMs = cameraDefaultsJson?.optLong("focusDurationMs", 420L) ?: 420L,
                returnDurationMs = cameraDefaultsJson?.optLong("returnDurationMs", 380L) ?: 380L
            )
            val cameraJson = model?.optJSONObject("camera")
            val cameraPose = CameraPose(
                orbitHome = parseVector3(
                    jsonArray = cameraJson?.optJSONArray("orbitHome"),
                    fallback = floatArrayOf(0.0f, 0.05f, 3.0f)
                ),
                lookAt = parseVector3(
                    jsonArray = cameraJson?.optJSONArray("lookAt"),
                    fallback = floatArrayOf(0.0f, 0.05f, 0.0f)
                )
            )

            val organLayersJson = root.optJSONArray("organLayers")
            val organLayers = mutableListOf<OrganLayerConfig>()
            if (organLayersJson != null) {
                for (i in 0 until organLayersJson.length()) {
                    val item = organLayersJson.optJSONObject(i) ?: continue
                    val layerCode = item.optString("layerCode").trim().lowercase(Locale.US)
                    if (layerCode.isBlank()) continue
                    val displayName = item.optString("displayName", layerCode)
                    val meshTokens = item.optJSONArray("meshTokens").toStringList()
                    val anchorNodes = item.optJSONArray("anchorNodes").toStringList()
                    val highlightColor = parseColorOrDefault(
                        item.optString("highlightColor"),
                        DEFAULT_HIGHLIGHT_COLOR
                    )
                    val passiveAlpha = item.optDouble("passiveAlpha", 0.72).toFloat()
                    val activeEmissive = item.optDouble("activeEmissive", 1.25).toFloat()
                    organLayers += OrganLayerConfig(
                        layerCode = layerCode,
                        displayName = displayName,
                        meshTokens = meshTokens,
                        highlightColor = highlightColor,
                        passiveAlpha = passiveAlpha,
                        activeEmissive = activeEmissive,
                        anchorNodes = anchorNodes
                    )
                }
            }

            val zonesJson = root.optJSONArray("zones")
            val zones = mutableListOf<ZoneConfig>()
            if (zonesJson != null) {
                for (i in 0 until zonesJson.length()) {
                    val item = zonesJson.optJSONObject(i) ?: continue
                    val zoneCode = item.optString("zoneCode").uppercase()
                    val zone = runCatching { BodyZone.valueOf(zoneCode) }.getOrNull() ?: continue
                    val displayName = item.optString("displayName", zone.defaultLabel())
                    val meshNames = item.optJSONArray("meshNames").toStringList()
                    val anchorNodes = item.optJSONArray("anchorNodes").toStringList()
                    val layerCode = item.optString("layerCode").trim().lowercase(Locale.US).ifBlank { null }
                    val color = parseColorOrDefault(item.optString("highlightColor"), DEFAULT_HIGHLIGHT_COLOR)
                    val focusJson = item.optJSONObject("focusPreset")
                    val focusPreset = ZoneFocusPreset(
                        scale = focusJson?.optDouble("scale", defaultFocusPreset(zone).scale.toDouble())?.toFloat()
                            ?: defaultFocusPreset(zone).scale,
                        offsetX = focusJson?.optDouble("offsetX", defaultFocusPreset(zone).offsetX.toDouble())?.toFloat()
                            ?: defaultFocusPreset(zone).offsetX,
                        offsetY = focusJson?.optDouble("offsetY", defaultFocusPreset(zone).offsetY.toDouble())?.toFloat()
                            ?: defaultFocusPreset(zone).offsetY,
                        holdMs = focusJson?.optLong("holdMs", defaultFocusPreset(zone).holdMs)
                            ?: defaultFocusPreset(zone).holdMs
                    )
                    zones += ZoneConfig(
                        zone = zone,
                        displayName = displayName,
                        meshKeywords = meshNames,
                        anchorNodes = anchorNodes,
                        layerCode = layerCode,
                        highlightColor = color,
                        focusPreset = focusPreset
                    )
                }
            }

            ModelManifest(
                assetPath = assetPath,
                unitCubeOffset = unitCubeOffset,
                defaultScale = defaultScale,
                defaultYawDeg = defaultYawDeg,
                autoPlayAnimation = autoPlayAnimation,
                defaultAnimationName = defaultAnimationName,
                autoPlayAnimationNames = autoPlayAnimationNames,
                autoPlaySwitchMs = autoPlaySwitchMs,
                visualTheme = visualTheme,
                cameraDefaults = cameraDefaults,
                cameraPose = cameraPose,
                organLayers = organLayers.ifEmpty { defaultManifest().organLayers },
                zones = zones.ifEmpty { defaultManifest().zones }
            )
        }.getOrElse {
            Log.w(TAG, "model_manifest load failed ($path), use default manifest", it)
            defaultManifest()
        }
    }

    private fun normalizeManifestForRuntime(manifest: ModelManifest): ModelManifest {
        if (!isVirtualizedDevice()) {
            return manifest
        }
        val lowerAssetPath = manifest.assetPath.lowercase(Locale.US)
        if (!lowerAssetPath.contains("guide_avatar_vroid")) {
            return manifest
        }
        Log.i(
            TAG,
            "Virtualized device detected, switch avatar scene asset from ${manifest.assetPath} to $AVATAR_VM_COMPAT_ASSET_PATH"
        )
        return manifest.copy(
            assetPath = AVATAR_VM_COMPAT_ASSET_PATH,
            visualTheme = manifest.visualTheme.copy(preserveMaterial = false)
        )
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

    private fun resolveAutoPlayAnimationIndex(
        animator: com.google.android.filament.gltfio.Animator?,
        animationName: String?
    ): Int {
        if (animator == null || animator.animationCount <= 0) {
            return 0
        }
        val target = animationName?.trim().orEmpty()
        if (target.isBlank()) {
            return 0
        }
        for (i in 0 until animator.animationCount) {
            val name = animator.getAnimationName(i) ?: continue
            if (name.equals(target, ignoreCase = true)) {
                return i
            }
        }
        for (i in 0 until animator.animationCount) {
            val name = animator.getAnimationName(i) ?: continue
            if (name.contains(target, ignoreCase = true)) {
                return i
            }
        }
        return 0
    }

    private fun resolveAutoPlayAnimationIndices(
        animator: com.google.android.filament.gltfio.Animator?,
        animationNames: List<String>,
        fallbackIndex: Int
    ): IntArray {
        if (animator == null || animator.animationCount <= 0) {
            return intArrayOf(0)
        }
        val indices = mutableListOf<Int>()
        for (name in animationNames) {
            val target = name.trim()
            if (target.isBlank()) continue
            var matchedIndex = -1
            for (i in 0 until animator.animationCount) {
                val animName = animator.getAnimationName(i) ?: continue
                if (animName.equals(target, ignoreCase = true)) {
                    matchedIndex = i
                    break
                }
            }
            if (matchedIndex < 0) {
                for (i in 0 until animator.animationCount) {
                    val animName = animator.getAnimationName(i) ?: continue
                    if (animName.contains(target, ignoreCase = true)) {
                        matchedIndex = i
                        break
                    }
                }
            }
            if (matchedIndex >= 0) {
                indices += matchedIndex
            }
        }
        if (indices.isEmpty()) {
            return intArrayOf(fallbackIndex.coerceIn(0, animator.animationCount - 1))
        }
        return indices.distinct().toIntArray()
    }

    private fun parseColorOrDefault(value: String?, defaultColor: Int): Int {
        if (value.isNullOrBlank()) return defaultColor
        return runCatching { Color.parseColor(value) }.getOrDefault(defaultColor)
    }

    private fun parseVector3(jsonArray: org.json.JSONArray?, fallback: FloatArray): FloatArray {
        if (jsonArray == null || jsonArray.length() < 3) {
            return fallback
        }
        return floatArrayOf(
            jsonArray.optDouble(0, fallback[0].toDouble()).toFloat(),
            jsonArray.optDouble(1, fallback[1].toDouble()).toFloat(),
            jsonArray.optDouble(2, fallback[2].toDouble()).toFloat()
        )
    }

    private fun defaultManifest(): ModelManifest {
        return ModelManifest(
            assetPath = DEFAULT_MODEL_ASSET_PATH,
            unitCubeOffset = floatArrayOf(0.0f, -0.06f, -2.55f),
            defaultScale = 1.25f,
            defaultYawDeg = 0.0f,
            autoPlayAnimation = false,
            defaultAnimationName = null,
            autoPlayAnimationNames = emptyList(),
            autoPlaySwitchMs = 3200L,
            visualTheme = VisualTheme(
                clearColor = DEFAULT_CLEAR_COLOR,
                directLight = 60_000f,
                emissiveStrength = 1.0f,
                preserveMaterial = false
            ),
            cameraDefaults = CameraDefaults(
                baseFocalLength = 34.0f,
                focusDurationMs = 420L,
                returnDurationMs = 380L
            ),
            cameraPose = CameraPose(
                orbitHome = floatArrayOf(0.0f, 0.05f, 3.0f),
                lookAt = floatArrayOf(0.0f, 0.05f, 0.0f)
            ),
            organLayers = listOf(
                OrganLayerConfig(
                    layerCode = "layer_shell",
                    displayName = "人体外层",
                    meshTokens = listOf("layer_shell"),
                    highlightColor = Color.parseColor("#8ED8FF"),
                    passiveAlpha = 0.28f,
                    activeEmissive = 0.92f,
                    anchorNodes = listOf("layer_shell_main")
                ),
                OrganLayerConfig(
                    layerCode = "layer_head_neck",
                    displayName = "头颈",
                    meshTokens = listOf("layer_head_neck"),
                    highlightColor = Color.parseColor("#69D4FF"),
                    passiveAlpha = 0.32f,
                    activeEmissive = 1.30f,
                    anchorNodes = listOf("layer_head_neck_Frontal bone", "layer_head_neck_Atlas (C1)")
                ),
                OrganLayerConfig(
                    layerCode = "layer_thorax",
                    displayName = "胸腔",
                    meshTokens = listOf("layer_thorax"),
                    highlightColor = Color.parseColor("#4FC3F7"),
                    passiveAlpha = 0.34f,
                    activeEmissive = 1.35f,
                    anchorNodes = listOf("layer_thorax_Body of sternum", "layer_thorax_Thoracic vertebrae (T4)")
                ),
                OrganLayerConfig(
                    layerCode = "layer_abdomen",
                    displayName = "腹腔",
                    meshTokens = listOf("layer_abdomen"),
                    highlightColor = Color.parseColor("#45D7C5"),
                    passiveAlpha = 0.34f,
                    activeEmissive = 1.30f,
                    anchorNodes = listOf("layer_abdomen_Lumbar vertebrae (L3)")
                ),
                OrganLayerConfig(
                    layerCode = "layer_pelvis",
                    displayName = "盆腔",
                    meshTokens = listOf("layer_pelvis"),
                    highlightColor = Color.parseColor("#79B8FF"),
                    passiveAlpha = 0.34f,
                    activeEmissive = 1.28f,
                    anchorNodes = listOf("layer_pelvis_Sacrum", "layer_pelvis_Hip bone.l")
                ),
                OrganLayerConfig(
                    layerCode = "layer_limbs",
                    displayName = "四肢",
                    meshTokens = listOf("layer_limbs"),
                    highlightColor = Color.parseColor("#8FC7FF"),
                    passiveAlpha = 0.30f,
                    activeEmissive = 1.24f,
                    anchorNodes = listOf("layer_limbs_Femur.r", "layer_limbs_Humerus.r")
                )
            ),
            zones = listOf(
                ZoneConfig(
                    BodyZone.HEAD,
                    "头部",
                    listOf("frontal bone", "parietal bone", "occipital bone", "mandible bone"),
                    listOf("layer_head_neck_Frontal bone"),
                    "layer_head_neck",
                    Color.parseColor("#57D0FF"),
                    defaultFocusPreset(BodyZone.HEAD)
                ),
                ZoneConfig(
                    BodyZone.NECK,
                    "颈部",
                    listOf("atlas", "axis", "cervical vertebrae"),
                    listOf("layer_head_neck_Atlas (C1)"),
                    "layer_head_neck",
                    Color.parseColor("#57D0FF"),
                    defaultFocusPreset(BodyZone.NECK)
                ),
                ZoneConfig(
                    BodyZone.CHEST,
                    "胸部",
                    listOf("sternum", "rib", "costal cart", "clavicle"),
                    listOf("layer_thorax_Body of sternum"),
                    "layer_thorax",
                    Color.parseColor("#4FC3F7"),
                    defaultFocusPreset(BodyZone.CHEST)
                ),
                ZoneConfig(
                    BodyZone.UPPER_BACK,
                    "上背部",
                    listOf("thoracic vertebrae", "scapula"),
                    listOf("layer_thorax_Thoracic vertebrae (T4)"),
                    "layer_thorax",
                    Color.parseColor("#42A5F5"),
                    defaultFocusPreset(BodyZone.UPPER_BACK)
                ),
                ZoneConfig(
                    BodyZone.ABDOMEN,
                    "腹部",
                    listOf("hip bone", "pelvis", "sacrum"),
                    listOf("layer_abdomen_Lumbar vertebrae (L3)"),
                    "layer_abdomen",
                    Color.parseColor("#26C6DA"),
                    defaultFocusPreset(BodyZone.ABDOMEN)
                ),
                ZoneConfig(
                    BodyZone.LOWER_BACK,
                    "下背部",
                    listOf("lumbar vertebrae", "sacrum", "coccyx"),
                    listOf("layer_pelvis_Sacrum"),
                    "layer_pelvis",
                    Color.parseColor("#26A69A"),
                    defaultFocusPreset(BodyZone.LOWER_BACK)
                ),
                ZoneConfig(
                    BodyZone.LEFT_ARM,
                    "左臂",
                    listOf("humerus.l", "radius.l", "ulna.l"),
                    listOf("layer_limbs_Humerus.l"),
                    "layer_limbs",
                    Color.parseColor("#5C9EFF"),
                    defaultFocusPreset(BodyZone.LEFT_ARM)
                ),
                ZoneConfig(
                    BodyZone.RIGHT_ARM,
                    "右臂",
                    listOf("humerus.r", "radius.r", "ulna.r"),
                    listOf("layer_limbs_Humerus.r"),
                    "layer_limbs",
                    Color.parseColor("#5C9EFF"),
                    defaultFocusPreset(BodyZone.RIGHT_ARM)
                ),
                ZoneConfig(
                    BodyZone.LEFT_LEG,
                    "左腿",
                    listOf("femur.l", "tibia.l", "fibula.l"),
                    listOf("layer_limbs_Femur.l"),
                    "layer_limbs",
                    Color.parseColor("#42A5F5"),
                    defaultFocusPreset(BodyZone.LEFT_LEG)
                ),
                ZoneConfig(
                    BodyZone.RIGHT_LEG,
                    "右腿",
                    listOf("femur.r", "tibia.r", "fibula.r"),
                    listOf("layer_limbs_Femur.r"),
                    "layer_limbs",
                    Color.parseColor("#42A5F5"),
                    defaultFocusPreset(BodyZone.RIGHT_LEG)
                )
            )
        )
    }

    private fun org.json.JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until length()) {
            val item = optString(i)
            if (!item.isNullOrBlank()) {
                result += item
            }
        }
        return result
    }

    private fun resolveRoleAnimationIndex(
        animator: com.google.android.filament.gltfio.Animator,
        candidates: List<String>
    ): Int? {
        for (candidate in candidates) {
            val target = candidate.trim()
            if (target.isBlank()) continue
            for (i in 0 until animator.animationCount) {
                val name = animator.getAnimationName(i) ?: continue
                if (name.equals(target, ignoreCase = true)) {
                    return i
                }
            }
            for (i in 0 until animator.animationCount) {
                val name = animator.getAnimationName(i) ?: continue
                if (name.contains(target, ignoreCase = true) || target.contains(name, ignoreCase = true)) {
                    return i
                }
            }
        }
        return null
    }

    private class ZoneHighlightOverlayView(context: Context) : android.view.View(context) {
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.4f
            color = DEFAULT_HIGHLIGHT_COLOR
        }
        private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            color = DEFAULT_HIGHLIGHT_COLOR
            alpha = 130
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = DEFAULT_HIGHLIGHT_COLOR
            alpha = 48
        }
        private val coreGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = DEFAULT_HIGHLIGHT_COLOR
            alpha = 96
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = DEFAULT_HIGHLIGHT_COLOR
            alpha = 180
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A5273")
            textSize = 31f
            isFakeBoldText = true
            style = Paint.Style.FILL
        }
        private val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 238
            style = Paint.Style.FILL
        }
        private val textBgStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B9DEEF")
            alpha = 220
            style = Paint.Style.STROKE
            strokeWidth = 1.3f
        }
        private val textShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#66A5CCE5")
            style = Paint.Style.FILL
        }

        private var xPos = -1f
        private var yPos = -1f
        private var label = ""
        private var visible = false
        private val bubbleRect = RectF()
        private val candidateRect = RectF()
        private val viewportRect = RectF()
        private val reservedInfoCardRect = RectF()
        private val candidateOffsets = arrayOf(
            floatArrayOf(64f, -72f),
            floatArrayOf(-64f, -72f),
            floatArrayOf(64f, 54f),
            floatArrayOf(-64f, 54f),
            floatArrayOf(0f, -92f)
        )

        fun show(label: String, color: Int, x: Float, y: Float) {
            this.label = label
            this.xPos = x
            this.yPos = y
            ringPaint.color = color
            outerRingPaint.color = color
            linePaint.color = color
            glowPaint.color = Color.argb(72, Color.red(color), Color.green(color), Color.blue(color))
            coreGlowPaint.color = Color.argb(128, Color.red(color), Color.green(color), Color.blue(color))
            visible = true
            invalidate()
        }

        fun updatePosition(x: Float, y: Float) {
            if (!visible) return
            xPos = x
            yPos = y
            invalidate()
        }

        fun hide() {
            visible = false
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!visible || xPos < 0f || yPos < 0f) return

            val baseRadius = min(width, height) * 0.054f
            val pulse = ((SystemClock.uptimeMillis() % 1500L) / 1500f)
            val pulseScale = 1f + 0.1f * kotlin.math.sin((pulse * 2f * Math.PI).toFloat())
            val radius = baseRadius * pulseScale

            coreGlowPaint.shader = RadialGradient(
                xPos,
                yPos,
                radius * 1.45f,
                intArrayOf(
                    Color.argb(138, Color.red(ringPaint.color), Color.green(ringPaint.color), Color.blue(ringPaint.color)),
                    Color.argb(0, Color.red(ringPaint.color), Color.green(ringPaint.color), Color.blue(ringPaint.color))
                ),
                floatArrayOf(0.0f, 1.0f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(xPos, yPos, radius * 1.4f, coreGlowPaint)
            canvas.drawCircle(xPos, yPos, radius * 1.1f, glowPaint)
            canvas.drawCircle(xPos, yPos, radius * 1.32f, outerRingPaint)
            canvas.drawCircle(xPos, yPos, radius, ringPaint)

            val textWidth = textPaint.measureText(label)
            val bubblePaddingX = 18f
            val bubblePaddingY = 12f
            val bubbleWidth = textWidth + bubblePaddingX * 2f
            val bubbleHeight = textPaint.textSize + bubblePaddingY * 2f

            val chosenBubble = resolveBubbleRect(xPos, yPos, bubbleWidth, bubbleHeight)
            bubbleRect.set(chosenBubble)

            val connectorX = bubbleRect.centerX()
            val connectorY = if (bubbleRect.centerY() < yPos) bubbleRect.bottom else bubbleRect.top
            val lineStartX = if (connectorX >= xPos) xPos + radius * 0.45f else xPos - radius * 0.45f
            val lineStartY = if (connectorY < yPos) yPos - radius * 0.55f else yPos + radius * 0.55f
            canvas.drawLine(lineStartX, lineStartY, connectorX, connectorY, linePaint)

            canvas.drawRoundRect(
                bubbleRect.left + 1f,
                bubbleRect.top + 2f,
                bubbleRect.right + 1f,
                bubbleRect.bottom + 2f,
                16f,
                16f,
                textShadowPaint
            )
            canvas.drawRoundRect(
                bubbleRect,
                16f,
                16f,
                textBgPaint
            )
            canvas.drawRoundRect(
                bubbleRect,
                16f,
                16f,
                textBgStrokePaint
            )

            val textX = bubbleRect.left + bubblePaddingX
            val textBaseline = bubbleRect.top + bubblePaddingY + textPaint.textSize * 0.86f
            canvas.drawText(label, textX, textBaseline, textPaint)
            coreGlowPaint.shader = null
        }

        private fun resolveBubbleRect(anchorX: Float, anchorY: Float, bubbleWidth: Float, bubbleHeight: Float): RectF {
            if (width <= 0 || height <= 0) {
                return RectF(anchorX, anchorY, anchorX + bubbleWidth, anchorY + bubbleHeight)
            }

            viewportRect.set(12f, 12f, width - 12f, height - 12f)
            reservedInfoCardRect.set(width * 0.58f, height * 0.16f, width - 8f, height * 0.58f)

            var bestScore = Float.MAX_VALUE
            var bestRect = RectF(anchorX, anchorY, anchorX + bubbleWidth, anchorY + bubbleHeight)

            for (offset in candidateOffsets) {
                val dx = offset[0]
                val dy = offset[1]
                val left = if (dx >= 0f) anchorX + dx else anchorX + dx - bubbleWidth
                val top = if (dy >= 0f) anchorY + dy else anchorY + dy - bubbleHeight
                candidateRect.set(left, top, left + bubbleWidth, top + bubbleHeight)
                clampRectToViewport(candidateRect, viewportRect)

                val centerX = candidateRect.centerX()
                val centerY = candidateRect.centerY()
                val distancePenalty = hypot(centerX - anchorX, centerY - anchorY)
                val reservedPenalty = if (RectF.intersects(candidateRect, reservedInfoCardRect)) 1800f else 0f
                val anchorPenalty = if (candidateRect.contains(anchorX, anchorY)) 2200f else 0f
                val score = distancePenalty + reservedPenalty + anchorPenalty

                if (score < bestScore) {
                    bestScore = score
                    bestRect = RectF(candidateRect)
                }
            }

            return bestRect
        }

        private fun clampRectToViewport(rect: RectF, viewport: RectF) {
            val dx = when {
                rect.left < viewport.left -> viewport.left - rect.left
                rect.right > viewport.right -> viewport.right - rect.right
                else -> 0f
            }
            val dy = when {
                rect.top < viewport.top -> viewport.top - rect.top
                rect.bottom > viewport.bottom -> viewport.bottom - rect.bottom
                else -> 0f
            }
            rect.offset(dx, dy)
        }
    }

    companion object {
        private const val TAG = "HumanBody3DView"
        private const val MANIFEST_PATH = "3d/model_manifest.json"
        private const val DEFAULT_MODEL_ASSET_PATH = "3d/human_body_layered_cartoon.glb"
        private const val AVATAR_VM_COMPAT_ASSET_PATH = "3d_avatar/guide_avatar_lite.glb"
        private val DEFAULT_CLEAR_COLOR = Color.TRANSPARENT
        private val DEFAULT_HIGHLIGHT_COLOR = Color.parseColor("#29B6F6")
        private val MEDICAL_BONE_BASE_COLOR = floatArrayOf(0.86f, 0.96f, 1.0f, 0.90f)
        private val MEDICAL_BONE_EMISSIVE_COLOR = floatArrayOf(0.10f, 0.24f, 0.36f)
        private val MEDICAL_SHELL_BASE_COLOR = floatArrayOf(0.96f, 0.99f, 1.0f, 0.32f)
        private val MEDICAL_SHELL_EMISSIVE_COLOR = floatArrayOf(0.12f, 0.28f, 0.42f)
        private val SHELL_PASSIVE_RGB = floatArrayOf(0.55f, 0.72f, 0.86f)
        private const val SHELL_PASSIVE_ALPHA = 0.24f
        private val SUPPRESSED_RENDERABLE_KEYWORDS = listOf(
            "bucket",
            "chair",
            "table",
            "icosphere",
            "ground",
            "camera",
            "light",
            "helper"
        )
        private val AVATAR_SUPPRESSED_RENDERABLE_KEYWORDS = listOf(
            "background",
            "floor",
            "stage",
            "plane",
            "camera",
            "helper"
        )
        private val SIDE_SENSITIVE_ZONES = setOf(
            BodyZone.LEFT_ARM,
            BodyZone.RIGHT_ARM,
            BodyZone.LEFT_LEG,
            BodyZone.RIGHT_LEG
        )
    }
}

