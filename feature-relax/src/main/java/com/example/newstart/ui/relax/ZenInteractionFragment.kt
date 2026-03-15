package com.example.newstart.ui.relax

import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.newstart.core.common.R
import com.example.newstart.feature.relax.databinding.FragmentZenInteractionBinding
import com.example.newstart.intervention.ZenInteractionMode
import com.google.android.material.snackbar.Snackbar

class ZenInteractionFragment : Fragment() {

    private var _binding: FragmentZenInteractionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ZenInteractionViewModel by viewModels()
    private var ambientPlayer: MediaPlayer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentZenInteractionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.initialize(
            protocolCode = arguments?.getString("protocolCode"),
            title = arguments?.getString("protocolTitle"),
            durationSec = arguments?.getInt("durationSec") ?: 300,
            rationale = arguments?.getString("rationale")
        )
        setupActions()
        observeData()
    }

    private fun setupActions() {
        binding.zenCanvas.setOnInteractionChangedListener { progress, touches ->
            viewModel.updateInteraction(progress = progress, touches = touches)
        }
        binding.btnZenBack.setOnClickListener {
            stopAmbientAudio()
            if (!findNavController().navigateUp()) {
                findNavController().navigate(R.id.navigation_intervention_center)
            }
        }
        binding.btnZenPrimary.setOnClickListener {
            binding.zenCanvas.resetInteraction()
            viewModel.updateInteraction(progress = 0f, touches = 0)
            startAmbientAudio()
            viewModel.startSession()
        }
        binding.btnZenSecondary.setOnClickListener {
            viewModel.updateInteraction(
                progress = binding.zenCanvas.getInteractionProgress(),
                touches = binding.zenCanvas.getTouchCount()
            )
            stopAmbientAudio()
            viewModel.completeSession()
        }
    }

    private fun observeData() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.tvZenTitle.text = state.title
            binding.tvZenSubtitle.text = state.subtitle
            binding.tvZenRationale.text = state.rationale
            binding.tvZenModeLabel.text = if (state.mode == ZenInteractionMode.MIST_ERASE) {
                getString(R.string.zen_interaction_mode_mist)
            } else {
                getString(R.string.zen_interaction_mode_wave)
            }
            binding.tvZenHint.text = if (state.mode == ZenInteractionMode.MIST_ERASE) {
                getString(R.string.zen_interaction_hint_mist)
            } else {
                getString(R.string.zen_interaction_hint_wave)
            }
            binding.zenCanvas.configure(state.mode)
            binding.zenCanvas.setInteractionEnabled(state.isRunning)
            binding.tvZenTimer.text = if (state.hasStarted) {
                getString(
                    R.string.zen_interaction_running,
                    state.remainingSec / 60,
                    state.remainingSec % 60
                )
            } else {
                getString(R.string.zen_interaction_ready)
            }
            binding.tvZenProgress.text = "${state.interactionProgress}% · ${state.touchCount} 次"
            binding.progressZenInteraction.progress = state.interactionProgress
            binding.btnZenPrimary.isEnabled = !state.hasStarted && !state.isCompleted
            binding.btnZenSecondary.isEnabled = state.hasStarted && !state.isCompleted
        }
        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                is ZenInteractionEvent.Toast -> {
                    Snackbar.make(binding.root, event.message, Snackbar.LENGTH_SHORT).show()
                    viewModel.consumeEvent()
                }
                ZenInteractionEvent.Completed -> {
                    stopAmbientAudio()
                    viewModel.consumeEvent()
                }
                null -> Unit
            }
        }
    }

    override fun onPause() {
        stopAmbientAudio()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.uiState.value?.isRunning == true) {
            startAmbientAudio()
        }
    }

    override fun onDestroyView() {
        binding.zenCanvas.setOnInteractionChangedListener(null)
        releaseAmbientAudio()
        _binding = null
        super.onDestroyView()
    }

    private fun startAmbientAudio() {
        val context = context ?: return
        val player = ambientPlayer ?: MediaPlayer.create(context, R.raw.sleep_wind_down_audio)?.apply {
            isLooping = true
            setVolume(0.24f, 0.24f)
            ambientPlayer = this
        } ?: return
        if (!player.isPlaying) {
            player.start()
        }
    }

    private fun stopAmbientAudio() {
        ambientPlayer?.takeIf { it.isPlaying }?.pause()
    }

    private fun releaseAmbientAudio() {
        ambientPlayer?.release()
        ambientPlayer = null
    }
}
