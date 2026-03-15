package com.example.newstart.ui.intervention

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.newstart.core.common.R
import com.example.newstart.feature.relax.databinding.FragmentInterventionSessionBinding
import com.example.newstart.intervention.HapticPatternMode
import com.example.newstart.intervention.InterventionExperienceMetadata
import com.example.newstart.intervention.InterventionExperienceModality
import com.example.newstart.intervention.PersonalizationLevel
import com.example.newstart.ui.relax.BreathingCoachUiState
import com.example.newstart.ui.relax.BreathingPhase
import com.example.newstart.ui.relax.HapticPacingController
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

class InterventionSessionFragment : Fragment() {

    private var _binding: FragmentInterventionSessionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InterventionSessionViewModel by viewModels()

    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioResId: Int = 0
    private var audioPlaybackFinished: Boolean = false
    private var indicatorPulseAnimator: AnimatorSet? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var soundscapeEngine: AdaptiveSoundscapeEngine
    private lateinit var hapticController: HapticPacingController
    private var soundscapePrefsApplied = false
    private var updatingSoundscapeControls = false

    private val audioProgressRunnable = object : Runnable {
        override fun run() {
            syncAudioFeedback()
            if (mediaPlayer?.isPlaying == true) {
                mainHandler.postDelayed(this, AUDIO_PROGRESS_INTERVAL_MS)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInterventionSessionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        soundscapeEngine = AdaptiveSoundscapeEngine(requireContext())
        hapticController = HapticPacingController(requireContext())
        viewModel.initialize(
            protocolCode = arguments?.getString("protocolCode"),
            title = arguments?.getString("protocolTitle"),
            itemType = arguments?.getString("itemType"),
            durationSec = arguments?.getInt("durationSec") ?: 0,
            rationale = arguments?.getString("rationale")
        )
        setupActions()
        observeData()
    }

    private fun setupActions() {
        binding.btnInterventionSessionBack.setOnClickListener {
            if (!findNavController().popBackStack()) {
                findNavController().navigate(R.id.navigation_relax_hub)
            }
        }
        binding.seekInterventionBefore.setOnSeekBarChangeListener(SimpleSeekBarListener { value, _ ->
            viewModel.updateBeforeStress(value)
        })
        binding.seekInterventionAfter.setOnSeekBarChangeListener(SimpleSeekBarListener { value, _ ->
            viewModel.updateAfterStress(value)
        })
        binding.btnInterventionSessionStart.setOnClickListener {
            val state = viewModel.uiState.value ?: return@setOnClickListener
            val preferences = loadAssistPreferences()
            if (state.isSoundscapeSession) {
                soundscapeEngine.prepare()
                applySoundscapeMix(fromUser = false)
                soundscapeEngine.play()
                if (preferences.hapticsEnabled) {
                    hapticController.pulseSessionAccent(preferences.hapticMode)
                }
            }
            viewModel.startSession()
        }
        binding.btnInterventionSessionComplete.setOnClickListener {
            val state = viewModel.uiState.value ?: return@setOnClickListener
            if (state.isSoundscapeSession) {
                soundscapeEngine.pause()
                hapticController.stop()
                viewModel.completeSession(buildSoundscapeMetadata(state))
            } else {
                viewModel.completeSession()
            }
        }
        binding.btnInterventionSessionCoach.setOnClickListener {
            val state = viewModel.uiState.value ?: return@setOnClickListener
            if (!state.showBreathingCoachEntry) return@setOnClickListener
            findNavController().navigate(
                R.id.navigation_breathing_coach,
                bundleOf(
                    "protocolType" to state.breathingCoachProtocolType,
                    "durationSec" to state.durationSec,
                    "taskId" to ""
                )
            )
        }
        binding.btnInterventionSessionAudioToggle.setOnClickListener {
            toggleAudio()
        }
        binding.switchSoundscapeAdaptive.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSoundscapeControls) return@setOnCheckedChangeListener
            viewModel.updateAdaptiveSoundscapeEnabled(isChecked)
            applySoundscapeMix(fromUser = false)
        }
        configureSoundscapeSeekBar(
            binding.seekSoundscapeRain,
            binding.tvSoundscapeRainValue
        )
        configureSoundscapeSeekBar(
            binding.seekSoundscapeFire,
            binding.tvSoundscapeFireValue
        )
        configureSoundscapeSeekBar(
            binding.seekSoundscapeNight,
            binding.tvSoundscapeNightValue
        )
        configureSoundscapeSeekBar(
            binding.seekSoundscapeLow,
            binding.tvSoundscapeLowValue
        )
    }

