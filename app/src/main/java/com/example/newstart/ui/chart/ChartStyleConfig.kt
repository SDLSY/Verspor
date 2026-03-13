package com.example.newstart.ui.chart

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import com.example.newstart.R
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry

object ChartStyleConfig {

    /**
     * 应用统一的 LineChart 样式
     */
    fun applyLineChartStyle(chart: LineChart, context: Context) {
        val textColor = ContextCompat.getColor(context, R.color.text_secondary)
        val gridColor = ContextCompat.getColor(context, R.color.card_stroke)

        chart.description.isEnabled = false
        chart.setDrawGridBackground(false)
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.legend.isEnabled = false

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            this.textColor = textColor
            textSize = 10f
            setDrawGridLines(false)
            setDrawAxisLine(false)
        }

        chart.axisLeft.apply {
            this.textColor = textColor
            textSize = 10f
            setDrawGridLines(true)
            this.gridColor = gridColor
            gridLineWidth = 0.5f
            setDrawAxisLine(false)
        }

        chart.axisRight.isEnabled = false
        chart.animateX(800, Easing.EaseInOutCubic)
    }

    /**
     * 应用统一的 PieChart 样式
     */
    fun applyPieChartStyle(chart: PieChart, context: Context, centerText: String = "") {
        val primaryTextColor = ContextCompat.getColor(context, R.color.text_primary)
        val secondaryTextColor = ContextCompat.getColor(context, R.color.text_secondary)
        val surfaceColor = ContextCompat.getColor(context, R.color.surface_card)

        chart.setUsePercentValues(true)
        chart.description.isEnabled = false
        
        chart.isDrawHoleEnabled = true
        chart.holeRadius = 55f
        chart.setHoleColor(surfaceColor)
        chart.transparentCircleRadius = 58f

        chart.setDrawCenterText(true)
        chart.centerText = centerText
        chart.setCenterTextColor(primaryTextColor)
        chart.setCenterTextSize(14f)

        chart.legend.apply {
            isEnabled = true
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            orientation = Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)
            textColor = secondaryTextColor
            textSize = 12f
            yEntrySpace = 5f
            xEntrySpace = 10f
        }

        chart.animateY(1000, Easing.EaseInOutQuad)
    }

    /**
     * 创建渐变填充的 LineDataSet
     */
    fun createGradientLineDataSet(
        entries: List<Entry>,
        label: String,
        lineColor: Int,
        context: Context
    ): LineDataSet {
        return LineDataSet(entries, label).apply {
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
            lineWidth = 2.5f
            color = lineColor

            setDrawFilled(true)
            val startColor = withAlpha(lineColor, 0.6f)
            val endColor = withAlpha(lineColor, 0.0f)
            fillDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(startColor, endColor)
            )

            setDrawCircles(true)
            circleRadius = 4f
            circleHoleRadius = 2f
            setCircleColor(lineColor)
            setCircleHoleColor(Color.WHITE)

            setDrawValues(false)
            setDrawHorizontalHighlightIndicator(false)
            highLightColor = withAlpha(lineColor, 0.5f)
        }
    }

    /**
     * 创建睡眠阶段饼图数据集
     */
    fun createSleepStagesPieDataSet(
        deep: Float, rem: Float, light: Float, awake: Float,
        context: Context
    ): PieDataSet {
        val entries = ArrayList<PieEntry>()
        val colors = ArrayList<Int>()

        if (deep > 0) {
            entries.add(PieEntry(deep, "深睡"))
            colors.add(ContextCompat.getColor(context, R.color.sleep_stage_deep))
        }
        if (rem > 0) {
            entries.add(PieEntry(rem, "REM"))
            colors.add(ContextCompat.getColor(context, R.color.sleep_stage_rem))
        }
        if (light > 0) {
            entries.add(PieEntry(light, "ǳ˯"))
            colors.add(ContextCompat.getColor(context, R.color.sleep_stage_light))
        }
        if (awake > 0) {
            entries.add(PieEntry(awake, "清醒"))
            colors.add(ContextCompat.getColor(context, R.color.sleep_stage_awake))
        }

        return PieDataSet(entries, "").apply {
            this.colors = colors
            sliceSpace = 2f
            selectionShift = 8f
            valueTextColor = Color.WHITE
            valueTextSize = 10f
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}
