package com.example.newstart.ui.relax

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.newstart.R
import com.example.newstart.databinding.FragmentRelaxHubBinding
import com.example.newstart.intervention.PersonalizationMissingInput
import com.example.newstart.ui.intervention.InterventionActionNavigator
import com.example.newstart.ui.intervention.InterventionActionUiModel
import com.example.newstart.ui.intervention.InterventionDashboardUiState
import com.example.newstart.ui.intervention.InterventionDashboardViewModel
import com.example.newstart.util.PerformanceTelemetry
import com.google.android.material.button.MaterialButton

class RelaxHubFragment : Fragment() {

    private var _binding: FragmentRelaxHubBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RelaxHubViewModel by viewModels()
    private val dashboardViewModel: InterventionDashboardViewModel by viewModels()
    private var latestDashboardState = InterventionDashboardUiState()
    private var activeShowcase: ZoneShowcaseSpec? = null

    companion object {
        private const val TAG = "RelaxHubFragment"
        private const val GLOBAL_AVATAR_MANIFEST_PATH = "3d/model_manifest_avatar_global.json"
        private const val HEAD_OPTION_ANIMATION = "Head Pain_8"
        private const val CHEST_OPTION_ANIMATION = "Chest Pain_9"
        private const val ABDOMINAL_OPTION_ANIMATION = "Abdominal Pain_10"
        private const val LIMB_OPTION_ANIMATION = "Limb Pain_11"
    }

    private data class ZoneShowcaseSpec(
        val zone: HumanBody3DView.BodyZone?,
        @DrawableRes val imageRes: Int?,
        val animationName: String?,
        val title: String,
        val hint: String
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRelaxHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureStageLayout()
        configureStageAvatar()
        applyShowcase(buildGuideShowcase(), animateAvatar = false)
        setupActions()
        observeData()
        setupBackPressed()
    }

