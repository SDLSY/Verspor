package com.example.newstart.ui.intervention

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.newstart.R
import com.example.newstart.databinding.FragmentAssessmentBaselineBinding
import com.google.android.material.snackbar.Snackbar

class AssessmentBaselineFragment : Fragment() {

    private var _binding: FragmentAssessmentBaselineBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AssessmentBaselineViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAssessmentBaselineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActions()
        observeData()
    }

    private fun setupActions() {
        binding.btnAssessmentBack.setOnClickListener {
            if (!findNavController().popBackStack()) {
                findNavController().navigate(R.id.navigation_relax_hub)
            }
        }
        binding.btnAssessmentPrevious.setOnClickListener {
            viewModel.previous()
        }
        binding.btnAssessmentNext.setOnClickListener {
            viewModel.next()
        }
    }

    private fun observeData() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.progressAssessment.text = state.progressText
            binding.tvAssessmentScaleTitle.text = state.scaleTitle
            binding.tvAssessmentScaleDesc.text = state.scaleDescription
            binding.tvAssessmentQuestionProgress.text = state.questionProgressText
            binding.tvAssessmentQuestion.text = state.questionPrompt
            binding.btnAssessmentPrevious.isEnabled = state.canGoBack
            binding.btnAssessmentNext.text = if (state.isLastStep) {
                getString(R.string.assessment_baseline_finish)
            } else {
                getString(R.string.assessment_baseline_next)
            }
            bindOptions(state)
        }
        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                is AssessmentBaselineEvent.Toast -> {
                    Snackbar.make(binding.root, event.message, Snackbar.LENGTH_SHORT).show()
                    viewModel.consumeEvent()
                }
                AssessmentBaselineEvent.Completed -> {
                    viewModel.consumeEvent()
                    findNavController().navigate(R.id.navigation_intervention_profile)
                }
                null -> Unit
            }
        }
    }

    private fun bindOptions(state: AssessmentBaselineUiState) {
        binding.groupAssessmentOptions.setOnCheckedChangeListener(null)
        binding.groupAssessmentOptions.removeAllViews()
        state.options.forEach { option ->
            val button = RadioButton(requireContext()).apply {
                id = View.generateViewId()
                text = option.label
                tag = option.value
                isChecked = option.value == state.selectedValue
                textSize = 15f
            }
            binding.groupAssessmentOptions.addView(button)
        }
        binding.groupAssessmentOptions.setOnCheckedChangeListener { group, checkedId ->
            val selected = group.findViewById<View>(checkedId)?.tag as? Int ?: return@setOnCheckedChangeListener
            viewModel.selectAnswer(selected)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
