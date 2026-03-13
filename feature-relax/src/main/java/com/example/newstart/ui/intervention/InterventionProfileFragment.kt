package com.example.newstart.ui.intervention

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.newstart.core.common.R
import com.example.newstart.feature.relax.databinding.FragmentInterventionProfileBinding
import com.google.android.material.snackbar.Snackbar

class InterventionProfileFragment : Fragment() {

    private var _binding: FragmentInterventionProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InterventionProfileViewModel by viewModels()
    private var latestPrimaryAction: InterventionActionUiModel? = null
    private var latestState = InterventionProfileUiState()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInterventionProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActions()
        observeData()
    }

    private fun setupActions() {
        binding.btnInterventionProfileBack.setOnClickListener {
            if (!findNavController().popBackStack()) {
                findNavController().navigate(R.id.navigation_relax_hub)
            }
        }
        binding.btnInterventionProfileRefresh.setOnClickListener {
            viewModel.refreshProfile()
        }
        binding.btnInterventionProfileGenerate.setOnClickListener {
            viewModel.generateTodayBundle()
        }
        binding.btnInterventionProfileStart.setOnClickListener {
            val action = latestPrimaryAction
            when {
                action != null -> {
                    if (!InterventionActionNavigator.navigate(this, action, "profilePrescription")) {
                        viewModel.generateTodayBundle()
                    }
                }
                latestState.missingInputs.contains("BASELINE_ASSESSMENT") -> {
                    findNavController().navigate(R.id.navigation_assessment_baseline)
                }
                latestState.missingInputs.contains("DOCTOR_INQUIRY") -> {
                    findNavController().navigate(R.id.navigation_doctor)
                }
                latestState.missingInputs.contains("DEVICE_DATA") -> {
                    findNavController().navigate(R.id.navigation_device)
                }
                else -> {
                    viewModel.generateTodayBundle()
                }
            }
        }
    }

    private fun observeData() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            latestState = state
            latestPrimaryAction = state.primaryAction
            binding.tvInterventionProfileBaseline.text = listOf(state.baselineText, state.missingInputText)
                .filter { it.isNotBlank() }
                .joinToString("\n")
            binding.tvInterventionProfileScores.text = state.scoreLines.joinToString("\n").ifBlank {
                getString(R.string.intervention_profile_scores_empty)
            }
            binding.tvInterventionProfileScales.text = state.scaleLines.joinToString("\n").ifBlank {
                getString(R.string.intervention_profile_scales_empty)
            }
            binding.tvInterventionProfileDoctor.text = state.doctorSummary
            binding.tvInterventionProfileMedical.text = state.medicalSummary
            binding.tvInterventionProfileAdherence.text = state.adherenceHint
            binding.tvInterventionProfileEvidence.text = state.evidenceLines.joinToString("\n").ifBlank {
                getString(R.string.intervention_profile_evidence_empty)
            }
            binding.tvInterventionProfileBundleTitle.text = state.bundleTitle
            binding.tvInterventionProfileBundleSummary.text = state.bundleSummary
            binding.btnInterventionProfileGenerate.isEnabled = state.canGenerate
            binding.btnInterventionProfileStart.isEnabled = !state.isLoading
            binding.btnInterventionProfileStart.text = when {
                state.primaryAction != null -> state.primaryAction.title
                state.missingInputs.contains("BASELINE_ASSESSMENT") -> getString(R.string.intervention_today_baseline)
                state.missingInputs.contains("DOCTOR_INQUIRY") -> getString(R.string.intervention_today_doctor)
                state.missingInputs.contains("DEVICE_DATA") -> getString(R.string.intervention_today_device)
                else -> getString(R.string.intervention_profile_no_primary)
            }
        }
        viewModel.toastEvent.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                viewModel.consumeToast()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