    private fun setupActions() {
        binding.btnRelaxBackToHome.setOnClickListener {
            findNavController().navigate(R.id.navigation_home)
        }
        binding.btnInterventionTodayBaseline.setOnClickListener {
            findNavController().navigate(R.id.navigation_assessment_baseline)
        }
        binding.btnInterventionTodayProfile.setOnClickListener {
            findNavController().navigate(R.id.navigation_intervention_profile)
        }
        binding.btnInterventionTodayRegenerate.setOnClickListener {
            dashboardViewModel.generateTodayPrescription()
        }
        binding.btnInterventionTodayExecute.setOnClickListener {
            launchDashboardPrimaryAction()
        }
        binding.btnRelaxQuick.setOnClickListener {
            launchDashboardAction(0)
        }
        binding.btnRelaxBalanced.setOnClickListener {
            launchDashboardAction(1)
        }
        binding.btnRelaxSleep.setOnClickListener {
            launchDashboardAction(2)
        }
        binding.btnRelaxReview.setOnClickListener {
            findNavController().navigate(R.id.navigation_relax_review)
        }
        binding.btnZoneHead.setOnClickListener {
            selectZoneFromUi(HumanBody3DView.BodyZone.HEAD, HumanBody3DView.ZonePickSource.BUTTON)
        }
        binding.btnZoneChest.setOnClickListener {
            selectZoneFromUi(HumanBody3DView.BodyZone.CHEST, HumanBody3DView.ZonePickSource.BUTTON)
        }
        binding.btnZoneAbdomen.setOnClickListener {
            selectZoneFromUi(HumanBody3DView.BodyZone.ABDOMEN, HumanBody3DView.ZonePickSource.BUTTON)
        }
        binding.btnZoneLimb.setOnClickListener {
            selectZoneFromUi(HumanBody3DView.BodyZone.LEFT_LEG, HumanBody3DView.ZonePickSource.BUTTON)
        }
        binding.btnRelaxPlanExecute.setOnClickListener {
            viewModel.executeCurrentPlan()
        }
        binding.btnRelaxReportAnalyze.setOnClickListener {
            findNavController().navigate(R.id.navigation_medical_report_analyze)
        }
        binding.btnRelaxExportPerf.setOnClickListener {
            val file = PerformanceTelemetry.exportCsv(requireContext())
            Toast.makeText(
                requireContext(),
                getString(R.string.relax_export_perf_done, file.absolutePath),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun navigateToBreathingCoach(protocolType: String, durationSec: Int, taskId: String) {
        findNavController().navigate(
            R.id.navigation_breathing_coach,
            bundleOf(
                "protocolType" to protocolType,
                "durationSec" to durationSec,
                "taskId" to taskId
            )
        )
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

    private fun selectZoneFromUi(
        zone: HumanBody3DView.BodyZone,
        source: HumanBody3DView.ZonePickSource
    ) {
        Log.d(TAG, "selectZoneFromUi zone=$zone source=$source")
        applyShowcase(buildShowcaseForZone(zone), animateAvatar = true)
        viewModel.onBodyZoneSelected(zone, source)
    }

    private fun observeData() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.tvRelaxStressScore.text = state.stressIndex.toString()
            binding.progressRelaxStress.progress = state.stressIndex
            binding.tvRelaxSummary.text = state.summary
            binding.tvRelaxRecommendation.text = state.recommendation
            binding.tvRelaxMetricHr.text = state.heartRateText
            binding.tvRelaxMetricHrv.text = state.hrvText
            binding.tvRelaxMetricSpo2.text = state.spo2Text
            binding.tvRelaxMetricMotion.text = state.motionText
            binding.tvRelaxUpdated.text = state.updatedAtText
            binding.tvRelaxTodaySessions.text = state.todaySessions.toString()
            binding.tvRelaxTodayMinutes.text = state.todayMinutes.toString()
            binding.tvRelaxPlanTitle.text = if (state.lastPlanTitle.isBlank()) {
                getString(R.string.relax_zone_pick_hint)
            } else {
                getString(R.string.relax_plan_generated_prefix, state.lastPlanTitle)
            }
            binding.tvRelaxPlanReason.text = if (state.lastPlanReason.isBlank()) {
                ""
            } else {
                getString(R.string.relax_plan_reason_prefix, state.lastPlanReason)
            }
            binding.tvRelaxPlanRule.text = if (state.lastPlanRule.isBlank()) {
                ""
            } else {
                getString(R.string.relax_plan_rule_prefix, state.lastPlanRule)
            }
            binding.tvRelaxPlanFallback.visibility = if (state.lastPlanFallback) View.VISIBLE else View.GONE
            if (state.lastPlanFallback) {
                binding.tvRelaxPlanFallback.text = getString(R.string.relax_plan_fallback_badge)
            } else if (state.isAiEnhanced) {
                binding.tvRelaxPlanFallback.visibility = View.VISIBLE
                binding.tvRelaxPlanFallback.text = getString(R.string.relax_plan_ai_enhanced_badge)
            }
            binding.btnRelaxPlanExecute.isEnabled = state.lastPlanTitle.isNotBlank()

            when (state.planGenerationState) {
                PlanGenerationState.ENHANCING -> {
                    binding.tvRelaxPlanReason.text = getString(R.string.relax_plan_reason_ai_enhancing)
                }

                PlanGenerationState.FAILED -> {
                    if (binding.tvRelaxPlanReason.text.isNullOrBlank()) {
                        binding.tvRelaxPlanReason.text = getString(R.string.relax_plan_generation_failed)
                    }
                }

                else -> Unit
            }

            val levelStyle = when (state.level) {
                RelaxStressLevel.HIGH -> RelaxLevelStyle(
                    levelText = getString(R.string.relax_level_high),
                    textColor = color(R.color.status_negative),
                    chipColor = color(R.color.status_negative),
                    progressColor = color(R.color.status_negative)
                )

                RelaxStressLevel.MEDIUM -> RelaxLevelStyle(
                    levelText = getString(R.string.relax_level_medium),
                    textColor = color(R.color.status_warning),
                    chipColor = color(R.color.status_warning),
                    progressColor = color(R.color.status_warning)
                )

                RelaxStressLevel.LOW -> RelaxLevelStyle(
                    levelText = getString(R.string.relax_level_low),
                    textColor = color(R.color.status_positive),
                    chipColor = color(R.color.status_positive),
                    progressColor = color(R.color.status_positive)
                )

                RelaxStressLevel.UNKNOWN -> RelaxLevelStyle(
                    levelText = getString(R.string.relax_level_unknown),
                    textColor = color(R.color.text_secondary),
                    chipColor = color(R.color.text_secondary),
                    progressColor = color(R.color.text_secondary)
                )
            }

            binding.tvRelaxLevelBadge.text = levelStyle.levelText
            binding.tvRelaxLevelBadge.backgroundTintList =
                ColorStateList.valueOf(withAlpha(levelStyle.chipColor, 0.22f))
            binding.tvRelaxLevelBadge.setTextColor(levelStyle.textColor)
            binding.progressRelaxStress.setIndicatorColor(levelStyle.progressColor)

            syncShowcaseWithState(state)
        }

        viewModel.launchCommand.observe(viewLifecycleOwner) { command ->
            command ?: return@observe
            navigateToBreathingCoach(
                protocolType = command.protocolType,
                durationSec = command.durationSec,
                taskId = command.taskId
            )
            viewModel.consumeLaunchCommand()
        }

        viewModel.toastEvent.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.consumeToast()
            }
        }

        dashboardViewModel.uiState.observe(viewLifecycleOwner) { state ->
            latestDashboardState = state
            binding.tvInterventionTodayTitle.text = state.todayTitle.ifBlank {
                getString(R.string.intervention_today_placeholder)
            }
            binding.tvInterventionTodayReason.text = state.rationale.ifBlank {
                getString(R.string.intervention_today_reason_placeholder)
            }
            binding.tvInterventionTodayGoal.text = state.goalLine
            binding.tvInterventionTodayRisk.text = state.riskLine
            binding.tvInterventionTodayEvidence.text = state.evidenceLine
            binding.tvInterventionTodayBaselineState.text = listOf(
                state.baselineSummary,
                state.missingInputSummary.takeIf { state.isPreview }
            ).filterNotNull().joinToString("\n")
            binding.btnInterventionTodayRegenerate.isEnabled = state.canGenerate
            binding.btnInterventionTodayExecute.isEnabled = state.quickActions.isNotEmpty()

            binding.tvRelaxRecommendation.text = if (state.quickActions.isNotEmpty()) {
                state.rationale
            } else if (!state.isPreview) {
                getString(R.string.intervention_today_reason_placeholder)
            } else {
                getString(R.string.intervention_today_preview_placeholder, state.missingInputSummary)
            }

            binding.btnInterventionTodayBaseline.text = primaryMissingInputActionLabel(state)

            bindQuickButton(
                button = binding.btnRelaxQuick,
                action = state.quickActions.getOrNull(0),
                fallbackText = if (!state.isPreview) {
                    getString(R.string.intervention_today_execute)
                } else {
                    binding.btnInterventionTodayBaseline.text.toString()
                }
            )
            bindQuickButton(
                button = binding.btnRelaxBalanced,
                action = state.quickActions.getOrNull(1),
                fallbackText = if (!state.isPreview) {
                    getString(R.string.intervention_today_profile)
                } else {
                    getString(R.string.intervention_quick_profile)
                }
            )
            bindQuickButton(
                button = binding.btnRelaxSleep,
                action = state.quickActions.getOrNull(2),
                fallbackText = getString(R.string.intervention_today_regenerate)
            )
        }

        dashboardViewModel.toastEvent.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                dashboardViewModel.consumeToast()
            }
        }
    }

    private fun configureStageLayout() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        binding.layoutStagePanels.orientation =
            if (isLandscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        binding.container3dStage.updateLayoutParams<ViewGroup.LayoutParams> {
            height = dp(if (isLandscape) 332 else 412)
        }
        binding.cardStageImagePanel.updateLayoutParams<LinearLayout.LayoutParams> {
            if (isLandscape) {
                width = 0
                height = LinearLayout.LayoutParams.MATCH_PARENT
                weight = 1.18f
                marginEnd = dp(10)
                topMargin = 0
            } else {
                width = LinearLayout.LayoutParams.MATCH_PARENT
                height = 0
                weight = 1.08f
                marginEnd = 0
                topMargin = 0
            }
        }
        binding.cardStageAvatarPanel.updateLayoutParams<LinearLayout.LayoutParams> {
            if (isLandscape) {
                width = 0
                height = LinearLayout.LayoutParams.MATCH_PARENT
                weight = 0.82f
                marginStart = 0
                topMargin = 0
            } else {
                width = LinearLayout.LayoutParams.MATCH_PARENT
                height = 0
                weight = 0.92f
                marginStart = 0
                topMargin = dp(10)
            }
        }
    }

    private fun configureStageAvatar() {
        binding.avatarStageView.setStageModeTag("global_avatar")
        binding.avatarStageView.setManifestAssetPath(GLOBAL_AVATAR_MANIFEST_PATH)
        binding.avatarStageView.setAutoFocusOnPick(false)
        binding.avatarStageView.setPickEnabled(false)
        binding.avatarStageView.setRendererEnabled(true)
        binding.tvStageModeBadge.visibility = View.VISIBLE
        binding.cardStageFocusInfo.visibility = View.VISIBLE
        binding.avatarStageView.post {
            if (_binding == null) return@post
            binding.avatarStageView.playRole("IDLE")
        }
    }

    private fun buildGuideShowcase(): ZoneShowcaseSpec {
        return ZoneShowcaseSpec(
            zone = null,
            imageRes = null,
            animationName = null,
            title = getString(R.string.relax_legacy_stage_default_title),
            hint = getString(R.string.relax_legacy_stage_default_hint)
        )
    }

    private fun buildShowcaseForZone(zone: HumanBody3DView.BodyZone): ZoneShowcaseSpec {
        return when (canonicalShowcaseZone(zone)) {
            HumanBody3DView.BodyZone.HEAD -> ZoneShowcaseSpec(
                zone = HumanBody3DView.BodyZone.HEAD,
                imageRes = R.drawable.relax_stage_head_scene,
                animationName = HEAD_OPTION_ANIMATION,
                title = getString(R.string.relax_legacy_stage_head_title),
                hint = getString(R.string.relax_legacy_stage_head_hint)
            )

            HumanBody3DView.BodyZone.CHEST -> ZoneShowcaseSpec(
                zone = HumanBody3DView.BodyZone.CHEST,
                imageRes = R.drawable.relax_stage_chest_scene,
                animationName = CHEST_OPTION_ANIMATION,
                title = getString(R.string.relax_legacy_stage_chest_title),
                hint = getString(R.string.relax_legacy_stage_chest_hint)
            )

            HumanBody3DView.BodyZone.ABDOMEN -> ZoneShowcaseSpec(
                zone = HumanBody3DView.BodyZone.ABDOMEN,
                imageRes = R.drawable.relax_stage_abdomen_scene,
                animationName = ABDOMINAL_OPTION_ANIMATION,
                title = getString(R.string.relax_legacy_stage_abdomen_title),
                hint = getString(R.string.relax_legacy_stage_abdomen_hint)
            )

            else -> ZoneShowcaseSpec(
                zone = HumanBody3DView.BodyZone.LEFT_LEG,
                imageRes = R.drawable.relax_stage_limb_scene,
                animationName = LIMB_OPTION_ANIMATION,
                title = getString(R.string.relax_legacy_stage_limb_title),
                hint = getString(R.string.relax_legacy_stage_limb_hint)
            )
        }
    }

    private fun canonicalShowcaseZone(zone: HumanBody3DView.BodyZone): HumanBody3DView.BodyZone {
        return when (zone) {
            HumanBody3DView.BodyZone.HEAD,
            HumanBody3DView.BodyZone.NECK -> HumanBody3DView.BodyZone.HEAD

            HumanBody3DView.BodyZone.CHEST,
            HumanBody3DView.BodyZone.UPPER_BACK -> HumanBody3DView.BodyZone.CHEST

            HumanBody3DView.BodyZone.ABDOMEN,
            HumanBody3DView.BodyZone.LOWER_BACK -> HumanBody3DView.BodyZone.ABDOMEN

            HumanBody3DView.BodyZone.LEFT_ARM,
            HumanBody3DView.BodyZone.RIGHT_ARM,
            HumanBody3DView.BodyZone.LEFT_LEG,
            HumanBody3DView.BodyZone.RIGHT_LEG -> HumanBody3DView.BodyZone.LEFT_LEG
        }
    }

    private fun buildShowcaseForBodyZone(bodyZone: String): ZoneShowcaseSpec? {
        val zone = runCatching { HumanBody3DView.BodyZone.valueOf(bodyZone) }.getOrNull() ?: return null
        return buildShowcaseForZone(zone)
    }

    private fun syncShowcaseWithState(state: RelaxHubUiState) {
        val desiredShowcase = when {
            state.selectedBodyZone.isBlank() -> activeShowcase?.takeIf { it.zone != null } ?: buildGuideShowcase()
            else -> buildShowcaseForBodyZone(state.selectedBodyZone) ?: activeShowcase ?: buildGuideShowcase()
        }
        val shouldReplace =
            activeShowcase?.zone != desiredShowcase.zone || activeShowcase?.imageRes != desiredShowcase.imageRes
        if (shouldReplace) {
            applyShowcase(desiredShowcase, animateAvatar = false)
        }
        renderShowcase(
            showcase = desiredShowcase,
            hintOverride = state.lastPlanReason.takeIf {
                state.selectedBodyZone.isNotBlank() && it.isNotBlank()
            }
        )
    }

    private fun applyShowcase(showcase: ZoneShowcaseSpec, animateAvatar: Boolean) {
        activeShowcase = showcase
        renderShowcase(showcase)
        if (animateAvatar && !showcase.animationName.isNullOrBlank()) {
            playStageAnimation(showcase.animationName)
        } else {
            playStageIdle()
        }
    }

    private fun renderShowcase(showcase: ZoneShowcaseSpec, hintOverride: String? = null) {
        binding.tv3dStageTitle.text = showcase.title
        binding.tvStageFocusZone.text = showcase.title
        binding.tvStageFocusHint.text = hintOverride ?: showcase.hint
        binding.tvStageModeBadge.text = getString(R.string.relax_legacy_stage_badge)

        if (showcase.imageRes != null) {
            binding.imgStageCloseup.setImageResource(showcase.imageRes)
            binding.imgStageCloseup.visibility = View.VISIBLE
            binding.layoutStageGuideOverlay.visibility = View.GONE
        } else {
            binding.imgStageCloseup.setImageDrawable(null)
            binding.imgStageCloseup.visibility = View.GONE
            binding.layoutStageGuideOverlay.visibility = View.VISIBLE
        }
        updateZoneButtonStates(showcase.zone)
    }

    private fun playStageIdle() {
        binding.avatarStageView.post {
            if (_binding == null) return@post
            binding.avatarStageView.playRole("IDLE")
        }
    }

    private fun playStageAnimation(animationName: String) {
        binding.avatarStageView.post {
            if (_binding == null) return@post
            binding.avatarStageView.playAnimationByName(animationName, loop = false)
        }
    }

    private fun updateZoneButtonStates(activeZone: HumanBody3DView.BodyZone?) {
        val canonicalZone = activeZone?.let(::canonicalShowcaseZone)
        setZoneButtonState(binding.btnZoneHead, canonicalZone == HumanBody3DView.BodyZone.HEAD)
        setZoneButtonState(binding.btnZoneChest, canonicalZone == HumanBody3DView.BodyZone.CHEST)
        setZoneButtonState(binding.btnZoneAbdomen, canonicalZone == HumanBody3DView.BodyZone.ABDOMEN)
        setZoneButtonState(binding.btnZoneLimb, canonicalZone == HumanBody3DView.BodyZone.LEFT_LEG)
    }

    private fun setZoneButtonState(button: MaterialButton, active: Boolean) {
        val backgroundColor = if (active) {
            color(R.color.md_theme_light_primaryContainer)
        } else {
            color(R.color.surface_card_subtle)
        }
        val strokeColor = if (active) {
            color(R.color.md_theme_light_primary)
        } else {
            color(R.color.card_stroke)
        }
        val textColor = if (active) {
            color(R.color.md_theme_light_onPrimaryContainer)
        } else {
            color(R.color.text_secondary)
        }
        button.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        button.strokeColor = ColorStateList.valueOf(strokeColor)
        button.strokeWidth = dp(1)
        button.setTextColor(textColor)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return android.graphics.Color.argb(
            a,
            android.graphics.Color.red(color),
            android.graphics.Color.green(color),
            android.graphics.Color.blue(color)
        )
    }

    private data class RelaxLevelStyle(
        val levelText: String,
        val textColor: Int,
        val chipColor: Int,
        val progressColor: Int
    )

    private fun bindQuickButton(
        button: MaterialButton,
        action: InterventionActionUiModel?,
        fallbackText: String
    ) {
        button.text = action?.title ?: fallbackText
        button.isEnabled = action != null || !latestDashboardState.isLoading
    }

    private fun launchDashboardPrimaryAction() {
        val action = latestDashboardState.quickActions.firstOrNull()
        if (action != null) {
            navigateToInterventionSession(action)
            return
        }
        routeToNextRequirement()
    }

    private fun launchDashboardAction(index: Int) {
        val action = latestDashboardState.quickActions.getOrNull(index)
        if (action != null) {
            navigateToInterventionSession(action)
            return
        }
        when {
            index == 1 -> findNavController().navigate(R.id.navigation_intervention_profile)
            latestDashboardState.isPreview -> routeToNextRequirement()
            else -> dashboardViewModel.generateTodayPrescription()
        }
    }

    private fun routeToNextRequirement() {
        when (resolvePrimaryMissingInput(latestDashboardState)) {
            PersonalizationMissingInput.BASELINE_ASSESSMENT ->
                findNavController().navigate(R.id.navigation_assessment_baseline)

            PersonalizationMissingInput.DOCTOR_INQUIRY ->
                findNavController().navigate(R.id.navigation_doctor)

            PersonalizationMissingInput.DEVICE_DATA ->
                findNavController().navigate(R.id.navigation_device)

            null -> dashboardViewModel.generateTodayPrescription()
        }
    }

    private fun primaryMissingInputActionLabel(state: InterventionDashboardUiState): String {
        return when (resolvePrimaryMissingInput(state)) {
            PersonalizationMissingInput.BASELINE_ASSESSMENT ->
                getString(R.string.intervention_today_baseline)

            PersonalizationMissingInput.DOCTOR_INQUIRY ->
                getString(R.string.intervention_today_doctor)

            PersonalizationMissingInput.DEVICE_DATA ->
                getString(R.string.intervention_today_device)

            null -> getString(R.string.intervention_today_baseline)
        }
    }

    private fun resolvePrimaryMissingInput(
        state: InterventionDashboardUiState
    ): PersonalizationMissingInput? {
        return when {
            state.missingInputs.contains(PersonalizationMissingInput.BASELINE_ASSESSMENT) ->
                PersonalizationMissingInput.BASELINE_ASSESSMENT

            state.missingInputs.contains(PersonalizationMissingInput.DOCTOR_INQUIRY) ->
                PersonalizationMissingInput.DOCTOR_INQUIRY

            state.missingInputs.contains(PersonalizationMissingInput.DEVICE_DATA) ->
                PersonalizationMissingInput.DEVICE_DATA

            else -> null
        }
    }

    private fun navigateToInterventionSession(action: InterventionActionUiModel) {
        if (!InterventionActionNavigator.navigate(this, action, "relaxPrescription")) {
            Toast.makeText(requireContext(), action.subtitle, Toast.LENGTH_LONG).show()
        }
    }

    private fun color(@ColorRes colorRes: Int): Int = ContextCompat.getColor(requireContext(), colorRes)

    override fun onResume() {
        super.onResume()
        dashboardViewModel.refreshDashboard()
    }

    override fun onDestroyView() {
        binding.avatarStageView.setRendererEnabled(false)
        super.onDestroyView()
        activeShowcase = null
        _binding = null
    }
}
