package com.example.newstart

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.findNavController
import androidx.navigation.navOptions
import com.example.newstart.core.common.R as CommonR
import com.example.newstart.databinding.ActivityMainNewBinding
import com.example.newstart.ui.avatar.AvatarAudioHost
import com.example.newstart.ui.avatar.AvatarController
import com.example.newstart.ui.avatar.AvatarNarration
import com.example.newstart.ui.avatar.AvatarSpeechPlaybackController
import com.example.newstart.ui.avatar.DesktopAvatarNarrationService
import com.example.newstart.ui.avatar.PageNarrationContext
import com.example.newstart.ui.profile.ProfileSettingsStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.widget.TextView

/**
 * Main Activity with bottom navigation and global avatar overlay.
 */
class MainActivity : AppCompatActivity(), AvatarAudioHost {

    companion object {
        private const val TAG = "MainActivity"
        private const val GLOBAL_AVATAR_MANIFEST_PATH = "3d/model_manifest_avatar_global.json"
    }

    private lateinit var binding: ActivityMainNewBinding
    private val topLevelDestinationIds = setOf(
        CommonR.id.navigation_home,
        CommonR.id.navigation_doctor,
        CommonR.id.navigation_trend,
        CommonR.id.navigation_device,
        CommonR.id.navigation_profile
    )
    private var selectedTopLevelDestinationId: Int = CommonR.id.navigation_home
    private var avatarOverlayEnabled: Boolean = true
    private var avatarTranslationX: Float = 0f
    private var avatarTranslationY: Float = 0f
    private var avatarMotionAnimator: ValueAnimator? = null
    private var avatarSpeakingAnimator: ValueAnimator? = null
    private var avatarTapAnimator: ValueAnimator? = null
    private var avatarIsSpeaking: Boolean = false
    private var avatarMotionProgress: Float = 0f
    private var avatarTapBoost: Float = 0f
    private var avatarBubbleOffsetX: Float = 0f
    private var avatarBubbleOffsetY: Float = 0f
    private lateinit var avatarSpeechPlaybackController: AvatarSpeechPlaybackController
    private lateinit var desktopAvatarNarrationService: DesktopAvatarNarrationService
    private var avatarNarrationJob: Job? = null
    private var lastAvatarNarration: AvatarNarration? = null
    private var lastAvatarNarrationDestinationId: Int = -1
    private var lastAvatarNarrationTrigger: String = ""
    private val preferCompactAvatarMode by lazy(LazyThreadSafetyMode.NONE) {
        shouldPreferCompactAvatarMode()
    }
    private val profileSettingsDestinationIds = setOf(
        CommonR.id.navigation_profile_personal_info,
        CommonR.id.navigation_profile_notifications,
        CommonR.id.navigation_profile_privacy,
        CommonR.id.navigation_profile_about
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainNewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        avatarSpeechPlaybackController = AvatarSpeechPlaybackController(this)
        desktopAvatarNarrationService = DesktopAvatarNarrationService(applicationContext)

        setupBottomNavigation()
        setupAvatarGuide()
        prewarmDesktopAvatarNarrations()
    }

