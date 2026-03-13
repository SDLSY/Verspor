package com.example.newstart.ui.trend

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.newstart.core.common.R as CommonR
import com.example.newstart.feature.trend.databinding.FragmentSleepTrendBinding
import com.example.newstart.ui.chart.ChartStyleConfig
import com.example.newstart.ui.chart.CustomMarkerView
import com.example.newstart.ui.intervention.InterventionActionNavigator
import com.example.newstart.ui.intervention.InterventionActionUiModel
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

class SleepTrendFragment : Fragment() {

    private var _binding: FragmentSleepTrendBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SleepTrendViewModel by viewModels {
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
    }

    private var currentTimeRange = TimeRange.LAST_7_DAYS

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSleepTrendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
        setupCharts()
        observeData()
        loadData(currentTimeRange)
    }

    private fun setupUi() {
        binding.chipGroupTimeRange.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            currentTimeRange = when (checkedIds.first()) {
                com.example.newstart.feature.trend.R.id.chip_30_days -> TimeRange.LAST_30_DAYS
                else -> TimeRange.LAST_7_DAYS
            }
            loadData(currentTimeRange)
        }

        binding.btnTrendOpenCenter.setOnClickListener {
            findNavController().navigate(CommonR.id.navigation_intervention_center)
        }
    }

    private fun setupCharts() {
        setupLineChart(binding.chartSleepDuration)
        setupLineChart(binding.chartHeartRate)
        setupLineChart(binding.chartBloodOxygen)
        setupLineChart(binding.chartTemperature)
        setupLineChart(binding.chartRecoveryScore)
        setupLineChart(binding.chartMotion)
        setupLineChart(binding.chartPpgTrend)
        hideXAxisLabels(binding.chartHeartRate)
        hideXAxisLabels(binding.chartBloodOxygen)
        hideXAxisLabels(binding.chartTemperature)
        hideXAxisLabels(binding.chartRecoveryScore)
        hideXAxisLabels(binding.chartMotion)
        hideXAxisLabels(binding.chartPpgTrend)
        setupPieChart(binding.chartSleepQuality)
    }

    private fun observeData() {
        viewModel.sleepDurationData.observe(viewLifecycleOwner) { data ->
            updateLineChart(binding.chartSleepDuration, data.first, data.second, color(CommonR.color.chart_sleep))
            binding.tvAvgSleepDuration.text = if (data.second.isNotEmpty()) {
                getString(CommonR.string.trend_avg_sleep_format, data.second.average().toDouble())
            } else {
                getString(CommonR.string.trend_avg_placeholder)
            }
        }

        viewModel.heartRateTrendData.observe(viewLifecycleOwner) { data ->
            updateLineChart(binding.chartHeartRate, data.first, data.second, color(CommonR.color.chart_heart))
            binding.tvAvgHeartRate.text = if (data.second.isNotEmpty()) {
                getString(CommonR.string.trend_avg_heart_rate_format, data.second.average().roundToInt())
            } else {
                getString(CommonR.string.trend_avg_heart_rate_placeholder)
            }
        }

        viewModel.bloodOxygenTrendData.observe(viewLifecycleOwner) { data ->
            updateLineChart(binding.chartBloodOxygen, data.first, data.second, color(CommonR.color.chart_oxygen))
            binding.tvAvgBloodOxygen.text = if (data.second.isNotEmpty()) {
                getString(CommonR.string.trend_avg_blood_oxygen_format, data.second.average().roundToInt())
            } else {
                getString(CommonR.string.trend_avg_blood_oxygen_placeholder)
            }
        }

        viewModel.temperatureTrendData.observe(viewLifecycleOwner) { data ->
            updateLineChart(binding.chartTemperature, data.first, data.second, color(CommonR.color.chart_temperature))
            binding.tvAvgTemperature.text = if (data.second.isNotEmpty()) {
                getString(CommonR.string.trend_avg_temperature_format, data.second.average().toDouble())
            } else {
                getString(CommonR.string.trend_avg_temperature_placeholder)
            }
        }

        viewModel.recoveryScoreData.observe(viewLifecycleOwner) { data ->
            updateLineChart(binding.chartRecoveryScore, data.first, data.second, color(CommonR.color.chart_recovery))
            binding.tvAvgRecoveryScore.text = if (data.second.isNotEmpty()) {
                getString(CommonR.string.trend_avg_recovery_format, data.second.average().roundToInt())
            } else {
                getString(CommonR.string.trend_avg_recovery_placeholder)
            }
        }

        viewModel.motionTrendData.observe(viewLifecycleOwner) { data ->
            updateLineChart(binding.chartMotion, data.first, data.second, color(CommonR.color.chart_motion))
            binding.tvAvgMotion.text = if (data.second.isNotEmpty()) {
                getString(CommonR.string.trend_avg_motion_format, data.second.average().toDouble())
            } else {
                getString(CommonR.string.trend_avg_placeholder)
            }
        }

        viewModel.ppgTrendData.observe(viewLifecycleOwner) { data ->
            updateLineChart(binding.chartPpgTrend, data.first, data.second, color(CommonR.color.chart_ppg))
            binding.tvAvgPpg.text = if (data.second.isNotEmpty()) {
                getString(CommonR.string.trend_ppg_samples, data.second.size)
            } else {
                getString(CommonR.string.trend_ppg_samples_empty)
            }
        }

        viewModel.sleepQualityDistribution.observe(viewLifecycleOwner) { distribution ->
            updatePieChart(binding.chartSleepQuality, distribution)
        }

        viewModel.statistics.observe(viewLifecycleOwner) { stats ->
            binding.tvStatAvgDuration.text = stats.avgDuration
            binding.tvStatDeepSleep.text = stats.avgDeepSleepPercentage
            binding.tvStatEfficiency.text = stats.avgEfficiency
            binding.tvStatBestDay.text = stats.bestDay
        }

        viewModel.periodReport.observe(viewLifecycleOwner) { report ->
            bindPeriodReport(report)
        }
    }

    private fun bindPeriodReport(report: HealthPeriodReportUiModel) {
        binding.tvReportTitle.text = report.title
        binding.tvReportSource.text = report.sourceLabel
        binding.tvReportHeadline.text = report.headline
        binding.tvReportSampleHint.text = report.sampleHint
        binding.tvReportRiskBadge.text = report.riskLabel
        binding.tvReportRiskSummary.text = report.riskSummary
        binding.tvReportHighlights.text = report.highlightsText
        binding.tvReportMetrics.text = report.metricChangesText
        binding.tvReportInterventionSummary.text = report.interventionSummary
        binding.tvReportNextFocusTitle.text = report.nextFocusTitle
        binding.tvReportNextFocusDetail.text = report.nextFocusDetail
        styleRiskBadge(report.riskLabel)
        bindReportAction(
            binding.btnReportPrimaryAction,
            report.primaryAction,
            CommonR.string.trend_period_primary_action
        )
        bindReportAction(
            binding.btnReportSecondaryAction,
            report.secondaryAction,
            CommonR.string.trend_period_secondary_action
        )
    }

    private fun bindReportAction(
        button: MaterialButton,
        action: InterventionActionUiModel?,
        labelRes: Int
    ) {
        if (action == null) {
            button.isVisible = false
            return
        }
        button.isVisible = true
        button.text = getString(labelRes) + " · " + action.title
        button.setOnClickListener { onReportActionClicked(action) }
    }

    private fun onReportActionClicked(action: InterventionActionUiModel) {
        if (!InterventionActionNavigator.navigate(this, action, "periodReportPrescription")) {
            Toast.makeText(requireContext(), action.subtitle, Toast.LENGTH_LONG).show()
        }
    }

    private fun styleRiskBadge(riskLabel: String) {
        val fillColor = when {
            riskLabel.contains("高") -> withAlpha(color(CommonR.color.status_negative), 0.18f)
            riskLabel.contains("中") -> withAlpha(color(CommonR.color.status_warning), 0.18f)
            else -> withAlpha(color(CommonR.color.status_positive), 0.18f)
        }
        val textColor = when {
            riskLabel.contains("高") -> color(CommonR.color.status_negative)
            riskLabel.contains("中") -> color(CommonR.color.status_warning)
            else -> color(CommonR.color.status_positive)
        }
        binding.tvReportRiskBadge.setTextColor(textColor)
        (binding.tvReportRiskBadge.background?.mutate() as? GradientDrawable)?.setColor(fillColor)
    }

    private fun hideXAxisLabels(chart: LineChart) {
        chart.xAxis.setDrawLabels(false)
        chart.xAxis.setDrawAxisLine(false)
    }

    private fun setupLineChart(chart: LineChart) {
        ChartStyleConfig.applyLineChartStyle(chart, requireContext())
        val textColor = ContextCompat.getColor(requireContext(), CommonR.color.md_theme_light_onSurfaceVariant)
        chart.setNoDataText(getString(CommonR.string.no_data))
        chart.setNoDataTextColor(textColor)
        chart.setExtraOffsets(6f, 10f, 14f, 10f)
        chart.marker = CustomMarkerView(requireContext())
        chart.xAxis.apply {
            setAvoidFirstLastClipping(true)
            labelRotationAngle = -35f
        }
    }

    private fun setupPieChart(chart: PieChart) {
        ChartStyleConfig.applyPieChartStyle(
            chart = chart,
            context = requireContext(),
            centerText = getString(CommonR.string.sleep_stage_center_text)
        )
    }

    private fun updateLineChart(
        chart: LineChart,
        labels: List<String>,
        values: List<Float>,
        lineColor: Int
    ) {
        if (values.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        val resolvedLabels = if (labels.isEmpty()) {
            values.indices.map { (it + 1).toString() }
        } else {
            labels
        }

        val renderValues = smoothForRender(values)
        val entries = renderValues.mapIndexed { index, value -> Entry(index.toFloat(), value) }
        val safeLabels = when {
            resolvedLabels.size == renderValues.size -> resolvedLabels
            resolvedLabels.size > renderValues.size -> resolvedLabels.take(renderValues.size)
            else -> renderValues.indices.map { index -> resolvedLabels.getOrElse(index) { "" } }
        }

        val dataSet = ChartStyleConfig.createGradientLineDataSet(
            entries = entries,
            label = "",
            lineColor = lineColor,
            context = requireContext()
        )

        chart.data = LineData(mutableListOf<ILineDataSet>(dataSet))
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(safeLabels)
        chart.xAxis.setLabelCount((safeLabels.size.coerceAtMost(4)).coerceAtLeast(2), false)
        chart.xAxis.granularity = 1f
        chart.xAxis.isGranularityEnabled = true

        val minY = renderValues.minOrNull() ?: 0f
        val maxY = renderValues.maxOrNull() ?: 0f
        val padding = ((maxY - minY) * 0.15f).coerceAtLeast(1f)
        chart.axisLeft.axisMinimum = minY - padding
        chart.axisLeft.axisMaximum = maxY + padding

        chart.animateX(520, Easing.EaseOutCubic)
        chart.invalidate()
    }

    private fun updatePieChart(chart: PieChart, distribution: Map<String, Float>) {
        val deep = distribution["深睡"] ?: 0f
        val rem = distribution["REM"] ?: 0f
        val light = distribution["浅睡"] ?: 0f
        val awake = distribution["清醒"] ?: 0f

        val dataSet = ChartStyleConfig.createSleepStagesPieDataSet(
            deep = deep,
            rem = rem,
            light = light,
            awake = awake,
            context = requireContext()
        )

        val pieData = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter(chart))
            setValueTextSize(11f)
            setValueTextColor(Color.WHITE)
        }

        chart.data = pieData
        chart.invalidate()
    }

    private fun loadData(timeRange: TimeRange) {
        viewModel.loadTrendData(timeRange)
    }

    private fun color(@ColorRes colorRes: Int): Int =
        ContextCompat.getColor(requireContext(), colorRes)

    private fun withAlpha(color: Int, alpha: Float): Int {
        val resolvedAlpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return Color.argb(resolvedAlpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun smoothForRender(values: List<Float>): List<Float> {
        if (values.size < 5) return values
        val radius = when {
            values.size >= 90 -> 3
            values.size >= 40 -> 2
            else -> 1
        }
        return MutableList(values.size) { 0f }.also { smoothed ->
            for (index in values.indices) {
                val from = (index - radius).coerceAtLeast(0)
                val to = (index + radius).coerceAtMost(values.lastIndex)
                smoothed[index] = values.subList(from, to + 1).average().toFloat()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
