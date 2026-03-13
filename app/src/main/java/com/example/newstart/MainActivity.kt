package com.example.newstart

import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.newstart.databinding.ActivityMainNewBinding
import com.example.newstart.demo.DemoConfig
import com.example.newstart.demo.DemoDataPreloader
import com.example.newstart.ui.avatar.AvatarController
import com.example.newstart.ui.avatar.AvatarSpeechPlaybackController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main Activity with bottom navigation and demo mode badge.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainNewBinding
    private lateinit var avatarSpeechPlaybackController: AvatarSpeechPlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainNewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        avatarSpeechPlaybackController = AvatarSpeechPlaybackController(this)

        initDemoMode()
        setupBottomNavigation()
        showDemoBadgeIfNeeded()
        setupAvatarGuide()
    }

    override fun onResume() {
        super.onResume()
        showDemoBadgeIfNeeded()
    }

    private fun setupBottomNavigation() {
        val navController = findNavController(R.id.nav_host_fragment)
        binding.bottomNavigation.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            applyAvatarOverlayForDestination(destination.id)
            AvatarController.onPageEntered(destination.id)
        }
        navController.currentDestination?.let { destination ->
            applyAvatarOverlayForDestination(destination.id)
            AvatarController.onPageEntered(destination.id)
        }
    }

    private fun setupAvatarGuide() {
        val avatarView = binding.globalAvatarView
        val speechBubble = binding.globalAvatarSpeechBubble

        avatarView.setOnAvatarTapListener {
            if (avatarSpeechPlaybackController.isPlaying()) {
                avatarSpeechPlaybackController.stop()
                AvatarController.stopAudio()
            } else {
                AvatarController.onAvatarTapped()
            }
        }

        lifecycleScope.launch {
            AvatarController.currentRole.collectLatest { role ->
                avatarView.playRole(role)
            }
        }

        lifecycleScope.launch {
            AvatarController.isSpeaking.collectLatest { isSpeaking ->
                if (isSpeaking) {
                    speechBubble.visibility = android.view.View.VISIBLE
                    speechBubble.alpha = 0f
                    speechBubble.animate().alpha(1f).setDuration(300).start()
                } else {
                    speechBubble.animate().alpha(0f).setDuration(300).withEndAction {
                        speechBubble.visibility = android.view.View.GONE
                    }.start()
                }
            }
        }

        lifecycleScope.launch {
            AvatarController.speechText.collectLatest { text ->
                speechBubble.text = text
            }
        }
    }

    private fun initDemoMode() {
        DemoConfig.init(this)

        if (DemoConfig.isDemoMode) {
            lifecycleScope.launch {
                try {
                    DemoDataPreloader(this@MainActivity).preloadDemoData()

                    // Trigger a test speech to verify Avatar integration.
                    kotlinx.coroutines.delay(2000)
                    AvatarController.speak("欢迎来到新起点，有什么我可以帮您？")
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Preload demo data failed", e)
                }
            }
        }
    }

    private fun applyAvatarOverlayForDestination(destinationId: Int) {
        val isDoctorPage = destinationId == R.id.navigation_doctor
        val isSymptomGuidePage = destinationId == R.id.navigation_relax_hub
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isSymptomGuidePage) {
            binding.globalAvatarHalo.visibility = android.view.View.GONE
            binding.globalAvatarView.visibility = android.view.View.GONE
            binding.globalAvatarSpeechBubble.visibility = android.view.View.GONE
            return
        }

        binding.globalAvatarHalo.visibility = android.view.View.VISIBLE
        binding.globalAvatarView.visibility = android.view.View.VISIBLE

        val avatarWidthDp = when {
            isDoctorPage && isLandscape -> 88
            isDoctorPage -> 108
            else -> 220
        }
        val avatarHeightDp = when {
            isDoctorPage && isLandscape -> 120
            isDoctorPage -> 148
            else -> 300
        }
        val avatarEndMarginDp = if (isDoctorPage) 8 else -12
        val avatarBottomMarginDp = when {
            isDoctorPage && isLandscape -> 188
            isDoctorPage -> 128
            else -> 52
        }
        val bubbleMaxWidthDp = if (isDoctorPage) 160 else 200

        binding.globalAvatarView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = dpToPx(avatarWidthDp)
            height = dpToPx(avatarHeightDp)
            marginEnd = dpToPx(avatarEndMarginDp)
            bottomMargin = dpToPx(avatarBottomMarginDp)
        }
        binding.globalAvatarSpeechBubble.maxWidth = dpToPx(bubbleMaxWidthDp)
    }

    private fun dpToPx(valueDp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            valueDp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun showDemoBadgeIfNeeded() {
        binding.demoBadge.root.visibility =
            if (DemoConfig.isDemoMode) android.view.View.VISIBLE else android.view.View.GONE
    }

    fun speakAvatarAudio(text: String, audioDataUrl: String) {
        AvatarController.speakWithAudio(text)
        avatarSpeechPlaybackController.play(
            text = text,
            audioDataUrl = audioDataUrl,
            onStart = {
                AvatarController.speakWithAudio(text)
            },
            onComplete = {
                AvatarController.stopAudio()
            },
            onError = {
                AvatarController.speak(text)
            }
        )
    }

    fun refreshDemoModeUi() {
        showDemoBadgeIfNeeded()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onDestroy() {
        if (::avatarSpeechPlaybackController.isInitialized) {
            avatarSpeechPlaybackController.stop()
        }
        super.onDestroy()
    }
}
