package com.example.newstart.ui.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.newstart.core.common.R
import com.example.newstart.core.common.ui.cards.ActionGroupCardModel
import com.example.newstart.core.common.ui.cards.CardTone
import com.example.newstart.core.common.ui.cards.EvidenceCardModel
import com.example.newstart.core.common.ui.cards.MedicalCardRenderer
import com.example.newstart.core.common.ui.cards.MiniTrendCardModel
import com.example.newstart.core.common.ui.cards.RiskSummaryCardModel
import com.example.newstart.data.HealthMetrics
import com.example.newstart.data.RecoveryLevel
import com.example.newstart.data.Trend
import com.example.newstart.feature.home.databinding.FragmentMorningReportBinding
import com.example.newstart.intervention.PersonalizationLevel
import com.example.newstart.intervention.PersonalizationMissingInput
import com.example.newstart.intervention.PrescriptionItemType
import com.example.newstart.ml.AnomalyLevel
import com.example.newstart.ui.intervention.InterventionActionNavigator
import com.example.newstart.ui.intervention.InterventionActionUiModel
import com.example.newstart.ui.widget.addScaleEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class MorningReportFragment : Fragment() {

    companion object {
        private const val AUTO_REFRESH_INTERVAL_MS = 2_000L
    }

    private var _binding: FragmentMorningReportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MorningReportViewModel by viewModels()
    private var latestPersonalizationState: MorningPersonalizationUiState? = null
    private var latestHealthMetrics: HealthMetrics? = null
    private var latestAiCredibilityState: AiCredibilityState? = null
    private var latestRecommendationInsightState: MorningRecommendationInsightUiState? = null
    private var latestAdviceList: List<InterventionActionUiModel> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMorningReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
        observeData()
        loadData()
        startAutoRefresh()
    }

    private fun setupUi() {
        binding.tvGreeting.text = getGreeting()
        binding.btnRefresh.addScaleEffect()
        binding.btnRefresh.setOnClickListener {
            loadData()
            viewModel.runCloudMinimalLoop()
        }
        binding.btnRelaxCenter.addScaleEffect()
        binding.btnRelaxCenter.setOnClickListener {
            findNavController().navigate(R.id.navigation_intervention_center)
        }
        binding.btnViewWeeklyReport.addScaleEffect()
        binding.btnViewWeeklyReport.setOnClickListener {
            findNavController().navigate(R.id.navigation_trend)
        }
        binding.btnPersonalizationAction.addScaleEffect()
        binding.btnPersonalizationAction.setOnClickListener {
            routeToNextRequirement()
        }
        binding.cardHeroRecovery.addScaleEffect()
        binding.cardHeroRecovery.setOnClickListener {
            findNavController().navigate(R.id.navigation_intervention_center)
        }
        binding.btnRegenerateAdvice.addScaleEffect()
        binding.btnRegenerateAdvice.setOnClickListener {
            viewModel.regenerateEdgeAdvice()
        }
    }

    private fun observeData() {
        viewModel.recoveryScore.observe(viewLifecycleOwner) { score ->
            score ?: return@observe
            binding.circularProgress.setProgress(score.score.toFloat(), animate = true)
            binding.tvRecoveryEmoji.text = when (score.level) {
                RecoveryLevel.EXCELLENT -> getString(R.string.morning_recovery_excellent)
                RecoveryLevel.GOOD -> getString(R.string.morning_recovery_good)
                RecoveryLevel.FAIR -> getString(R.string.morning_recovery_fair)
                RecoveryLevel.POOR -> getString(R.string.morning_recovery_poor)
            }
            binding.tvStatusDescription.text = score.getStatusDescription()
        }

        viewModel.sleepData.observe(viewLifecycleOwner) { sleep ->
            sleep ?: return@observe
            binding.tvTotalSleep.text = sleep.getFormattedDuration()
            binding.tvDeepSleep.text = formatMinutesToHours(sleep.deepSleepMinutes)
            binding.tvRemSleep.text = formatMinutesToHours(sleep.remSleepMinutes)
            binding.tvLightSleep.text = formatMinutesToHours(sleep.lightSleepMinutes)
            binding.sleepStagesProgress.setStagesFromMinutes(
                deepMinutes = sleep.deepSleepMinutes,
                remMinutes = sleep.remSleepMinutes,
                lightMinutes = sleep.lightSleepMinutes,
                awakeMinutes = sleep.awakeMinutes,
                animate = true
            )
        }

        viewModel.healthMetrics.observe(viewLifecycleOwner) { metrics ->
            metrics ?: return@observe
            latestHealthMetrics = metrics
            binding.tvHeartRate.text = getString(R.string.morning_heart_rate_format, metrics.heartRate.current)
            binding.tvHeartRateTrend.text = metrics.heartRate.trend.getSymbol()
            binding.tvHeartRateTrend.setTextColor(getTrendColor(metrics.heartRate.trend))

            binding.tvBloodOxygen.text = "${metrics.bloodOxygen.current}%"
            binding.tvOxygenStatus.text = getString(R.string.morning_oxygen_status_format, metrics.bloodOxygen.stability)

            binding.tvTemperature.text = getString(
                R.string.morning_temperature_format,
                metrics.temperature.current.toDouble()
            )
            binding.tvTempStatus.text = getString(
                R.string.morning_temperature_status_format,
                metrics.temperature.status
            )

            binding.tvHrv.text = "${metrics.hrv.current} ms"
            binding.tvHrvStatus.text = getString(
                R.string.morning_hrv_status_format,
                metrics.hrv.recoveryRate.toDouble()
            )
            bindRecommendationCards()
        }

        viewModel.adviceList.observe(viewLifecycleOwner) { adviceList ->
            latestAdviceList = adviceList
            displayAdviceList(adviceList)
        }

        viewModel.edgeAdviceStatus.observe(viewLifecycleOwner) { status ->
            binding.tvEdgeAdviceStatus.text = status
        }

        viewModel.interventionSummary.observe(viewLifecycleOwner) { summary ->
            bindInterventionSummary(summary)
        }

        viewModel.recoveryContributionSummary.observe(viewLifecycleOwner) { summary ->
            binding.tvRecoveryContribution.text = summary
        }

        viewModel.personalizationState.observe(viewLifecycleOwner) { state ->
            latestPersonalizationState = state
            bindPersonalizationState(state)
            bindRecommendationCards()
        }

        viewModel.aiCredibility.observe(viewLifecycleOwner) { state ->
            latestAiCredibilityState = state
            binding.tvAiSummary.text = state.summary
            binding.tvAiSource.text = state.sourceLabel
            binding.tvAiConfidenceValue.text = "${state.confidencePercent}%"
            binding.tvAiConfidenceLabel.text = state.confidenceLabel
            binding.progressAiConfidence.progress = state.confidencePercent
            binding.tvAiPrimaryFactor.text = state.primaryFactor
            binding.tvAiReason.text = state.reason
            binding.tvAiInference.text = state.inferenceHint

            val indicatorColor = when (state.indicatorLevel) {
                CredibilityLevel.HIGH -> color(R.color.status_positive)
                CredibilityLevel.MEDIUM -> color(R.color.status_warning)
                CredibilityLevel.LOW -> color(R.color.status_negative)
            }
            binding.progressAiConfidence.setIndicatorColor(indicatorColor)
            renderAiRiskState(state)
            bindRecommendationCards()
        }

        viewModel.recommendationInsight.observe(viewLifecycleOwner) { state ->
            latestRecommendationInsightState = state
            binding.tvRecommendationExplanationSummary.text = state.summary
            bindRecommendationCards()
        }

        viewModel.cloudLoopMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindPersonalizationState(state: MorningPersonalizationUiState) {
        binding.tvPersonalizationLevelBadge.text = state.label
        binding.tvPersonalizationSummary.text = state.summary
        binding.tvPersonalizationMissing.text = state.detail
        binding.layoutPersonalizationHintPanel.visibility = if (state.isPreview) View.VISIBLE else View.GONE
        binding.tvPersonalizationRequirementTitle.text = if (state.isPreview) {
            getString(R.string.morning_personalization_requirement_title)
        } else {
            getString(R.string.morning_personalization_requirement_title_ready)
        }
        binding.tvPersonalizationFooter.text = if (state.isPreview) {
            getString(R.string.morning_personalization_footer_default)
        } else {
            getString(R.string.morning_personalization_full_detail)
        }
        binding.btnPersonalizationAction.text = if (state.isPreview) {
            primaryMissingInputActionLabel(state)
        } else {
            getString(R.string.intervention_quick_profile)
        }

        val accentColor = when (state.level) {
            PersonalizationLevel.PREVIEW -> color(R.color.md_theme_light_secondary)
            PersonalizationLevel.FULL -> color(R.color.status_positive)
        }

        binding.cardPersonalizationStatus.strokeColor = withAlpha(accentColor, 0.32f)
        binding.cardPersonalizationStatus.strokeWidth = dp(1)
        binding.cardPersonalizationStatus.setCardBackgroundColor(color(R.color.surface_card))
        binding.viewPersonalizationAccent.backgroundTintList = ColorStateList.valueOf(accentColor)
        tintBadge(binding.tvPersonalizationLevelBadge, withAlpha(accentColor, 0.12f), accentColor)
        binding.tvPersonalizationRequirementTitle.setTextColor(accentColor)
        binding.layoutPersonalizationHintPanel.backgroundTintList =
            ColorStateList.valueOf(withAlpha(accentColor, 0.05f))
    }

    private fun routeToNextRequirement() {
        when (resolvePrimaryMissingInput(latestPersonalizationState)) {
            PersonalizationMissingInput.BASELINE_ASSESSMENT ->
                findNavController().navigate(R.id.navigation_assessment_baseline)

            PersonalizationMissingInput.DOCTOR_INQUIRY ->
                findNavController().navigate(R.id.navigation_doctor)

            PersonalizationMissingInput.DEVICE_DATA ->
                findNavController().navigate(R.id.navigation_device)

            null ->
                findNavController().navigate(R.id.navigation_intervention_profile)
        }
    }

    private fun primaryMissingInputActionLabel(state: MorningPersonalizationUiState): String {
        return when (resolvePrimaryMissingInput(state)) {
            PersonalizationMissingInput.BASELINE_ASSESSMENT ->
                getString(R.string.intervention_today_baseline)

            PersonalizationMissingInput.DOCTOR_INQUIRY ->
                getString(R.string.intervention_today_doctor)

            PersonalizationMissingInput.DEVICE_DATA ->
                getString(R.string.intervention_today_device)

            null ->
                getString(R.string.intervention_quick_profile)
        }
    }

    private fun resolvePrimaryMissingInput(
        state: MorningPersonalizationUiState?
    ): PersonalizationMissingInput? {
        state ?: return null
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

    private fun loadData() {
        viewModel.loadMorningReport()
    }

    private fun startAutoRefresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    delay(AUTO_REFRESH_INTERVAL_MS)
                    viewModel.refresh()
                }
            }
        }
    }

    private fun displayAdviceList(adviceList: List<InterventionActionUiModel>) {
        val cards = adviceList.map { advice ->
            ActionGroupCardModel(
                category = adviceCategory(advice.itemType),
                headline = advice.title,
                supportingText = advice.subtitle,
                detailLines = listOf(
                    "时长：${(advice.durationSec / 60).coerceAtLeast(1)} 分钟",
                    "协议：${advice.protocolCode}"
                ),
                actionLabel = getString(R.string.home_primary_action_intervention),
                actionId = "${advice.protocolCode}:${advice.title}",
                tone = adviceTone(advice.itemType)
            )
        }
        MedicalCardRenderer.renderActionGroupCards(binding.layoutAdviceList, cards) { model ->
            latestAdviceList.firstOrNull {
                model.actionId == "${it.protocolCode}:${it.title}"
            }?.let(::onAdviceClicked)
        }
    }

    private fun bindInterventionSummary(summary: String) {
        val lines = summary
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val headline = lines.firstOrNull()
            ?: getString(R.string.morning_intervention_summary_empty)
        val detail = lines.drop(1).joinToString("\n").ifBlank {
            getString(R.string.morning_intervention_summary_empty)
        }
        binding.tvInterventionSummaryHeadline.text = headline
        binding.tvInterventionSummary.text = detail
    }

    private fun onAdviceClicked(advice: InterventionActionUiModel) {
        if (!InterventionActionNavigator.navigate(this, advice, "morningPrescription")) {
            Toast.makeText(requireContext(), advice.subtitle, Toast.LENGTH_LONG).show()
        }
    }

    private fun bindRecommendationCards() {
        val insight = latestRecommendationInsightState ?: return
        val ai = latestAiCredibilityState ?: return
        val metrics = latestHealthMetrics
        val personalization = latestPersonalizationState
        val tone = riskTone(ai.riskLevel)

        val evidenceCards = buildList {
            add(
                EvidenceCardModel(
                    title = "主导因子",
                    value = ai.primaryFactor.removePrefix("主导因子：").ifBlank { ai.primaryFactor },
                    note = ai.reason,
                    badgeText = ai.confidenceLabel,
                    tone = tone
                )
            )
            add(
                EvidenceCardModel(
                    title = "建议来源",
                    value = insight.metaLabel,
                    note = insight.reasons.takeIf { it.isNotEmpty() }?.joinToString("；")
                        ?: getString(R.string.morning_recommendation_explanation_reasons_empty),
                    badgeText = "证据 ${insight.reasons.size}",
                    tone = if (insight.reasons.isEmpty()) CardTone.WARNING else CardTone.INFO
                )
            )
            if (personalization != null) {
                add(
                    EvidenceCardModel(
                        title = "数据准备度",
                        value = if (personalization.isPreview) "部分就绪" else "完整就绪",
                        note = personalization.detail,
                        badgeText = personalization.label,
                        tone = if (personalization.isPreview) CardTone.WARNING else CardTone.POSITIVE
                    )
                )
            }
        }
        MedicalCardRenderer.renderEvidenceCards(binding.layoutRecommendationEvidenceCards, evidenceCards)

        MedicalCardRenderer.renderMiniTrendCards(
            binding.layoutRecommendationTrendCards,
            metrics?.let { buildTrendCards(it) }.orEmpty()
        )

        MedicalCardRenderer.renderRiskSummaryCard(
            binding.containerRecommendationRiskCard,
            RiskSummaryCardModel(
                badgeText = riskBadge(ai.riskLevel),
                title = "今日建议依据",
                summary = ai.summary,
                supportingText = insight.effectDetail,
                bullets = buildList {
                    add(ai.inferenceHint)
                    if (insight.effectHeadline.isNotBlank()) add(insight.effectHeadline)
                    if (personalization?.isPreview == true) add(personalization.detail)
                },
                tone = tone
            )
        )
    }

    private fun buildTrendCards(metrics: HealthMetrics): List<MiniTrendCardModel> {
        return listOf(
            MiniTrendCardModel(
                title = "心率",
                valueText = "${metrics.heartRate.current} bpm",
                trendText = "趋势：${trendLabel(metrics.heartRate.trend)}",
                supportingText = "区间 ${metrics.heartRate.min}-${metrics.heartRate.max} bpm",
                progressPercent = (((metrics.heartRate.current - 40).coerceIn(0, 60) / 60f) * 100).roundToInt(),
                tone = toneForTrend(metrics.heartRate.trend)
            ),
            MiniTrendCardModel(
                title = "血氧",
                valueText = "${metrics.bloodOxygen.current}%",
                trendText = "稳定度：${metrics.bloodOxygen.stability}",
                supportingText = "夜间最低 ${metrics.bloodOxygen.min}%",
                progressPercent = metrics.bloodOxygen.current.coerceIn(0, 100),
                tone = if (metrics.bloodOxygen.current >= 95) CardTone.POSITIVE else CardTone.WARNING
            ),
            MiniTrendCardModel(
                title = "HRV",
                valueText = "${metrics.hrv.current} ms",
                trendText = "恢复率：${String.format(Locale.getDefault(), "%.1f", metrics.hrv.recoveryRate)}%",
                supportingText = "基线 ${metrics.hrv.baseline} ms",
                progressPercent = metrics.hrv.current.coerceIn(0, 100),
                tone = toneForTrend(metrics.hrv.trend)
            )
        )
    }

    private fun adviceCategory(type: PrescriptionItemType): String {
        return when (type) {
            PrescriptionItemType.PRIMARY -> "主干预"
            PrescriptionItemType.SECONDARY -> "辅助干预"
            PrescriptionItemType.LIFESTYLE -> "生活任务"
        }
    }

    private fun adviceTone(type: PrescriptionItemType): CardTone {
        return when (type) {
            PrescriptionItemType.PRIMARY -> CardTone.INFO
            PrescriptionItemType.SECONDARY -> CardTone.POSITIVE
            PrescriptionItemType.LIFESTYLE -> CardTone.WARNING
        }
    }

    private fun riskTone(level: AnomalyLevel): CardTone {
        return when (level) {
            AnomalyLevel.CRITICAL,
            AnomalyLevel.ERROR -> CardTone.NEGATIVE
            AnomalyLevel.WARNING,
            AnomalyLevel.MILD -> CardTone.WARNING
            AnomalyLevel.NORMAL -> CardTone.POSITIVE
            AnomalyLevel.UNKNOWN -> CardTone.NEUTRAL
        }
    }

    private fun riskBadge(level: AnomalyLevel): String {
        return when (level) {
            AnomalyLevel.CRITICAL -> "高风险"
            AnomalyLevel.WARNING -> "中风险"
            AnomalyLevel.MILD -> "轻度风险"
            AnomalyLevel.ERROR,
            AnomalyLevel.UNKNOWN -> "待确认"
            AnomalyLevel.NORMAL -> "低风险"
        }
    }

    private fun toneForTrend(trend: Trend): CardTone {
        return when (trend) {
            Trend.UP -> CardTone.INFO
            Trend.DOWN -> CardTone.POSITIVE
            Trend.STABLE -> CardTone.NEUTRAL
        }
    }

    private fun trendLabel(trend: Trend): String {
        return when (trend) {
            Trend.UP -> "上升"
            Trend.DOWN -> "下降"
            Trend.STABLE -> "稳定"
        }
    }

    private fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..8 -> getString(R.string.morning_greeting_morning)
            in 9..11 -> getString(R.string.morning_greeting_noon)
            in 12..13 -> getString(R.string.morning_greeting_midday)
            in 14..17 -> getString(R.string.morning_greeting_afternoon)
            in 18..22 -> getString(R.string.morning_greeting_evening)
            else -> getString(R.string.morning_greeting_night)
        }
    }

    private fun formatMinutesToHours(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (mins > 0) "${hours}.${mins / 6}h" else "${hours}h"
    }

    private fun getTrendColor(trend: Trend): Int {
        return when (trend) {
            Trend.UP -> color(R.color.status_negative)
            Trend.DOWN -> color(R.color.status_positive)
            Trend.STABLE -> color(R.color.text_secondary)
        }
    }

    private fun renderAiRiskState(state: AiCredibilityState) {
        val riskPercent = (state.riskScore * 100f).roundToInt().coerceIn(0, 100)
        binding.tvAiRiskScoreBadge.text = getString(R.string.morning_risk_score_format, riskPercent)

        val showBanner = state.riskDetected || state.riskLevel == AnomalyLevel.ERROR
        binding.cardAiRiskBanner.visibility = if (showBanner) View.VISIBLE else View.GONE

        val visual = when (state.riskLevel) {
            AnomalyLevel.CRITICAL -> AiRiskVisual(
                levelText = getString(R.string.morning_risk_level_high),
                bannerTitle = getString(R.string.morning_risk_title_high),
                bannerDetail = getString(R.string.morning_risk_detail_high),
                accentColor = color(R.color.status_negative),
                surfaceColor = withAlpha(color(R.color.status_negative), 0.09f)
            )

            AnomalyLevel.WARNING -> AiRiskVisual(
                levelText = getString(R.string.morning_risk_level_medium),
                bannerTitle = getString(R.string.morning_risk_title_medium),
                bannerDetail = getString(R.string.morning_risk_detail_medium),
                accentColor = color(R.color.status_warning),
                surfaceColor = withAlpha(color(R.color.status_warning), 0.10f)
            )

            AnomalyLevel.MILD -> AiRiskVisual(
                levelText = getString(R.string.morning_risk_level_low),
                bannerTitle = getString(R.string.morning_risk_title_low),
                bannerDetail = getString(R.string.morning_risk_detail_low),
                accentColor = color(R.color.status_warning),
                surfaceColor = withAlpha(color(R.color.status_warning), 0.08f)
            )

            AnomalyLevel.ERROR -> AiRiskVisual(
                levelText = getString(R.string.morning_risk_level_unknown),
                bannerTitle = getString(R.string.morning_risk_title_error),
                bannerDetail = getString(R.string.morning_risk_detail_error),
                accentColor = color(R.color.status_warning),
                surfaceColor = withAlpha(color(R.color.status_warning), 0.08f)
            )

            AnomalyLevel.UNKNOWN -> AiRiskVisual(
                levelText = getString(R.string.morning_risk_level_unknown),
                bannerTitle = getString(R.string.morning_risk_title_unknown),
                bannerDetail = getString(R.string.morning_risk_detail_unknown),
                accentColor = color(R.color.text_secondary),
                surfaceColor = withAlpha(color(R.color.text_secondary), 0.08f)
            )

            AnomalyLevel.NORMAL -> AiRiskVisual(
                levelText = getString(R.string.morning_risk_level_normal),
                bannerTitle = getString(R.string.morning_risk_title_normal),
                bannerDetail = getString(R.string.morning_risk_detail_normal),
                accentColor = color(R.color.status_positive),
                surfaceColor = withAlpha(color(R.color.status_positive), 0.08f)
            )
        }

        binding.tvAiRiskLevelBadge.text = visual.levelText
        tintBadge(binding.tvAiRiskLevelBadge, visual.surfaceColor, visual.accentColor)
        tintBadge(binding.tvAiRiskScoreBadge, visual.surfaceColor, visual.accentColor)

        binding.cardAiPanel.strokeColor =
            if (showBanner) visual.accentColor else color(R.color.card_stroke)
        binding.cardAiPanel.strokeWidth = if (showBanner) dp(2) else dp(1)
        binding.tvAiSummary.setTextColor(
            if (showBanner) visual.accentColor else color(R.color.text_secondary)
        )

        if (showBanner) {
            binding.cardAiRiskBanner.setCardBackgroundColor(visual.surfaceColor)
            binding.cardAiRiskBanner.strokeColor = visual.accentColor
            binding.cardAiRiskBanner.strokeWidth = dp(1)
            binding.ivAiRiskIcon.imageTintList = ColorStateList.valueOf(visual.accentColor)
            binding.tvAiRiskTitle.text = visual.bannerTitle
            binding.tvAiRiskTitle.setTextColor(visual.accentColor)
            binding.tvAiRiskDetail.text = "${visual.bannerDetail} ${binding.tvAiRiskScoreBadge.text}"
            binding.tvAiRiskDetail.setTextColor(color(R.color.text_secondary))
        }
    }

    private fun tintBadge(view: TextView, backgroundColor: Int, textColor: Int) {
        view.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        view.setTextColor(textColor)
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val resolvedAlpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return Color.argb(resolvedAlpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private data class AiRiskVisual(
        val levelText: String,
        val bannerTitle: String,
        val bannerDetail: String,
        val accentColor: Int,
        val surfaceColor: Int
    )

    private fun color(@ColorRes colorRes: Int): Int =
        ContextCompat.getColor(requireContext(), colorRes)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

