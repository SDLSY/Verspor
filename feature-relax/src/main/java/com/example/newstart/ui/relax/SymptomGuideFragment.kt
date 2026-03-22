package com.example.newstart.ui.relax

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.newstart.core.common.R
import com.example.newstart.core.common.ui.cards.ActionGroupCardModel
import com.example.newstart.core.common.ui.cards.CardTone
import com.example.newstart.core.common.ui.cards.EvidenceCardModel
import com.example.newstart.core.common.ui.cards.MedicalCardRenderer
import com.example.newstart.core.common.ui.cards.RiskSummaryCardModel
import com.example.newstart.feature.relax.databinding.FragmentSymptomGuideBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip

class SymptomGuideFragment : Fragment() {

    private var _binding: FragmentSymptomGuideBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SymptomGuideViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSymptomGuideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBackPressed()
        setupResultListener()
        setupActions()
        observeViewModel()
    }

    private fun setupBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    findNavController().navigate(R.id.navigation_home)
                }
            }
        )
    }

    private fun setupResultListener() {
        childFragmentManager.setFragmentResultListener(
            SymptomDetailBottomSheet.RESULT_KEY,
            viewLifecycleOwner
        ) { _, result ->
            val zone = SymptomBodyZone.valueOf(result.getString("zone").orEmpty())
            val side = SurfaceSide.valueOf(result.getString("side").orEmpty())
            viewModel.upsertMarker(
                zone = zone,
                side = side,
                symptomLabel = result.getString("symptomLabel").orEmpty(),
                severity = result.getInt("severity"),
                durationLabel = result.getString("durationLabel").orEmpty(),
                note = result.getString("note").orEmpty()
            )
        }
    }

    private fun setupActions() {
        binding.btnSymptomBackToHome.setOnClickListener {
            findNavController().navigate(R.id.navigation_home)
        }
        binding.btnSymptomOpenRelaxCenter.setOnClickListener {
            findNavController().navigate(R.id.navigation_relax_center_legacy)
        }

        binding.btnSurfaceFront.setOnClickListener {
            viewModel.setSurfaceSide(SurfaceSide.FRONT)
        }
        binding.btnSurfaceBack.setOnClickListener {
            viewModel.setSurfaceSide(SurfaceSide.BACK)
        }

        binding.btnMarkerHead.setOnClickListener { openSymptomSheet(SymptomBodyZone.HEAD) }
        binding.btnMarkerChest.setOnClickListener { openSymptomSheet(SymptomBodyZone.CHEST) }
        binding.btnMarkerAbdomen.setOnClickListener { openSymptomSheet(SymptomBodyZone.ABDOMEN) }
        binding.btnMarkerLimb.setOnClickListener { openSymptomSheet(SymptomBodyZone.LIMB) }

        (quickChips() + redFlagChips()).forEach { chip ->
            chip.setOnClickListener {
                viewModel.addQuickSymptom(chip.text.toString())
            }
        }

        binding.chipGroupTriggers.setOnCheckedStateChangeListener { group, checkedIds ->
            val trigger = checkedIds.firstOrNull()?.let { id ->
                group.findViewById<Chip>(id)?.text?.toString().orEmpty()
            }.orEmpty()
            viewModel.setTrigger(trigger)
        }

        associatedChips().forEach { chip ->
            chip.setOnCheckedChangeListener { button, isChecked ->
                viewModel.toggleAssociatedSymptom(button.text.toString(), isChecked)
            }
        }

        binding.etSymptomAdditionalNote.doAfterTextChanged { text ->
            viewModel.setAdditionalNote(text?.toString().orEmpty())
        }

        binding.btnGenerateOutcome.setOnClickListener {
            viewModel.generateOutcome()
        }

        binding.btnOutcomeContinueDoctor.setOnClickListener {
            val prefill = viewModel.uiState.value?.outcome?.doctorPrefill.orEmpty()
            findNavController().currentBackStackEntry?.savedStateHandle?.set(
                DOCTOR_PREFILL_MESSAGE_KEY,
                prefill
            )
            findNavController().navigate(R.id.navigation_doctor)
        }

        binding.btnOutcomeReport.setOnClickListener {
            findNavController().navigate(R.id.navigation_medical_report_analyze)
        }

        binding.btnOutcomeSupport.setOnClickListener {
            viewModel.startSupportAction()
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            bindSurfaceSide(state.selectedSurfaceSide)
            bindHotspotState(state)
            bindQuickChipState(state.selectedMarkers)
            bindSelectedMarkers(state.selectedMarkers)
            bindDeviceEvidence(state.deviceEvidence)
            binding.btnGenerateOutcome.isEnabled = state.canGenerate
            binding.cardOutcome.isVisible = state.outcome != null
            state.outcome?.let { bindOutcome(it) }
        }

        viewModel.toastEvent.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.consumeToast()
            }
        }

        viewModel.launchCommand.observe(viewLifecycleOwner) { command ->
            if (command != null) {
                findNavController().navigate(
                    R.id.navigation_breathing_coach,
                    bundleOf(
                        "protocolType" to command.protocolType,
                        "durationSec" to command.durationSec,
                        "taskId" to command.taskId
                    )
                )
                viewModel.consumeLaunchCommand()
            }
        }
    }

    private fun bindSurfaceSide(side: SurfaceSide) {
        val isBack = side == SurfaceSide.BACK
        binding.imgBodyMap.setImageResource(
            if (isBack) R.drawable.symptom_guide_back else R.drawable.symptom_guide_front
        )
        binding.tvBodyMapHint.text = getString(
            if (isBack) R.string.symptom_body_hint_back else R.string.symptom_body_hint_front
        )
        bindSurfaceToggleButton(binding.btnSurfaceFront, selected = !isBack)
        bindSurfaceToggleButton(binding.btnSurfaceBack, selected = isBack)
    }

    private fun bindHotspotState(state: SymptomGuideUiState) {
        bindHotspot(binding.btnMarkerHead, SymptomBodyZone.HEAD, state)
        bindHotspot(binding.btnMarkerChest, SymptomBodyZone.CHEST, state)
        bindHotspot(binding.btnMarkerAbdomen, SymptomBodyZone.ABDOMEN, state)
        bindHotspot(binding.btnMarkerLimb, SymptomBodyZone.LIMB, state)
    }

    private fun bindHotspot(
        view: TextView,
        zone: SymptomBodyZone,
        state: SymptomGuideUiState
    ) {
        val currentSide = state.selectedSurfaceSide
        val count = state.selectedMarkers.count { it.zone == zone && it.surfaceSide == currentSide }
        val selected = count > 0
        view.text = ""
        view.alpha = 0f
        view.backgroundTintList = ColorStateList.valueOf(color(android.R.color.transparent))
        view.isSelected = selected
    }

    private fun bindQuickChipState(markers: List<SelectedBodyMarker>) {
        val selectedLabels = markers.map { it.symptomLabel }.toSet()
        quickChips().forEach { chip ->
            bindSelectableChip(
                chip = chip,
                selected = selectedLabels.contains(chip.text.toString()),
                accent = ChipAccent.PRIMARY
            )
        }
        redFlagChips().forEach { chip ->
            bindSelectableChip(
                chip = chip,
                selected = selectedLabels.contains(chip.text.toString()),
                accent = ChipAccent.DANGER
            )
        }
    }

    private fun bindSelectableChip(
        chip: Chip,
        selected: Boolean,
        accent: ChipAccent
    ) {
        val background = when {
            !selected -> color(R.color.chip_bg)
            accent == ChipAccent.DANGER -> color(R.color.status_negative)
            else -> color(R.color.md_theme_light_primaryContainer)
        }
        val textColor = when {
            !selected -> color(R.color.text_primary)
            accent == ChipAccent.DANGER -> color(android.R.color.white)
            else -> color(R.color.md_theme_light_onPrimaryContainer)
        }
        val strokeColor = when {
            !selected -> color(R.color.md_theme_light_outlineVariant)
            accent == ChipAccent.DANGER -> color(R.color.status_negative)
            else -> color(R.color.md_theme_light_primary)
        }
        chip.isChecked = selected
        chip.chipBackgroundColor = ColorStateList.valueOf(background)
        chip.chipStrokeColor = ColorStateList.valueOf(strokeColor)
        chip.chipStrokeWidth = if (selected) resources.displayMetrics.density * 1.25f else 0f
        chip.setTextColor(textColor)
        chip.alpha = if (selected) 1f else 0.96f
        chip.scaleX = if (selected) 1.03f else 1f
        chip.scaleY = if (selected) 1.03f else 1f
    }

    private fun bindSurfaceToggleButton(button: MaterialButton, selected: Boolean) {
        val backgroundColor = color(
            if (selected) R.color.md_theme_light_primary else R.color.md_theme_light_surfaceVariant
        )
        val textColor = color(
            if (selected) R.color.md_theme_light_onPrimary else R.color.md_theme_light_onSurfaceVariant
        )
        val strokeColor = color(
            if (selected) R.color.md_theme_light_primary else R.color.md_theme_light_outlineVariant
        )
        button.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        button.setTextColor(textColor)
        button.strokeColor = ColorStateList.valueOf(strokeColor)
        button.strokeWidth = if (selected) 0 else surfaceToggleStrokeWidth()
    }

    private fun surfaceToggleStrokeWidth(): Int {
        return (resources.displayMetrics.density * 1.5f).toInt().coerceAtLeast(1)
    }

    private fun bindSelectedMarkers(markers: List<SelectedBodyMarker>) {
        binding.chipGroupSelectedMarkers.removeAllViews()
        binding.tvSelectedMarkersEmpty.isVisible = markers.isEmpty()
        markers.forEach { marker ->
            val chip = Chip(requireContext()).apply {
                text = markerLabel(marker)
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    viewModel.removeMarker(marker.id)
                }
            }
            binding.chipGroupSelectedMarkers.addView(chip)
        }
    }

    private fun bindDeviceEvidence(deviceEvidence: String) {
        binding.tvDeviceEvidence.text = deviceEvidence
    }

    private fun bindOutcome(outcome: SymptomCheckOutcome) {
        binding.tvOutcomeRiskBadge.text = outcome.riskTitle
        binding.tvOutcomeRiskSummary.text = outcome.riskSummary
        binding.tvOutcomeEvidence.text = outcome.evidenceSummary
        binding.tvOutcomeDeviceEvidence.text = outcome.deviceEvidence
        binding.tvOutcomeDepartment.text = outcome.suggestedDepartment
        binding.tvOutcomeChecks.text = outcome.suggestedChecks
        binding.tvOutcomeNextSteps.text = outcome.nextSteps.mapIndexed { index, item ->
            "${index + 1}. $item"
        }.joinToString("\n")
        binding.tvOutcomeDisclaimer.text = outcome.disclaimer
        binding.tvOutcomeSupportHint.text = outcome.supportAction.reason
        binding.btnOutcomeSupport.text = outcome.supportAction.label
        binding.btnOutcomeSupport.isEnabled = outcome.supportAction.enabled

        val badgeColor = when (outcome.riskLevel) {
            SymptomRiskLevel.HIGH -> color(R.color.status_negative)
            SymptomRiskLevel.MEDIUM -> color(R.color.status_warning)
            SymptomRiskLevel.LOW -> color(R.color.status_positive)
        }
        binding.tvOutcomeRiskBadge.backgroundTintList = ColorStateList.valueOf(badgeColor)
        val tone = when (outcome.riskLevel) {
            SymptomRiskLevel.HIGH -> CardTone.NEGATIVE
            SymptomRiskLevel.MEDIUM -> CardTone.WARNING
            SymptomRiskLevel.LOW -> CardTone.POSITIVE
        }

        binding.containerSymptomOutcomeRiskCard?.let { container ->
            MedicalCardRenderer.renderRiskSummaryCard(
                container,
                RiskSummaryCardModel(
                    badgeText = outcome.riskTitle,
                    title = "风险分层与处理方向",
                    summary = outcome.riskSummary,
                    supportingText = outcome.disclaimer,
                    bullets = outcome.nextSteps,
                    tone = tone
                )
            )
        }

        binding.layoutSymptomOutcomeEvidenceCards?.let { container ->
            MedicalCardRenderer.renderEvidenceCards(
                container,
                listOf(
                    EvidenceCardModel(
                        title = "判断依据",
                        value = outcome.evidenceSummary.lineSequence().firstOrNull()?.toString().orEmpty(),
                        note = outcome.deviceEvidence,
                        badgeText = "已聚合",
                        tone = CardTone.INFO
                    ),
                    EvidenceCardModel(
                        title = "建议科室",
                        value = outcome.suggestedDepartment,
                        note = outcome.suggestedChecks,
                        badgeText = "下一步",
                        tone = tone
                    )
                )
            )
        }

        binding.layoutSymptomOutcomeActionCards?.let { container ->
            MedicalCardRenderer.renderActionGroupCards(
                container,
                listOf(
                    ActionGroupCardModel(
                        category = "继续问诊",
                        headline = "补全病史与伴随表现",
                        supportingText = outcome.doctorPrefill,
                        actionLabel = getString(R.string.symptom_action_doctor),
                        actionId = "doctor",
                        tone = CardTone.INFO
                    ),
                    ActionGroupCardModel(
                        category = "康复辅助",
                        headline = outcome.supportAction.label,
                        supportingText = outcome.supportAction.reason,
                        detailLines = listOf(
                            "协议：${outcome.supportAction.protocolType}",
                            "时长：${(outcome.supportAction.durationSec / 60).coerceAtLeast(1)} 分钟"
                        ),
                        actionLabel = outcome.supportAction.label,
                        actionId = "support",
                        enabled = outcome.supportAction.enabled,
                        tone = if (outcome.supportAction.enabled) CardTone.POSITIVE else CardTone.WARNING
                    )
                )
            ) { card ->
                when (card.actionId) {
                    "doctor" -> binding.btnOutcomeContinueDoctor.performClick()
                    "support" -> binding.btnOutcomeSupport.performClick()
                }
            }
        }

        bindSuspectedDirection(
            titleView = binding.tvSuspected1Title,
            reasonView = binding.tvSuspected1Reason,
            confidenceView = binding.tvSuspected1Confidence,
            item = outcome.suspectedDirections.getOrNull(0)
        )
        bindSuspectedDirection(
            titleView = binding.tvSuspected2Title,
            reasonView = binding.tvSuspected2Reason,
            confidenceView = binding.tvSuspected2Confidence,
            item = outcome.suspectedDirections.getOrNull(1)
        )
        bindSuspectedDirection(
            titleView = binding.tvSuspected3Title,
            reasonView = binding.tvSuspected3Reason,
            confidenceView = binding.tvSuspected3Confidence,
            item = outcome.suspectedDirections.getOrNull(2)
        )
    }

    private fun bindSuspectedDirection(
        titleView: TextView,
        reasonView: TextView,
        confidenceView: TextView,
        item: SymptomSuspectedDirection?
    ) {
        titleView.text = item?.title.orEmpty()
        reasonView.text = item?.reason.orEmpty()
        confidenceView.text = item?.confidenceLabel.orEmpty()
        val visible = item != null
        titleView.isVisible = visible
        reasonView.isVisible = visible
        confidenceView.isVisible = visible
    }

    private fun openSymptomSheet(zone: SymptomBodyZone) {
        val side = viewModel.uiState.value?.selectedSurfaceSide ?: SurfaceSide.FRONT
        SymptomDetailBottomSheet.newInstance(zone, side)
            .show(childFragmentManager, "symptomDetail")
    }

    private fun markerLabel(marker: SelectedBodyMarker): String {
        return "${surfaceLabel(marker.surfaceSide)}${zoneLabel(marker.zone)} · ${marker.symptomLabel}"
    }

    private fun surfaceLabel(side: SurfaceSide): String {
        return when (side) {
            SurfaceSide.FRONT -> getString(R.string.symptom_surface_front_short)
            SurfaceSide.BACK -> getString(R.string.symptom_surface_back_short)
        }
    }

    private fun zoneLabel(zone: SymptomBodyZone): String {
        return when (zone) {
            SymptomBodyZone.HEAD -> getString(R.string.symptom_zone_head)
            SymptomBodyZone.CHEST -> getString(R.string.symptom_zone_chest)
            SymptomBodyZone.ABDOMEN -> getString(R.string.symptom_zone_abdomen)
            SymptomBodyZone.LIMB -> getString(R.string.symptom_zone_limb)
        }
    }

    private fun quickChips(): List<Chip> {
        return listOf(
            binding.chipQuickHeadache,
            binding.chipQuickChestTightness,
            binding.chipQuickShortBreath,
            binding.chipQuickAbdominalPain,
            binding.chipQuickDizziness,
            binding.chipQuickMuscleSoreness,
            binding.chipQuickFatigue,
            binding.chipQuickFever
        )
    }

    private fun associatedChips(): List<Chip> {
        return listOf(
            binding.chipAssocFatigue,
            binding.chipAssocDizziness,
            binding.chipAssocNausea,
            binding.chipAssocCough,
            binding.chipAssocInsomnia,
            binding.chipAssocFever
        )
    }

    private fun redFlagChips(): List<Chip> {
        return listOf(
            binding.chipQuickChestPain,
            binding.chipQuickBreath,
            binding.chipQuickConsciousness,
            binding.chipQuickHighFever
        )
    }

    private fun color(colorRes: Int): Int = requireContext().getColor(colorRes)

    private enum class ChipAccent {
        PRIMARY,
        DANGER
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