    private fun observeData() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.tvInterventionSessionTitle.text = state.title
            binding.tvInterventionSessionSubtitle.text = state.subtitle
            binding.tvInterventionSessionRationale.text = state.rationale.ifBlank {
                getString(R.string.intervention_session_default_desc)
            }
            binding.tvInterventionSessionPersonalizationBadge.text = state.personalizationLabel
            binding.tvInterventionSessionPersonalizationSummary.text = state.personalizationSummary
            binding.tvInterventionSessionPersonalizationMissing.isVisible =
                state.missingInputSummary.isNotBlank()
            binding.tvInterventionSessionPersonalizationMissing.text = state.missingInputSummary
            applyPersonalizationVisualState(state.personalizationLevel)
            binding.tvInterventionSessionType.text = state.itemTypeLabel
            binding.tvInterventionSessionTiming.text = state.timingLabel
            binding.tvInterventionSessionDuration.text = state.durationLabel
            binding.tvInterventionSessionMode.text = state.modeLabel
            binding.tvInterventionSessionSteps.text = state.stepsText.ifBlank {
                getString(R.string.intervention_session_steps_empty)
            }
            binding.cardInterventionSessionAudio.isVisible = state.showAudioCard
            binding.cardInterventionSessionSoundscape.isVisible = state.isSoundscapeSession
            binding.tvInterventionSessionAudioTitle.text = state.audioTitle
            binding.tvInterventionSessionAudioSubtitle.text = state.audioSubtitle
            binding.tvInterventionSessionAudioSource.text = state.audioSourceText
            binding.tvInterventionSessionStoryboard.text = state.storyboardText.ifBlank {
                getString(R.string.intervention_session_storyboard_empty)
            }
            binding.tvInterventionSessionMethodSource.text = state.methodSourceText
            binding.layoutInterventionSessionAudioStatus.isVisible = state.showAudioCard
            binding.tvInterventionSessionCompletion.text = state.completionRule
            val min = state.remainingSec / 60
            val sec = state.remainingSec % 60
            binding.tvInterventionSessionTimer.text = if (state.hasStarted) {
                getString(R.string.intervention_session_running, min, sec)
            } else {
                getString(R.string.intervention_session_ready)
            }
            if (currentAudioResId != 0 && currentAudioResId != state.audioResId) {
                releaseAudio()
            }
            currentAudioResId = state.audioResId
            binding.tvInterventionBeforeValue.text = state.beforeStress.toString()
            binding.tvInterventionAfterValue.text = state.afterStress.toString()
            if (binding.seekInterventionBefore.progress != state.beforeStress) {
                binding.seekInterventionBefore.progress = state.beforeStress
            }
            if (binding.seekInterventionAfter.progress != state.afterStress) {
                binding.seekInterventionAfter.progress = state.afterStress
            }
            binding.btnInterventionSessionStart.isEnabled = !state.hasStarted && !state.isCompleted
            binding.btnInterventionSessionComplete.isEnabled =
                state.hasStarted && !state.isRunning && !state.isCompleted
            binding.btnInterventionSessionCoach.isVisible = state.showBreathingCoachEntry

            if (state.isSoundscapeSession && !soundscapePrefsApplied) {
                val preferences = loadAssistPreferences()
                updatingSoundscapeControls = true
                binding.switchSoundscapeAdaptive.isChecked = preferences.adaptiveEnabled
                updatingSoundscapeControls = false
                viewModel.updateAdaptiveSoundscapeEnabled(preferences.adaptiveEnabled)
                soundscapePrefsApplied = true
            }

            if (state.isSoundscapeSession) {
                updatingSoundscapeControls = true
                if (binding.switchSoundscapeAdaptive.isChecked != state.adaptiveSoundscapeEnabled) {
                    binding.switchSoundscapeAdaptive.isChecked = state.adaptiveSoundscapeEnabled
                }
                updatingSoundscapeControls = false
                binding.tvSoundscapeStatus.text = when {
                    state.isRunning -> getString(R.string.soundscape_status_playing)
                    state.isCompleted -> getString(R.string.soundscape_status_finished)
                    state.hasStarted -> getString(R.string.soundscape_status_paused)
                    else -> getString(R.string.soundscape_status_idle)
                }
                binding.tvSoundscapeHint.text = state.soundscapeHint
                applySoundscapeMix(fromUser = false)
            }

