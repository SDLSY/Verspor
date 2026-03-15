package com.example.newstart.ui.relax

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.newstart.core.common.ui.cards.ActionGroupCardModel
import com.example.newstart.core.common.ui.cards.CardTone
import com.example.newstart.core.common.ui.cards.EvidenceCardModel
import com.example.newstart.core.common.ui.cards.MedicalCardRenderer
import com.example.newstart.core.common.ui.cards.RiskSummaryCardModel
import com.example.newstart.core.common.R as CommonR
import com.example.newstart.feature.relax.R
import com.example.newstart.feature.relax.databinding.FragmentRelaxReviewBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.util.Locale

class RelaxReviewFragment : Fragment() {

    private var _binding: FragmentRelaxReviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RelaxReviewViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRelaxReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActions()
        setupChart(binding.chartRelaxReviewEffect)
        observeData()
    }

    private fun setupActions() {
        binding.btnRelaxReviewBack.setOnClickListener {
            if (!findNavController().popBackStack()) {
                findNavController().navigate(CommonR.id.navigation_relax_hub)
            }
        }

        binding.chipGroupRelaxReviewRange.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            when (checkedIds.first()) {
                R.id.chip_relax_review_7 -> viewModel.setRange(RelaxReviewRange.LAST_7_DAYS)
                R.id.chip_relax_review_30 -> viewModel.setRange(RelaxReviewRange.LAST_30_DAYS)
            }
        }
    }

    private fun observeData() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            if (state.range == RelaxReviewRange.LAST_7_DAYS && !binding.chipRelaxReview7.isChecked) {
                binding.chipRelaxReview7.isChecked = true
            } else if (state.range == RelaxReviewRange.LAST_30_DAYS && !binding.chipRelaxReview30.isChecked) {
                binding.chipRelaxReview30.isChecked = true
            }

            binding.tvRelaxReviewSessions.text = state.totalSessions.toString()
            binding.tvRelaxReviewMinutes.text = state.totalMinutes.toString()
            binding.tvRelaxReviewAvgEffect.text = state.avgEffectScore.toString()
            binding.tvRelaxReviewAvgDrop.text = getString(
                CommonR.string.relax_review_avg_drop_format,
                state.avgStressDrop
            )
            binding.tvRelaxReviewTopProtocolName.text = state.topProtocolName
            binding.tvRelaxReviewTopProtocolDetail.text = state.topProtocolDetail
            binding.tvRelaxReviewModalitySummary.text = if (state.modalitySummaryLines.isEmpty()) {
                getString(CommonR.string.relax_review_modality_empty)
            } else {
                buildString {
                    append(state.modalitySummaryLines.joinToString("\n"))
                    if (state.bestModalityHint.isNotBlank()) {
                        append("\n\n最近更适合：")
                        append(state.bestModalityHint)
                    }
                }
            }

            if (state.recoveryLinkedDays > 0) {
                binding.tvRelaxReviewRecoverySameDay.text = getString(
                    CommonR.string.relax_review_recovery_score_format,
                    state.recoverySameDayAvg
                )
                binding.tvRelaxReviewRecoveryNextDay.text = getString(
                    CommonR.string.relax_review_recovery_score_format,
                    state.recoveryNextDayAvg
                )
                binding.tvRelaxReviewRecoveryDelta.text = getString(
                    CommonR.string.relax_review_recovery_delta_format,
                    state.recoveryDelta
                )
                binding.tvRelaxReviewRecoveryControl.text = getString(
                    CommonR.string.relax_review_recovery_control_format,
                    state.recoveryControlDelta,
                    state.recoveryControlDays
                )
                binding.tvRelaxReviewRecoveryGain.text = getString(
                    CommonR.string.relax_review_recovery_gain_format,
                    state.recoveryGainVsControl,
                    state.recoveryLinkedDays
                )
                binding.tvRelaxReviewRecoveryDelta.setTextColor(
                    if (state.recoveryDelta >= 0f) {
                        color(CommonR.color.status_positive)
                    } else {
                        color(CommonR.color.status_negative)
                    }
                )
                binding.tvRelaxReviewRecoveryGain.setTextColor(
                    when {
                        state.recoveryGainVsControl > 0.8f -> color(CommonR.color.status_positive)
                        state.recoveryGainVsControl < -0.8f -> color(CommonR.color.status_negative)
                        else -> color(CommonR.color.text_secondary)
                    }
                )
                binding.tvRelaxReviewRecoveryLinkSubtitle.text = getString(
                    CommonR.string.relax_review_recovery_samples_format,
                    state.recoveryLinkedDays
                )
            } else {
                binding.tvRelaxReviewRecoverySameDay.text = "--"
                binding.tvRelaxReviewRecoveryNextDay.text = "--"
                binding.tvRelaxReviewRecoveryDelta.text = getString(CommonR.string.relax_review_no_data)
                binding.tvRelaxReviewRecoveryControl.text = getString(CommonR.string.relax_review_no_data)
                binding.tvRelaxReviewRecoveryGain.text = getString(CommonR.string.relax_review_no_data)
                binding.tvRelaxReviewRecoveryDelta.setTextColor(color(CommonR.color.text_secondary))
                binding.tvRelaxReviewRecoveryGain.setTextColor(color(CommonR.color.text_secondary))
                binding.tvRelaxReviewRecoveryLinkSubtitle.text = getString(CommonR.string.relax_review_no_data)
            }

            updateTrendChart(
                chart = binding.chartRelaxReviewEffect,
                labels = state.trendLabels,
                values = state.trendValues
            )
            renderProtocolRows(state.protocolRows)
            renderReviewCards(state)
            binding.tvRelaxReviewEmpty.visibility = if (state.hasData) View.GONE else View.VISIBLE
        }
    }

    private fun setupChart(chart: LineChart) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setDrawGridBackground(false)
        chart.setNoDataText(getString(CommonR.string.relax_review_no_data))
        chart.setNoDataTextColor(color(CommonR.color.text_secondary))
        chart.legend.isEnabled = false
        chart.setExtraOffsets(6f, 8f, 12f, 10f)

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setAvoidFirstLastClipping(true)
            granularity = 1f
            textSize = 10f
            textColor = color(CommonR.color.text_secondary)
            setLabelCount(4, false)
        }

        chart.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = 100f
            setDrawGridLines(true)
            setDrawAxisLine(false)
            textSize = 10f
            textColor = color(CommonR.color.text_secondary)
            gridColor = color(CommonR.color.card_stroke)
        }
        chart.axisRight.isEnabled = false
    }

    private fun updateTrendChart(chart: LineChart, labels: List<String>, values: List<Float>) {
        if (labels.isEmpty() || values.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        val entries = values.mapIndexed { index, value ->
            Entry(index.toFloat(), value.coerceIn(0f, 100f))
        }

        val dataSet = LineDataSet(entries, "").apply {
            color = color(CommonR.color.chart_recovery)
            lineWidth = 2.6f
            setDrawCircles(values.size <= 30)
            setDrawCircleHole(false)
            circleRadius = 2.6f
            setCircleColor(withAlpha(color(CommonR.color.chart_recovery), 0.75f))
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = withAlpha(color(CommonR.color.chart_recovery), 0.18f)
            setDrawHorizontalHighlightIndicator(false)
            highLightColor = withAlpha(color(CommonR.color.text_primary), 0.35f)
        }

        chart.data = LineData(dataSet)
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chart.xAxis.setLabelCount((labels.size.coerceAtMost(6)).coerceAtLeast(2), false)
        chart.xAxis.granularity = 1f
        chart.invalidate()
    }

    private fun renderProtocolRows(rows: List<RelaxProtocolRankRowUi>) {
        binding.layoutRelaxProtocolRankList.removeAllViews()

        rows.forEach { row ->
            val itemView = layoutInflater.inflate(
                R.layout.item_relax_protocol_rank,
                binding.layoutRelaxProtocolRankList,
                false
            )

            itemView.findViewById<android.widget.TextView>(R.id.tv_protocol_name).text = row.protocolName
            itemView.findViewById<android.widget.TextView>(R.id.tv_protocol_detail).text = row.detail
            itemView.findViewById<android.widget.TextView>(R.id.tv_protocol_score).text = row.effectScore.toString()
            itemView.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(
                R.id.progress_protocol_score
            ).progress = row.progress

            binding.layoutRelaxProtocolRankList.addView(itemView)
        }
    }

    private fun renderReviewCards(state: RelaxReviewUiState) {
        val summaryCards = listOf(
            EvidenceCardModel(
                title = "训练次数",
                value = state.totalSessions.toString(),
                note = if (state.range == RelaxReviewRange.LAST_7_DAYS) "近 7 天累计执行次数" else "近 30 天累计执行次数",
                badgeText = "执行",
                tone = if (state.totalSessions > 0) CardTone.INFO else CardTone.NEUTRAL
            ),
            EvidenceCardModel(
                title = "累计分钟",
                value = state.totalMinutes.toString(),
                note = "当前统计窗口内的总训练时长",
                badgeText = "时长",
                tone = if (state.totalMinutes >= 30) CardTone.POSITIVE else CardTone.NEUTRAL
            ),
            EvidenceCardModel(
                title = "平均效果",
                value = state.avgEffectScore.toString(),
                note = getString(CommonR.string.relax_review_avg_drop_format, state.avgStressDrop),
                badgeText = "效果",
                tone = when {
                    state.avgEffectScore >= 75 -> CardTone.POSITIVE
                    state.avgEffectScore >= 50 -> CardTone.INFO
                    state.avgEffectScore > 0 -> CardTone.WARNING
                    else -> CardTone.NEUTRAL
                }
            )
        )
        MedicalCardRenderer.renderEvidenceCards(binding.layoutRelaxReviewSummaryCards, summaryCards)

        val riskCard = if (!state.hasData) {
            RiskSummaryCardModel(
                badgeText = "待积累",
                title = "还没有形成稳定复盘结论",
                summary = "先继续完成训练和恢复记录，再判断哪种方案更有效。",
                supportingText = "当前没有足够的执行样本和恢复样本。",
                bullets = listOf("先从放松中心继续执行主方案", "至少积累 3 到 5 次记录后再回来查看趋势"),
                tone = CardTone.NEUTRAL
            )
        } else {
            val gain = state.recoveryGainVsControl
            val tone = when {
                gain >= 0.8f && state.avgEffectScore >= 70 -> CardTone.POSITIVE
                gain >= 0f -> CardTone.INFO
                else -> CardTone.WARNING
            }
            val badge = when (tone) {
                CardTone.POSITIVE -> "明显改善"
                CardTone.INFO -> "持续观察"
                else -> "有待加强"
            }
            RiskSummaryCardModel(
                badgeText = badge,
                title = if (tone == CardTone.POSITIVE) {
                    "当前方案对恢复有正向帮助"
                } else {
                    "当前方案仍需继续观察和微调"
                },
                summary = "本窗口内最有效的方案是 ${state.topProtocolName}，平均效果 ${state.avgEffectScore}，恢复增益 ${String.format(Locale.getDefault(), "%+.1f", gain)}。",
                supportingText = if (state.recoveryLinkedDays > 0) {
                    "恢复联动样本 ${state.recoveryLinkedDays} 天，对照样本 ${state.recoveryControlDays} 天。"
                } else {
                    "当前恢复联动样本不足，结论以趋势观察为主。"
                },
                bullets = listOf(
                    "最佳方案：${state.topProtocolName}",
                    "平均下降：${String.format(Locale.getDefault(), "%.1f", state.avgStressDrop)}",
                    "次日恢复增益：${String.format(Locale.getDefault(), "%+.1f", state.recoveryDelta)}"
                ),
                tone = tone
            )
        }
        MedicalCardRenderer.renderRiskSummaryCard(binding.containerRelaxReviewRiskCard, riskCard)

        val trendHeadline = "继续查看睡眠与恢复趋势"
        val actionCards = listOf(
            ActionGroupCardModel(
                category = "继续执行",
                headline = if (state.hasData) "再次执行最有效方案：${state.topProtocolName}" else "先回到放松中心开始训练",
                supportingText = if (state.hasData) {
                    state.topProtocolDetail
                } else {
                    "先形成基础样本，再回来看复盘趋势会更有意义。"
                },
                detailLines = listOf(
                    if (state.hasData) {
                        "当前窗口内累计 ${state.totalSessions} 次训练"
                    } else {
                        "当前暂无可用复盘样本"
                    }
                ),
                actionLabel = "进入放松中心",
                enabled = true,
                tone = if (state.hasData) CardTone.POSITIVE else CardTone.INFO
            ),
            ActionGroupCardModel(
                category = "趋势对照",
                headline = trendHeadline,
                supportingText = "把训练复盘和趋势页一起看，更容易判断是否真的有效。",
                detailLines = listOf(
                    if (state.recoveryLinkedDays > 0) {
                        "当前已有 ${state.recoveryLinkedDays} 天恢复联动样本"
                    } else {
                        "恢复联动样本不足时，先看整体趋势更稳妥"
                    }
                ),
                actionLabel = "查看趋势",
                enabled = true,
                tone = CardTone.INFO
            )
        )
        MedicalCardRenderer.renderActionGroupCards(binding.layoutRelaxReviewActionCards, actionCards) { card ->
            when (card.headline) {
                trendHeadline -> findNavController().navigate(CommonR.id.navigation_trend)
                else -> findNavController().navigate(CommonR.id.navigation_relax_hub)
            }
        }
    }

    private fun color(@ColorRes colorRes: Int): Int = ContextCompat.getColor(requireContext(), colorRes)

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
