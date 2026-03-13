package com.example.newstart.ui.relax

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.os.Bundle
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.newstart.R
import com.example.newstart.databinding.FragmentRelaxReviewBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

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
                findNavController().navigate(R.id.navigation_relax_hub)
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
                R.string.relax_review_avg_drop_format,
                state.avgStressDrop
            )
            binding.tvRelaxReviewTopProtocolName.text = state.topProtocolName
            binding.tvRelaxReviewTopProtocolDetail.text = state.topProtocolDetail

            if (state.recoveryLinkedDays > 0) {
                binding.tvRelaxReviewRecoverySameDay.text = getString(
                    R.string.relax_review_recovery_score_format,
                    state.recoverySameDayAvg
                )
                binding.tvRelaxReviewRecoveryNextDay.text = getString(
                    R.string.relax_review_recovery_score_format,
                    state.recoveryNextDayAvg
                )
                binding.tvRelaxReviewRecoveryDelta.text = getString(
                    R.string.relax_review_recovery_delta_format,
                    state.recoveryDelta
                )
                binding.tvRelaxReviewRecoveryControl.text = getString(
                    R.string.relax_review_recovery_control_format,
                    state.recoveryControlDelta,
                    state.recoveryControlDays
                )
                binding.tvRelaxReviewRecoveryGain.text = getString(
                    R.string.relax_review_recovery_gain_format,
                    state.recoveryGainVsControl,
                    state.recoveryLinkedDays
                )
                val gainColor = when {
                    state.recoveryGainVsControl > 0.8f -> color(R.color.status_positive)
                    state.recoveryGainVsControl < -0.8f -> color(R.color.status_negative)
                    else -> color(R.color.text_secondary)
                }
                binding.tvRelaxReviewRecoveryDelta.setTextColor(
                    if (state.recoveryDelta >= 0f) color(R.color.status_positive) else color(R.color.status_negative)
                )
                binding.tvRelaxReviewRecoveryGain.setTextColor(gainColor)
                binding.tvRelaxReviewRecoveryLinkSubtitle.text = getString(
                    R.string.relax_review_recovery_samples_format,
                    state.recoveryLinkedDays
                )
            } else {
                binding.tvRelaxReviewRecoverySameDay.text = "--"
                binding.tvRelaxReviewRecoveryNextDay.text = "--"
                binding.tvRelaxReviewRecoveryDelta.text = getString(R.string.relax_review_no_data)
                binding.tvRelaxReviewRecoveryControl.text = getString(R.string.relax_review_no_data)
                binding.tvRelaxReviewRecoveryGain.text = getString(R.string.relax_review_no_data)
                binding.tvRelaxReviewRecoveryDelta.setTextColor(color(R.color.text_secondary))
                binding.tvRelaxReviewRecoveryGain.setTextColor(color(R.color.text_secondary))
                binding.tvRelaxReviewRecoveryLinkSubtitle.text = getString(R.string.relax_review_no_data)
            }

            updateTrendChart(
                chart = binding.chartRelaxReviewEffect,
                labels = state.trendLabels,
                values = state.trendValues
            )
            renderProtocolRows(state.protocolRows)

            binding.tvRelaxReviewEmpty.visibility = if (state.hasData) View.GONE else View.VISIBLE
        }
    }

    private fun setupChart(chart: LineChart) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setDrawGridBackground(false)
        chart.setNoDataText(getString(R.string.relax_review_no_data))
        chart.setNoDataTextColor(color(R.color.text_secondary))
        chart.legend.isEnabled = false
        chart.setExtraOffsets(6f, 8f, 12f, 10f)

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setAvoidFirstLastClipping(true)
            granularity = 1f
            textSize = 10f
            textColor = color(R.color.text_secondary)
            setLabelCount(4, false)
        }

        chart.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = 100f
            setDrawGridLines(true)
            setDrawAxisLine(false)
            textSize = 10f
            textColor = color(R.color.text_secondary)
            gridColor = color(R.color.card_stroke)
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
            color = color(R.color.chart_recovery)
            lineWidth = 2.6f
            setDrawCircles(values.size <= 30)
            setDrawCircleHole(false)
            circleRadius = 2.6f
            setCircleColor(withAlpha(color(R.color.chart_recovery), 0.75f))
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = withAlpha(color(R.color.chart_recovery), 0.18f)
            setDrawHorizontalHighlightIndicator(false)
            highLightColor = withAlpha(color(R.color.text_primary), 0.35f)
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