            if (!state.showAudioCard) {
                stopAudioFeedbackTracking()
            } else {
                syncAudioFeedback()
            }
        }
        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                is InterventionSessionEvent.Toast -> {
                    Snackbar.make(binding.root, event.message, Snackbar.LENGTH_SHORT).show()
                    viewModel.consumeEvent()
                }

                InterventionSessionEvent.Completed -> {
                    soundscapeEngine.release()
                    hapticController.stop()
                    viewModel.consumeEvent()
                }

                null -> Unit
            }
        }
    }

    override fun onStop() {
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
        soundscapeEngine.pause()
        hapticController.stop()
        stopAudioFeedbackTracking()
        syncAudioFeedback()
        super.onStop()
    }

    override fun onDestroyView() {
        stopAudioFeedbackTracking()
        releaseAudio()
        soundscapeEngine.release()
        hapticController.stop()
        super.onDestroyView()
        _binding = null
    }

    private fun toggleAudio() {
        val state = viewModel.uiState.value ?: return
        val audioResId = state.audioResId
        if (!state.showAudioCard || audioResId == 0) {
            Snackbar.make(
                binding.root,
                getString(R.string.intervention_session_audio_unavailable),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        val player = obtainAudioPlayer(audioResId)
        if (player == null) {
            Snackbar.make(
                binding.root,
                getString(R.string.intervention_session_audio_unavailable),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        if (player.isPlaying) {
            player.pause()
            stopAudioFeedbackTracking()
            syncAudioFeedback()
        } else {
            if (audioPlaybackFinished || player.currentPosition >= player.duration.coerceAtLeast(0)) {
                player.seekTo(0)
            }
            audioPlaybackFinished = false
            player.start()
            startAudioFeedbackTracking()
            syncAudioFeedback()
        }
    }

    private fun releaseAudio() {
        stopAudioFeedbackTracking()
        mediaPlayer?.release()
        mediaPlayer = null
        currentAudioResId = 0
        audioPlaybackFinished = false
        if (_binding != null) {
            syncAudioFeedback()
        }
    }

    private fun obtainAudioPlayer(audioResId: Int): MediaPlayer? {
        if (mediaPlayer != null && currentAudioResId == audioResId) {
            return mediaPlayer
        }
        releaseAudio()
        mediaPlayer = MediaPlayer.create(requireContext(), audioResId)?.apply {
            setOnCompletionListener {
                audioPlaybackFinished = true
                stopAudioFeedbackTracking()
                syncAudioFeedback()
            }
        }
        currentAudioResId = audioResId
        return mediaPlayer
    }

    private fun startAudioFeedbackTracking() {
        mainHandler.removeCallbacks(audioProgressRunnable)
        mainHandler.post(audioProgressRunnable)
    }

    private fun stopAudioFeedbackTracking() {
        mainHandler.removeCallbacks(audioProgressRunnable)
        stopIndicatorPulse()
    }

    private fun syncAudioFeedback() {
        val safeBinding = _binding ?: return
        val state = viewModel.uiState.value ?: return
        if (!state.showAudioCard || state.audioResId == 0) {
            safeBinding.tvInterventionSessionAudioStatus.text =
                getString(R.string.intervention_session_audio_status_idle)
            safeBinding.tvInterventionSessionAudioProgress.text = getString(
                R.string.intervention_session_audio_progress_format,
                formatPlaybackTime(0),
                formatPlaybackTime(0)
            )
            safeBinding.progressInterventionSessionAudio.setProgressCompat(0, false)
            applyAudioVisualState(isPlaying = false, accentColor = R.color.status_disconnected)
            updateAudioButton(isPlaying = false)
            return
        }

        val player = mediaPlayer
        val durationMs = player?.duration?.takeIf { it > 0 } ?: 0
        val currentMs = when {
            audioPlaybackFinished && durationMs > 0 -> durationMs
            player != null -> player.currentPosition.coerceAtLeast(0)
            else -> 0
        }
        val isPlaying = player?.isPlaying == true
        val progress = if (durationMs > 0) {
            ((currentMs.toFloat() / durationMs.toFloat()) * AUDIO_PROGRESS_MAX)
                .toInt()
                .coerceIn(0, AUDIO_PROGRESS_MAX)
        } else {
            0
        }
        val statusRes = when {
            isPlaying -> R.string.intervention_session_audio_status_playing
            audioPlaybackFinished -> R.string.intervention_session_audio_status_finished
            currentMs > 0 -> R.string.intervention_session_audio_status_paused
            else -> R.string.intervention_session_audio_status_idle
        }
        val accentColor = when {
            isPlaying -> R.color.status_scanning
            audioPlaybackFinished -> R.color.status_positive
            currentMs > 0 -> R.color.status_warning
            else -> R.color.status_disconnected
        }
        safeBinding.tvInterventionSessionAudioStatus.text = getString(statusRes)
        safeBinding.tvInterventionSessionAudioProgress.text = getString(
            R.string.intervention_session_audio_progress_format,
            formatPlaybackTime(currentMs),
            formatPlaybackTime(durationMs)
        )
        safeBinding.progressInterventionSessionAudio.setProgressCompat(progress, true)
        applyAudioVisualState(isPlaying = isPlaying, accentColor = accentColor)
        updateAudioButton(isPlaying = isPlaying)
    }

    private fun applyAudioVisualState(isPlaying: Boolean, accentColor: Int) {
        val safeBinding = _binding ?: return
        val color = requireContext().getColor(accentColor)
        safeBinding.layoutInterventionSessionAudioStatus.backgroundTintList =
            ColorStateList.valueOf(requireContext().getColor(R.color.md_theme_light_primaryContainer))
        safeBinding.viewInterventionSessionAudioIndicator.backgroundTintList =
            ColorStateList.valueOf(color)
        safeBinding.cardInterventionSessionAudio.strokeWidth = dpToPx(if (isPlaying) 2 else 1)
        safeBinding.cardInterventionSessionAudio.strokeColor = color
        safeBinding.progressInterventionSessionAudio.setIndicatorColor(color)
        if (isPlaying) {
            startIndicatorPulse()
        } else {
            stopIndicatorPulse()
        }
    }

    private fun updateAudioButton(isPlaying: Boolean) {
        binding.btnInterventionSessionAudioToggle.text = getString(
            if (isPlaying) R.string.intervention_session_audio_pause else R.string.intervention_session_audio_play
        )
    }

    private fun startIndicatorPulse() {
        val safeBinding = _binding ?: return
        if (indicatorPulseAnimator?.isRunning == true) return
        val alphaAnimator = ObjectAnimator.ofFloat(
            safeBinding.viewInterventionSessionAudioIndicator,
            View.ALPHA,
            1f,
            0.35f
        ).apply {
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            duration = 720L
        }
        val scaleXAnimator = ObjectAnimator.ofFloat(
            safeBinding.viewInterventionSessionAudioIndicator,
            View.SCALE_X,
            1f,
            1.22f
        ).apply {
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            duration = 720L
        }
        val scaleYAnimator = ObjectAnimator.ofFloat(
            safeBinding.viewInterventionSessionAudioIndicator,
            View.SCALE_Y,
            1f,
            1.22f
        ).apply {
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            duration = 720L
        }
        indicatorPulseAnimator = AnimatorSet().apply {
            playTogether(alphaAnimator, scaleXAnimator, scaleYAnimator)
            start()
        }
    }

    private fun stopIndicatorPulse() {
        indicatorPulseAnimator?.cancel()
        indicatorPulseAnimator = null
        _binding?.viewInterventionSessionAudioIndicator?.apply {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
        }
    }

    private fun applySoundscapeMix(fromUser: Boolean) {
        val safeBinding = _binding ?: return
        val state = viewModel.uiState.value ?: return
        if (!state.isSoundscapeSession) return
        val mix = currentSoundscapeMix()
        if (fromUser) {
            soundscapeEngine.markManualAdjustment()
        }
        val adaptiveState = soundscapeEngine.buildAdaptiveState(
            mix,
            safeBinding.switchSoundscapeAdaptive.isChecked,
            state.soundscapeFeedback
        )
        safeBinding.tvSoundscapeMixSummary.text = "当前主导层：${adaptiveState.dominantLayerLabel}"
        safeBinding.progressSoundscape.setProgressCompat(soundscapeEngine.currentProgress(), true)
    }

    private fun buildSoundscapeMetadata(state: InterventionSessionUiState): InterventionExperienceMetadata {
        val preferences = loadAssistPreferences()
        return InterventionExperienceMetadata(
            modality = InterventionExperienceModality.SOUNDSCAPE,
            sessionVariant = state.protocolCode,
            hapticEnabled = preferences.hapticsEnabled,
            preferredHapticMode = preferences.hapticMode,
            realtimeSignalAvailable = state.soundscapeFeedback.hasRealtimeData,
            avgRelaxSignal = null,
            peakHeartRate = null,
            averageHrv = null,
            soundscapeMix = soundscapeEngine.currentMix(),
            manualAdjustCount = soundscapeEngine.buildAdaptiveState(
                currentSoundscapeMix(),
                binding.switchSoundscapeAdaptive.isChecked,
                state.soundscapeFeedback
            ).manualAdjustCount,
            completionQuality = 0
        )
    }

    private fun currentSoundscapeMix(): Map<String, Float> {
        return linkedMapOf(
            AdaptiveSoundscapeEngine.LAYER_RAIN to binding.seekSoundscapeRain.progress / 100f,
            AdaptiveSoundscapeEngine.LAYER_FIRE to binding.seekSoundscapeFire.progress / 100f,
            AdaptiveSoundscapeEngine.LAYER_NIGHT to binding.seekSoundscapeNight.progress / 100f,
            AdaptiveSoundscapeEngine.LAYER_LOW to binding.seekSoundscapeLow.progress / 100f
        )
    }

    private fun configureSoundscapeSeekBar(
        seekBar: android.widget.SeekBar,
        valueView: android.widget.TextView
    ) {
        valueView.text = formatMixValue(seekBar.progress)
        seekBar.setOnSeekBarChangeListener(SimpleSeekBarListener { value, fromUser ->
            valueView.text = formatMixValue(value)
            if (fromUser) {
                applySoundscapeMix(fromUser = true)
            }
        })
    }

    private fun formatMixValue(progress: Int): String = String.format(Locale.getDefault(), "%d%%", progress)

    private fun loadAssistPreferences(): AssistPreferences {
        val prefs = requireContext().applicationContext.getSharedPreferences("profile_settings", Context.MODE_PRIVATE)
        return AssistPreferences(
            hapticsEnabled = prefs.getBoolean("haptics_enabled", false),
            hapticMode = HapticPatternMode.fromStorageValue(
                prefs.getString("preferred_haptic_mode", HapticPatternMode.BREATH.name)
            ),
            adaptiveEnabled = prefs.getBoolean("adaptive_soundscape_enabled", true)
        )
    }

    private fun formatPlaybackTime(durationMs: Int): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    private fun applyPersonalizationVisualState(level: PersonalizationLevel) {
        val accentColor = when (level) {
            PersonalizationLevel.PREVIEW -> requireContext().getColor(R.color.status_warning)
            PersonalizationLevel.FULL -> requireContext().getColor(R.color.status_positive)
        }
        binding.tvInterventionSessionPersonalizationBadge.backgroundTintList =
            ColorStateList.valueOf(
                requireContext().getColor(R.color.md_theme_light_primaryContainer)
            )
        binding.tvInterventionSessionPersonalizationBadge.setTextColor(accentColor)
        binding.cardInterventionSessionPersonalization.strokeWidth = dpToPx(1)
        binding.cardInterventionSessionPersonalization.strokeColor = accentColor
    }

    companion object {
        private const val AUDIO_PROGRESS_MAX = 1000
        private const val AUDIO_PROGRESS_INTERVAL_MS = 250L
    }

    private data class AssistPreferences(
        val hapticsEnabled: Boolean,
        val hapticMode: HapticPatternMode,
        val adaptiveEnabled: Boolean
    )
}

private class SimpleSeekBarListener(
    private val onChanged: (Int, Boolean) -> Unit
) : android.widget.SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(
        seekBar: android.widget.SeekBar?,
        progress: Int,
        fromUser: Boolean
    ) {
        onChanged(progress, fromUser)
    }

    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit

    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
}
