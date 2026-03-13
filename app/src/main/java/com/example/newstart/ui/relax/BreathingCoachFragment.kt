package com.example.newstart.ui.relax

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.newstart.R
import com.example.newstart.databinding.FragmentBreathingCoachBinding
import java.util.Locale

class BreathingCoachFragment : Fragment() {

    private var _binding: FragmentBreathingCoachBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BreathingCoachViewModel by viewModels()
    private var latestState: BreathingCoachUiState = BreathingCoachUiState()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBreathingCoachBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.initialize(
            protocolTypeArg = arguments?.getString("protocolType"),
            durationSecArg = arguments?.getInt("durationSec"),
            taskIdArg = arguments?.getString("taskId")
        )

        setupActions()
        observeData()
    }

    private fun setupActions() {
        binding.btnBreathBack.setOnClickListener {
            if (!findNavController().popBackStack()) {
                findNavController().navigate(R.id.navigation_relax_hub)
            }
        }

        binding.chipProtocol46.setOnClickListener {
            viewModel.selectProtocol(BreathingProtocol.BREATH_4_6)
        }
        binding.chipProtocol478.setOnClickListener {
            viewModel.selectProtocol(BreathingProtocol.BREATH_4_7_8)
        }
        binding.chipProtocolBox.setOnClickListener {
            viewModel.selectProtocol(BreathingProtocol.BOX)
        }

        binding.chipDuration1m.setOnClickListener { viewModel.selectDuration(60) }
        binding.chipDuration3m.setOnClickListener { viewModel.selectDuration(180) }
        binding.chipDuration5m.setOnClickListener { viewModel.selectDuration(300) }

        binding.btnBreathPrimary.setOnClickListener {
            if (latestState.isRunning) {
                viewModel.stopSessionEarly()
            } else {
                viewModel.startSession()
            }
        }

        binding.btnBreathSecondary.setOnClickListener {
            viewModel.reset()
        }
    }

    private fun observeData() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            latestState = state

            binding.chipProtocol46.isChecked = state.selectedProtocol == BreathingProtocol.BREATH_4_6
            binding.chipProtocol478.isChecked = state.selectedProtocol == BreathingProtocol.BREATH_4_7_8
            binding.chipProtocolBox.isChecked = state.selectedProtocol == BreathingProtocol.BOX

            binding.chipDuration1m.isChecked = state.selectedDurationSec == 60
            binding.chipDuration3m.isChecked = state.selectedDurationSec == 180
            binding.chipDuration5m.isChecked = state.selectedDurationSec == 300

            val selectorEnabled = !state.isRunning
            binding.chipProtocol46.isEnabled = selectorEnabled
            binding.chipProtocol478.isEnabled = selectorEnabled
            binding.chipProtocolBox.isEnabled = selectorEnabled
            binding.chipDuration1m.isEnabled = selectorEnabled
            binding.chipDuration3m.isEnabled = selectorEnabled
            binding.chipDuration5m.isEnabled = selectorEnabled

            val phaseLabel = when (state.phase) {
                BreathingPhase.PREPARE -> getString(R.string.relax_phase_prepare)
                BreathingPhase.INHALE -> getString(R.string.relax_phase_inhale)
                BreathingPhase.HOLD -> getString(R.string.relax_phase_hold)
                BreathingPhase.EXHALE -> getString(R.string.relax_phase_exhale)
            }
            binding.tvBreathPhase.text = phaseLabel
            binding.tvBreathPhaseCountdown.text = getString(
                R.string.relax_phase_countdown,
                phaseLabel,
                state.phaseRemainingSec.coerceAtLeast(0)
            )

            val min = state.totalRemainingSec / 60
            val sec = state.totalRemainingSec % 60
            binding.tvBreathTotalCountdown.text = getString(R.string.relax_total_countdown, min, sec)
            binding.progressBreathPhase.progress = state.phaseProgress
            binding.tvBreathCycleHint.text = getString(R.string.relax_cycle_hint)

            binding.btnBreathPrimary.text = if (state.isRunning) {
                getString(R.string.relax_breath_stop)
            } else {
                getString(R.string.relax_breath_start)
            }
            binding.btnBreathSecondary.isEnabled = !state.isRunning

            val result = state.result
            binding.cardBreathResult.visibility = if (result != null) View.VISIBLE else View.GONE
            if (result != null) {
                val title = when {
                    result.completed && result.saved -> getString(R.string.relax_breath_result_title_done)
                    !result.completed && result.saved -> getString(R.string.relax_breath_result_title_partial)
                    else -> getString(R.string.relax_breath_result_title_discarded)
                }
                binding.tvBreathResultTitle.text = title
                binding.tvBreathResultDetail.text = getString(
                    R.string.relax_breath_result_detail,
                    result.preStress,
                    result.postStress
                )
                binding.tvBreathResultScore.text = getString(
                    R.string.relax_breath_result_score,
                    result.effectScore
                )
                val savedHint = if (result.saved) {
                    getString(R.string.relax_breath_result_saved)
                } else {
                    getString(R.string.relax_breath_result_unsaved)
                }
                binding.tvBreathResultSaved.text = String.format(
                    Locale.getDefault(),
                    "%s (%ds)",
                    savedHint,
                    result.elapsedSec
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