    private fun setupBottomNavigation() {
        val navController = findNavController(R.id.nav_host_fragment)
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            navigateToTopLevelDestination(navController, item.itemId)
            true
        }
        binding.bottomNavigation.setOnItemReselectedListener { item ->
            navigateToTopLevelDestination(navController, item.itemId)
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            syncBottomNavigationSelection(destination.id)
            applyAvatarOverlayForDestination(destination.id)
            AvatarController.onPageEntered(destination.id)
            requestDesktopAvatarNarration(destination.id, trigger = "enter")
        }
        navController.currentDestination?.let { destination ->
            syncBottomNavigationSelection(destination.id)
            applyAvatarOverlayForDestination(destination.id)
            AvatarController.onPageEntered(destination.id)
            requestDesktopAvatarNarration(destination.id, trigger = "enter")
        }
    }

    private fun navigateToTopLevelDestination(navController: NavController, destinationId: Int) {
        if (destinationId !in topLevelDestinationIds) return
        selectedTopLevelDestinationId = destinationId
        val currentDestinationId = navController.currentDestination?.id
        if (currentDestinationId == destinationId) {
            syncBottomNavigationSelection(destinationId)
            return
        }

        val navOptions = navOptions {
            launchSingleTop = true
            restoreState = false
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = false
            }
        }
        navController.navigate(destinationId, null, navOptions)
        syncBottomNavigationSelection(destinationId)
    }

    private fun syncBottomNavigationSelection(destinationId: Int) {
        if (destinationId in topLevelDestinationIds) {
            selectedTopLevelDestinationId = destinationId
        }
        val itemIdToHighlight = if (destinationId in topLevelDestinationIds) {
            destinationId
        } else {
            selectedTopLevelDestinationId
        }
        if (binding.bottomNavigation.selectedItemId != itemIdToHighlight) {
            binding.bottomNavigation.menu.findItem(itemIdToHighlight)?.isChecked = true
        }
    }

    private fun setupAvatarGuide() {
        val avatarView = binding.globalAvatarView
        val speechBubble = binding.globalAvatarSpeechBubble

        if (preferCompactAvatarMode) {
            Log.i(TAG, "Compact avatar mode enabled for current device")
        }

        avatarView.setStageModeTag("global_avatar")
        avatarView.setManifestAssetPath(GLOBAL_AVATAR_MANIFEST_PATH)
        avatarView.setAutoFocusOnPick(false)
        avatarView.setPickEnabled(false)
        avatarView.setRendererEnabled(true)
        avatarView.setOnAvatarTapListener {
            animateAvatarTapAccent()
            if (avatarSpeechPlaybackController.isPlaying()) {
                avatarSpeechPlaybackController.stop()
                AvatarController.stopAudio()
                updateAvatarVoiceEntry()
            } else {
                requestDesktopAvatarNarration(selectedTopLevelDestinationId, trigger = "tap")
            }
        }
        binding.btnGlobalAvatarVoice.setOnClickListener {
            if (!isAvatarSpeechEnabled()) {
                updateAvatarVoiceEntry()
                return@setOnClickListener
            }
            if (avatarSpeechPlaybackController.isPlaying()) {
                stopAvatarAudio()
            } else {
                requestDesktopAvatarNarration(selectedTopLevelDestinationId, trigger = "button")
            }
        }
        avatarView.setOnAvatarDragListener { deltaX, deltaY, released ->
            if (!avatarOverlayEnabled) return@setOnAvatarDragListener
            if (!released) {
                avatarTranslationX += deltaX
                avatarTranslationY += deltaY
                clampAvatarOverlayTranslation(animated = false)
            } else {
                clampAvatarOverlayTranslation(animated = true)
            }
        }

        lifecycleScope.launch {
            AvatarController.currentRole.collectLatest { role ->
                avatarView.playRole(role.name)
            }
        }

        lifecycleScope.launch {
            AvatarController.isSpeaking.collectLatest { isSpeaking ->
                avatarIsSpeaking = isSpeaking
                if (!avatarOverlayEnabled) {
                    updateAvatarMotion(false)
                    speechBubble.visibility = View.GONE
                } else if (isSpeaking) {
                    speechBubble.visibility = View.VISIBLE
                    speechBubble.alpha = 0f
                    speechBubble.animate().alpha(1f).setDuration(300).start()
                    updateAvatarMotion(true)
                } else {
                    updateAvatarMotion(false)
                    speechBubble.animate().alpha(0f).setDuration(300).withEndAction {
                        speechBubble.visibility = View.GONE
                    }.start()
                }
            }
        }

        lifecycleScope.launch {
            AvatarController.speechText.collectLatest { text ->
                speechBubble.text = text
            }
        }

        binding.root.post {
            clampAvatarOverlayTranslation(animated = false)
            updateAvatarVoiceEntry()
        }
    }

    private fun applyAvatarOverlayForDestination(destinationId: Int) {
        val avatarHiddenDestinations = setOf(
            CommonR.id.navigation_cloud_auth
        )
        val showAvatar = destinationId !in avatarHiddenDestinations
        avatarOverlayEnabled = showAvatar
        binding.globalAvatarView.setRendererEnabled(showAvatar)
        binding.globalAvatarHalo.visibility = if (showAvatar) View.VISIBLE else View.GONE
        binding.globalAvatarView.visibility = if (showAvatar) View.VISIBLE else View.GONE
        updateAvatarVoiceEntry(destinationId)
        if (!showAvatar) {
            binding.globalAvatarSpeechBubble.visibility = View.GONE
            stopAvatarMotion()
            return
        }

        val isDoctorPage = destinationId == CommonR.id.navigation_doctor
        val isHomePage = destinationId == CommonR.id.navigation_home
        val isTrendPage = destinationId == CommonR.id.navigation_trend
        val isDevicePage = destinationId == CommonR.id.navigation_device
        val isProfilePage = destinationId == CommonR.id.navigation_profile ||
            destinationId in profileSettingsDestinationIds
        val isInterventionHubPage = destinationId == CommonR.id.navigation_intervention_center
        val isExecutionPage = destinationId in setOf(
            CommonR.id.navigation_relax_hub,
            CommonR.id.navigation_relax_center_legacy,
            CommonR.id.navigation_breathing_coach,
            CommonR.id.navigation_medical_report_analyze,
            CommonR.id.navigation_relax_review,
            CommonR.id.navigation_assessment_baseline,
            CommonR.id.navigation_intervention_profile,
            CommonR.id.navigation_intervention_session
        )
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val isMinimalPage = isTrendPage || isDevicePage || isProfilePage
        val useCompactAvatar = preferCompactAvatarMode || isHomePage || isMinimalPage
        val useMediumAvatar = isInterventionHubPage || isExecutionPage

        val avatarWidthDp = when {
            isDoctorPage && isLandscape -> 112
            isDoctorPage -> 128
            useMediumAvatar && isLandscape -> 112
            useMediumAvatar -> 120
            isLandscape && useCompactAvatar -> 108
            useCompactAvatar -> 104
            else -> 146
        }
        val avatarHeightDp = when {
            isDoctorPage && isLandscape -> 152
            isDoctorPage -> 174
            useMediumAvatar && isLandscape -> 148
            useMediumAvatar -> 160
            isLandscape && useCompactAvatar -> 146
            useCompactAvatar -> 138
            else -> 204
        }
        val avatarEndMarginDp = when {
            isDoctorPage -> 16
            useMediumAvatar -> 16
            useCompactAvatar -> 14
            else -> 12
        }
        val avatarBottomMarginDp = when {
            isDoctorPage && isLandscape -> 198
            isDoctorPage -> 166
            useMediumAvatar -> 190
            useCompactAvatar -> 202
            else -> 148
        }
        val bubbleMaxWidthDp = when {
            isDoctorPage -> 112
            useMediumAvatar -> 118
            useCompactAvatar -> 102
            else -> 132
        }

        binding.globalAvatarView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = dpToPx(avatarWidthDp)
            height = dpToPx(avatarHeightDp)
            marginEnd = dpToPx(avatarEndMarginDp)
            bottomMargin = dpToPx(avatarBottomMarginDp)
        }
        binding.globalAvatarHalo.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = dpToPx(avatarWidthDp + 32)
            height = dpToPx(avatarHeightDp + 32)
            marginEnd = dpToPx((avatarEndMarginDp - 4).coerceAtLeast(0))
            bottomMargin = dpToPx((avatarBottomMarginDp - 6).coerceAtLeast(0))
        }
        binding.globalAvatarSpeechBubble.maxWidth = dpToPx(bubbleMaxWidthDp)
        binding.globalAvatarSpeechBubble.updateLayoutParams<ConstraintLayout.LayoutParams> {
            marginEnd = dpToPx((avatarEndMarginDp + 2).coerceAtLeast(0))
            bottomMargin = dpToPx(6)
        }
        avatarBubbleOffsetX = -dpToPx(
            when {
                useCompactAvatar -> 70
                useMediumAvatar -> 78
                else -> 86
            }
        ).toFloat()
        avatarBubbleOffsetY = dpToPx(
            when {
                useCompactAvatar -> 20
                useMediumAvatar -> 26
                else -> 32
            }
        ).toFloat()
        binding.globalAvatarView.alpha = 1f
        binding.globalAvatarHalo.alpha = 0.32f
        binding.globalAvatarSpeechBubble.alpha = if (binding.globalAvatarSpeechBubble.visibility == View.VISIBLE) 1f else 0f
        updateAvatarMotion(avatarIsSpeaking)
        binding.root.post {
            clampAvatarOverlayTranslation(animated = false)
        }
    }

    private fun clampAvatarOverlayTranslation(animated: Boolean) {
        val avatarView = binding.globalAvatarView
        if (binding.root.width <= 0 || avatarView.width <= 0) {
            return
        }
        val safeMargin = dpToPx(10)
        val safeTop = dpToPx(72)
        val safeBottom = if (binding.bottomNavigation.top > 0) {
            binding.bottomNavigation.top - dpToPx(12)
        } else {
            binding.root.height - dpToPx(120)
        }
        val minTranslationX = (safeMargin - avatarView.left).toFloat()
        val maxTranslationX = (binding.root.width - safeMargin - avatarView.right).toFloat()
        val minTranslationY = (safeTop - avatarView.top).toFloat()
        val maxTranslationY = (safeBottom - avatarView.bottom).toFloat().coerceAtLeast(minTranslationY)
        avatarTranslationX = avatarTranslationX.coerceIn(minTranslationX, maxTranslationX)
        avatarTranslationY = avatarTranslationY.coerceIn(minTranslationY, maxTranslationY)
        applyAvatarOverlayTranslation(animated)
    }

    private fun applyAvatarOverlayTranslation(animated: Boolean) {
        val duration = if (animated) 180L else 0L
        if (animated) {
            binding.globalAvatarHalo.animate()
                .translationX(avatarTranslationX)
                .translationY(avatarTranslationY)
                .setDuration(duration)
                .start()
            binding.globalAvatarView.animate()
                .translationX(avatarTranslationX)
                .translationY(avatarTranslationY)
                .setDuration(duration)
                .start()
            binding.globalAvatarSpeechBubble.animate()
                .translationX(avatarTranslationX + avatarBubbleOffsetX)
                .translationY(avatarTranslationY + avatarBubbleOffsetY)
                .setDuration(duration)
                .start()
        } else {
            binding.globalAvatarHalo.translationX = avatarTranslationX
            binding.globalAvatarHalo.translationY = avatarTranslationY
            binding.globalAvatarView.translationX = avatarTranslationX
            binding.globalAvatarView.translationY = avatarTranslationY
            binding.globalAvatarSpeechBubble.translationX = avatarTranslationX + avatarBubbleOffsetX
            binding.globalAvatarSpeechBubble.translationY = avatarTranslationY + avatarBubbleOffsetY
        }
    }

    private fun updateAvatarMotion(isSpeaking: Boolean) {
        avatarIsSpeaking = isSpeaking
        startAvatarMotion()
        if (isSpeaking) {
            startAvatarSpeakingPulse()
        } else {
            stopAvatarSpeakingPulse()
            applyAvatarMotionFrame(avatarMotionProgress)
        }
    }

    private fun startAvatarMotion() {
        if (!avatarOverlayEnabled) return
        if (avatarMotionAnimator != null) return
        avatarMotionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                avatarMotionProgress = animator.animatedValue as Float
                applyAvatarMotionFrame(avatarMotionProgress)
            }
            start()
        }
    }

    private fun stopAvatarMotion() {
        avatarMotionAnimator?.cancel()
        avatarMotionAnimator = null
        stopAvatarSpeakingPulse()
        avatarTapAnimator?.cancel()
        avatarTapAnimator = null
        avatarTapBoost = 0f
        binding.globalAvatarHalo.animate().cancel()
        binding.globalAvatarView.animate()
            .scaleX(1f)
            .scaleY(1f)
            .rotation(0f)
            .setDuration(160L)
            .start()
        binding.globalAvatarHalo.scaleX = 1f
        binding.globalAvatarHalo.scaleY = 1f
        binding.globalAvatarHalo.rotation = 0f
    }

    private fun startAvatarSpeakingPulse() {
        avatarSpeakingAnimator?.cancel()
        avatarSpeakingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 720L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                if (!avatarOverlayEnabled || !avatarIsSpeaking) return@addUpdateListener
                applyAvatarMotionFrame(avatarMotionProgress, animator.animatedValue as Float)
            }
            start()
        }
    }

    private fun stopAvatarSpeakingPulse() {
        avatarSpeakingAnimator?.cancel()
        avatarSpeakingAnimator = null
    }

    private fun animateAvatarTapAccent() {
        avatarTapAnimator?.cancel()
        avatarTapAnimator = ValueAnimator.ofFloat(0.11f, 0f).apply {
            duration = 360L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                avatarTapBoost = animator.animatedValue as Float
                applyAvatarMotionFrame(avatarMotionProgress)
            }
            start()
        }
    }

    private fun applyAvatarMotionFrame(progress: Float, speakPulse: Float = if (avatarIsSpeaking) 0.5f else 0f) {
        val idleScale = 1f + 0.018f * progress
        val speakBoost = if (avatarIsSpeaking) 0.032f * speakPulse else 0f
        val tapBoost = avatarTapBoost
        binding.globalAvatarView.scaleX = idleScale + speakBoost + tapBoost
        binding.globalAvatarView.scaleY = idleScale + speakBoost + tapBoost
        binding.globalAvatarView.rotation = 0f

        binding.globalAvatarHalo.scaleX = 1.01f + 0.04f * progress + tapBoost * 0.38f
        binding.globalAvatarHalo.scaleY = 1.01f + 0.04f * progress + tapBoost * 0.38f
        binding.globalAvatarHalo.rotation = 0f
        binding.globalAvatarHalo.alpha = if (avatarIsSpeaking) {
            0.34f + 0.12f * speakPulse
        } else {
            0.26f + 0.10f * progress
        }
    }

    private fun dpToPx(valueDp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            valueDp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun speakAvatarAudio(text: String, audioDataUrl: String) {
        avatarSpeechPlaybackController.play(
            text = text,
            audioDataUrl = audioDataUrl,
            onStart = {
                AvatarController.speakWithAudio(text)
                updateAvatarVoiceEntry()
            },
            onComplete = {
                AvatarController.stopAudio()
                updateAvatarVoiceEntry()
            },
            onError = {
                AvatarController.speak(text)
                updateAvatarVoiceEntry()
            }
        )
    }

    override fun stopAvatarAudio() {
        avatarNarrationJob?.cancel()
        avatarSpeechPlaybackController.stop()
        AvatarController.stopAudio()
        updateAvatarVoiceEntry()
    }

    private fun requestDesktopAvatarNarration(
        destinationId: Int,
        trigger: String
    ) {
        if (!avatarOverlayEnabled) return
        if (trigger == "enter") {
            avatarSpeechPlaybackController.stop()
            AvatarController.stopAudio()
        }
        if (
            shouldReplayExistingNarration(
                destinationId = destinationId,
                trigger = trigger
            )
        ) {
            playNarration(lastAvatarNarration!!)
            return
        }
        avatarNarrationJob?.cancel()
        updateAvatarVoiceEntry(destinationId)
        avatarNarrationJob = lifecycleScope.launch {
            val context = buildPageNarrationContext(destinationId, trigger)
            val narration = desktopAvatarNarrationService.generate(context)
            lastAvatarNarration = narration
            lastAvatarNarrationDestinationId = destinationId
            lastAvatarNarrationTrigger = trigger
            playNarration(narration)
        }.also { job ->
            job.invokeOnCompletion { updateAvatarVoiceEntry(destinationId) }
        }
    }

    private fun shouldReplayExistingNarration(destinationId: Int, trigger: String): Boolean {
        val narration = lastAvatarNarration ?: return false
        if (lastAvatarNarrationDestinationId != destinationId) return false
        if (avatarSpeechPlaybackController.isPlaying()) return false

        // Entry narrations are prewarmed asynchronously. On the first tap they often
        // have no cached audio yet, so replaying them would only show text without
        // voice. In that case, force a fresh interactive narration instead.
        if (
            trigger in setOf("tap", "button", "replay") &&
            lastAvatarNarrationTrigger == "enter" &&
            narration.audioDataUrl.isBlank()
        ) {
            return false
        }

        return true
    }

    private fun prewarmDesktopAvatarNarrations() {
        val destinationIds = listOf(
            CommonR.id.navigation_home,
            CommonR.id.navigation_doctor,
            CommonR.id.navigation_trend,
            CommonR.id.navigation_device,
            CommonR.id.navigation_profile,
            CommonR.id.navigation_profile_personal_info,
            CommonR.id.navigation_profile_notifications,
            CommonR.id.navigation_profile_privacy,
            CommonR.id.navigation_profile_about,
            CommonR.id.navigation_intervention_center,
            CommonR.id.navigation_relax_hub,
            CommonR.id.navigation_relax_center_legacy,
            CommonR.id.navigation_medical_report_analyze
        )
        lifecycleScope.launch {
            destinationIds.forEach { destinationId ->
                desktopAvatarNarrationService.prewarmLocalNarration(
                    buildStaticPageNarrationContext(destinationId)
                )
            }
        }
    }

    private fun playNarration(narration: AvatarNarration) {
        applyAvatarNarrationAction(narration.semanticAction, narration.text)
        if (isAvatarSpeechEnabled() && narration.audioDataUrl.isNotBlank()) {
            speakAvatarAudio(narration.text, narration.audioDataUrl)
        } else {
            AvatarController.speak(narration.text)
            updateAvatarVoiceEntry()
        }
    }

    private fun applyAvatarNarrationAction(semanticAction: String, text: String) {
        val lowered = text.lowercase()
        val action = when {
            semanticAction.isNotBlank() -> semanticAction
            text.contains("风险") || text.contains("预警") || lowered.contains("alert") -> "alert"
            text.contains("点击") || text.contains("进入") || text.contains("打开") -> "point"
            text.contains("继续") || text.contains("先") || text.contains("建议") -> "encourage"
            else -> "wave"
        }
        AvatarController.performSemanticAction(action)
    }

    private fun buildPageNarrationContext(destinationId: Int, trigger: String): PageNarrationContext {
        val fragmentRoot = currentPageRootView()
        val highlights = collectVisibleTexts(fragmentRoot)
        val pageTitle = resolvePageTitle(destinationId, highlights)
        val pageSubtitle = resolvePageSubtitle(destinationId, highlights)
        val riskSummary = highlights.firstOrNull {
            it.contains("风险") || it.contains("预警") || it.contains("异常")
        }.orEmpty()
        val userStateSummary = highlights
            .filterNot { it == pageTitle || it == pageSubtitle }
            .take(3)
            .joinToString("；")
        return PageNarrationContext(
            destinationId = destinationId,
            pageKey = resolvePageKey(destinationId),
            pageTitle = pageTitle,
            pageSubtitle = pageSubtitle,
            visibleHighlights = highlights.take(8),
            userStateSummary = userStateSummary,
            riskSummary = riskSummary,
            actionHint = resolveActionHint(destinationId),
            trigger = trigger
        )
    }

    private fun buildStaticPageNarrationContext(destinationId: Int): PageNarrationContext {
        val emptyHighlights = emptyList<String>()
        return PageNarrationContext(
            destinationId = destinationId,
            pageKey = resolvePageKey(destinationId),
            pageTitle = resolvePageTitle(destinationId, emptyHighlights),
            pageSubtitle = resolvePageSubtitle(destinationId, emptyHighlights),
            visibleHighlights = emptyHighlights,
            userStateSummary = "",
            riskSummary = "",
            actionHint = resolveActionHint(destinationId),
            trigger = "enter"
        )
    }

    private fun currentPageRootView(): View? {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        return navHost?.childFragmentManager?.primaryNavigationFragment?.view
    }

    private fun collectVisibleTexts(root: View?): List<String> {
        if (root == null) return emptyList()
        val results = linkedSetOf<String>()
        fun walk(view: View) {
            if (!view.isShown) return
            when (view) {
                is TextView -> {
                    val text = view.text?.toString()?.trim().orEmpty()
                    if (text.length in 2..48) {
                        results += text
                    }
                }
                is ViewGroup -> {
                    repeat(view.childCount) { index ->
                        walk(view.getChildAt(index))
                    }
                }
            }
        }
        walk(root)
        return results.toList()
    }

    private fun resolvePageTitle(destinationId: Int, highlights: List<String>): String {
        val fallback = when (destinationId) {
            CommonR.id.navigation_home -> getString(CommonR.string.home_page_title_clean)
            CommonR.id.navigation_doctor -> getString(CommonR.string.doctor_page_title)
            CommonR.id.navigation_trend -> getString(CommonR.string.trend_page_title_clean)
            CommonR.id.navigation_device -> getString(CommonR.string.device_page_title_clean)
            CommonR.id.navigation_profile -> getString(CommonR.string.profile_page_title_clean)
            CommonR.id.navigation_profile_personal_info -> getString(CommonR.string.profile_personal_info_title)
            CommonR.id.navigation_profile_notifications -> getString(CommonR.string.profile_notification_settings_title)
            CommonR.id.navigation_profile_privacy -> getString(CommonR.string.profile_privacy_settings_title)
            CommonR.id.navigation_profile_about -> getString(CommonR.string.profile_about_project_title)
            CommonR.id.navigation_intervention_center -> getString(CommonR.string.intervention_center_title)
            CommonR.id.navigation_relax_hub -> getString(CommonR.string.symptom_guide_title)
            CommonR.id.navigation_relax_center_legacy -> getString(CommonR.string.relax_hub_title)
            CommonR.id.navigation_breathing_coach -> getString(CommonR.string.relax_breathing_title)
            CommonR.id.navigation_medical_report_analyze -> getString(CommonR.string.medical_report_title)
            else -> getString(CommonR.string.avatar_guide_default)
        }
        return highlights.firstOrNull()?.takeIf { it.length <= 18 } ?: fallback
    }

    private fun resolvePageSubtitle(destinationId: Int, highlights: List<String>): String {
        val fallback = when (destinationId) {
            CommonR.id.navigation_home -> getString(CommonR.string.home_page_subtitle_clean)
            CommonR.id.navigation_doctor -> getString(CommonR.string.doctor_chat_subtitle_clean)
            CommonR.id.navigation_trend -> getString(CommonR.string.trend_page_subtitle_clean)
            CommonR.id.navigation_device -> getString(CommonR.string.device_page_subtitle_clean)
            CommonR.id.navigation_profile -> getString(CommonR.string.profile_page_subtitle_clean)
            CommonR.id.navigation_profile_personal_info -> "继续维护你的云端资料与基础档案。"
            CommonR.id.navigation_profile_notifications -> "调整通知、提醒和桌面机器人播报方式。"
            CommonR.id.navigation_profile_privacy -> "查看账号状态并管理本地数据与隐私选项。"
            CommonR.id.navigation_profile_about -> "查看版本、项目定位和当前能力说明。"
            CommonR.id.navigation_intervention_center -> getString(CommonR.string.intervention_center_subtitle)
            else -> getString(CommonR.string.avatar_voice_entry_hint)
        }
        return highlights.drop(1).firstOrNull()?.takeIf { it.length <= 40 } ?: fallback
    }

    private fun resolveActionHint(destinationId: Int): String {
        return when (destinationId) {
            CommonR.id.navigation_home -> "提醒用户先看今日重点，再决定是否进入干预中心或趋势页。"
            CommonR.id.navigation_doctor -> "提醒用户先说清当前不适，再决定继续半双工通话还是补充文字。"
            CommonR.id.navigation_trend -> "提醒用户先看周报结论，再决定是否进入干预中心。"
            CommonR.id.navigation_device -> "提醒用户先确认连接和同步，再考虑高级工具。"
            CommonR.id.navigation_profile -> "提醒用户先处理账号和设置，再通过快捷入口回到主流程。"
            CommonR.id.navigation_profile_personal_info -> "提醒用户先核对用户名、年龄和性别，再保存资料。"
            CommonR.id.navigation_profile_notifications -> "提醒用户先确认通知总开关，再调整提醒和机器人播报。"
            CommonR.id.navigation_profile_privacy -> "提醒用户先确认当前登录状态，再决定是否清理本地数据。"
            CommonR.id.navigation_profile_about -> "解释当前版本信息，并提示用户返回主流程继续使用。"
            CommonR.id.navigation_intervention_center -> "提醒用户从最贴近当前问题的干预入口开始。"
            CommonR.id.navigation_relax_center_legacy -> "提醒用户先选中不适部位，再查看完整示意图和对应干预建议。"
            CommonR.id.navigation_relax_hub -> "提醒用户先完成症状定位，再生成辅助判断。"
            CommonR.id.navigation_breathing_coach -> "提醒用户先跟随当前节奏稳定呼吸，再根据体感决定是否继续。"
            CommonR.id.navigation_medical_report_analyze -> "提醒用户先看可读摘要，再决定是否校对 OCR 文本。"
            CommonR.id.navigation_assessment_baseline -> "提醒用户先完成基线评估，再生成更合适的干预方案。"
            CommonR.id.navigation_intervention_profile -> "提醒用户先看当前方案目标和结构，再决定是否开始执行。"
            CommonR.id.navigation_intervention_session -> "提醒用户先跟随当前步骤执行，再反馈完成情况和体感变化。"
            else -> "解释当前页面并给出一个自然的下一步动作。"
        }
    }

    private fun resolvePageKey(destinationId: Int): String {
        return when (destinationId) {
            CommonR.id.navigation_home -> "home"
            CommonR.id.navigation_doctor -> "doctor"
            CommonR.id.navigation_trend -> "trend"
            CommonR.id.navigation_device -> "device"
            CommonR.id.navigation_profile -> "profile"
            CommonR.id.navigation_profile_personal_info -> "profile_personal_info"
            CommonR.id.navigation_profile_notifications -> "profile_notifications"
            CommonR.id.navigation_profile_privacy -> "profile_privacy"
            CommonR.id.navigation_profile_about -> "profile_about"
            CommonR.id.navigation_intervention_center -> "intervention_center"
            CommonR.id.navigation_relax_hub -> "symptom_guide"
            CommonR.id.navigation_relax_center_legacy -> "relax_center"
            CommonR.id.navigation_breathing_coach -> "breathing_coach"
            CommonR.id.navigation_medical_report_analyze -> "medical_report"
            CommonR.id.navigation_relax_review -> "relax_review"
            CommonR.id.navigation_assessment_baseline -> "assessment_baseline"
            CommonR.id.navigation_intervention_profile -> "intervention_profile"
            CommonR.id.navigation_intervention_session -> "intervention_session"
            else -> "unknown"
        }
    }

    private fun updateAvatarVoiceEntry(destinationId: Int = selectedTopLevelDestinationId) {
        val shouldShow = avatarOverlayEnabled &&
            destinationId != CommonR.id.navigation_doctor &&
            isAvatarSpeechEnabled()
        binding.btnGlobalAvatarVoice.visibility = if (shouldShow) View.VISIBLE else View.GONE
        if (!shouldShow) return
        binding.btnGlobalAvatarVoice.text = if (avatarSpeechPlaybackController.isPlaying() || avatarNarrationJob?.isActive == true) {
            getString(CommonR.string.avatar_voice_stop)
        } else {
            getString(CommonR.string.avatar_voice_entry)
        }
    }

    private fun isAvatarSpeechEnabled(): Boolean {
        return ProfileSettingsStore.getNotificationSettings(this).avatarSpeechEnabled
    }

    private fun shouldPreferCompactAvatarMode(): Boolean {
        val manufacturer = android.os.Build.MANUFACTURER.orEmpty().lowercase(java.util.Locale.US)
        val model = android.os.Build.MODEL.orEmpty().lowercase(java.util.Locale.US)
        return manufacturer == "oppo" && model == "opd2405"
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onDestroy() {
        avatarNarrationJob?.cancel()
        if (::avatarSpeechPlaybackController.isInitialized) {
            avatarSpeechPlaybackController.stop()
        }
        super.onDestroy()
    }
}
