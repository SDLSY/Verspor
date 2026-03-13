package com.example.newstart.ui.intervention

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.newstart.R
import com.example.newstart.databinding.BottomSheetPrescriptionNoteBinding
import com.example.newstart.intervention.InterventionProtocolCatalog
import com.example.newstart.intervention.PrescriptionItemType
import com.example.newstart.intervention.PrescriptionTimingSlot
import com.example.newstart.repository.NetworkRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class PrescriptionNoteBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPrescriptionNoteBinding? = null
    private val binding get() = _binding!!
    private val networkRepository = NetworkRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPrescriptionNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val protocolCode = requireArguments().getString(ARG_PROTOCOL_CODE).orEmpty()
        val title = requireArguments().getString(ARG_TITLE).orEmpty()
        val rationale = requireArguments().getString(ARG_RATIONALE).orEmpty()
        val durationSec = requireArguments().getInt(ARG_DURATION_SEC)
        val itemType = runCatching {
            PrescriptionItemType.valueOf(requireArguments().getString(ARG_ITEM_TYPE).orEmpty())
        }.getOrDefault(PrescriptionItemType.LIFESTYLE)
        val definition = InterventionProtocolCatalog.find(protocolCode)

        binding.tvPrescriptionSheetTitle.text = title.ifBlank {
            definition?.displayName ?: getString(R.string.intervention_session_title_fallback)
        }
        binding.tvPrescriptionSheetType.text = getString(
            R.string.morning_prescription_sheet_type_format,
            when (itemType) {
                PrescriptionItemType.PRIMARY -> getString(R.string.intervention_dashboard_action_primary)
                PrescriptionItemType.SECONDARY -> getString(R.string.intervention_dashboard_action_secondary)
                PrescriptionItemType.LIFESTYLE -> getString(R.string.intervention_dashboard_action_lifestyle)
            }
        )
        binding.tvPrescriptionSheetReason.text = rationale.ifBlank {
            getString(R.string.morning_prescription_sheet_reason_empty)
        }
        binding.tvPrescriptionSheetDesc.text = definition?.description
            ?: getString(R.string.intervention_session_default_desc)
        binding.tvPrescriptionSheetTiming.text = getString(
            R.string.morning_prescription_sheet_timing_format,
            timingLabel(definition?.defaultTimingSlot ?: PrescriptionTimingSlot.FLEXIBLE)
        )
        binding.tvPrescriptionSheetDuration.text = getString(
            R.string.morning_prescription_sheet_duration_format,
            formatDuration(durationSec.takeIf { it > 0 } ?: definition?.defaultDurationSec ?: 0)
        )
        binding.tvPrescriptionSheetSteps.text = definition?.steps
            ?.mapIndexed { index, step -> "${index + 1}. $step" }
            ?.joinToString("\n")
            .orEmpty()
            .ifBlank { getString(R.string.intervention_session_steps_empty) }
        binding.tvPrescriptionSheetNotice.text = if (protocolCode == DOCTOR_PRIORITY_CODE) {
            getString(R.string.morning_prescription_sheet_notice_doctor)
        } else {
            getString(R.string.morning_prescription_sheet_notice_default)
        }
        binding.btnPrescriptionSheetDone.setOnClickListener { dismiss() }
        loadRecommendationSupport()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun timingLabel(slot: PrescriptionTimingSlot): String {
        return when (slot) {
            PrescriptionTimingSlot.MORNING -> getString(R.string.intervention_session_timing_morning)
            PrescriptionTimingSlot.AFTERNOON -> getString(R.string.intervention_session_timing_afternoon)
            PrescriptionTimingSlot.EVENING -> getString(R.string.intervention_session_timing_evening)
            PrescriptionTimingSlot.BEFORE_SLEEP -> getString(R.string.intervention_session_timing_before_sleep)
            PrescriptionTimingSlot.FLEXIBLE -> getString(R.string.intervention_session_timing_flexible)
        }
    }

    private fun formatDuration(durationSec: Int): String {
        if (durationSec <= 0) {
            return getString(R.string.intervention_session_duration_seconds, 0)
        }
        val minutes = durationSec / 60
        return if (minutes > 0) {
            getString(R.string.intervention_session_duration_minutes, minutes)
        } else {
            getString(R.string.intervention_session_duration_seconds, durationSec)
        }
    }

    companion object {
        private const val ARG_PROTOCOL_CODE = "protocolCode"
        private const val ARG_TITLE = "title"
        private const val ARG_RATIONALE = "rationale"
        private const val ARG_DURATION_SEC = "durationSec"
        private const val ARG_ITEM_TYPE = "itemType"
        private const val DOCTOR_PRIORITY_CODE = "TASK_DOCTOR_PRIORITY"

        fun newInstance(action: InterventionActionUiModel): PrescriptionNoteBottomSheet {
            return PrescriptionNoteBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROTOCOL_CODE, action.protocolCode)
                    putString(ARG_TITLE, action.title)
                    putString(ARG_RATIONALE, action.subtitle)
                    putInt(ARG_DURATION_SEC, action.durationSec)
                    putString(ARG_ITEM_TYPE, action.itemType.name)
                }
            }
        }
    }

    private fun loadRecommendationSupport() {
        val hasCloudSession = networkRepository.getCurrentSession() != null
        if (!hasCloudSession) {
            bindSupportFallback(
                summary = getString(R.string.prescription_support_login_required),
                reasons = emptyList(),
                effects = getString(R.string.prescription_support_effects_login_required),
                meta = getString(R.string.prescription_support_meta_local)
            )
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val explanation = networkRepository
                .getRecommendationExplanations(traceType = "DAILY_PRESCRIPTION", limit = 1)
                .getOrNull()
                ?.items
                ?.firstOrNull()
            val effects = networkRepository
                .getRecommendationEffects(days = 30)
                .getOrNull()

            bindSupportFallback(
                summary = explanation?.summary?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.prescription_support_empty),
                reasons = explanation?.reasons?.take(3).orEmpty(),
                effects = if (effects == null || effects.totalExecutions <= 0) {
                    getString(R.string.prescription_support_effects_empty)
                } else {
                    getString(
                        R.string.prescription_support_effects_detail,
                        effects.attributedExecutions,
                        effects.totalExecutions,
                        effects.avgEffectScore,
                        effects.avgStressDrop
                    )
                },
                meta = listOfNotNull(
                    explanation?.modelProfile?.takeIf { it.isNotBlank() },
                    explanation?.configSource?.takeIf { it.isNotBlank() },
                    explanation?.recommendationMode?.takeIf { it.isNotBlank() }
                ).joinToString(" | ").ifBlank {
                    getString(R.string.prescription_support_meta_local)
                }
            )
        }
    }

    private fun bindSupportFallback(
        summary: String,
        reasons: List<String>,
        effects: String,
        meta: String
    ) {
        binding.groupPrescriptionSupport.isVisible = true
        binding.tvPrescriptionSupportSummary.text = summary
        binding.tvPrescriptionSupportMeta.text = meta
        binding.tvPrescriptionSupportReasons.text = if (reasons.isEmpty()) {
            getString(R.string.prescription_support_reasons_empty)
        } else {
            reasons.joinToString("\n") { "- $it" }
        }
        binding.tvPrescriptionSupportEffects.text = effects
    }
}

