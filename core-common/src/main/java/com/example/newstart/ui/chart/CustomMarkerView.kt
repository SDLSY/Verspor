package com.example.newstart.ui.chart

import android.content.Context
import android.widget.TextView
import com.example.newstart.core.common.R
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF

class CustomMarkerView(
    context: Context,
    layoutResource: Int = R.layout.chart_marker_view
) : MarkerView(context, layoutResource) {

    private val tvDate: TextView = findViewById(R.id.tv_marker_date)
    private val tvValue: TextView = findViewById(R.id.tv_marker_value)
    
    private var dateFormatter: ((Float) -> String)? = null
    private var valueFormatter: ((Float) -> String)? = null

    /**
     * 设置日期格式化器
     */
    fun setDateFormatter(formatter: (Float) -> String) {
        dateFormatter = formatter
    }

    /**
     * 设置数值格式化器
     */
    fun setValueFormatter(formatter: (Float) -> String) {
        valueFormatter = formatter
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        e?.let { entry ->
            tvDate.text = dateFormatter?.invoke(entry.x) ?: "Day ${entry.x.toInt()}"
            tvValue.text = valueFormatter?.invoke(entry.y) ?: entry.y.toString()
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        // 将 marker 居中显示在数据点上方
        return MPPointF(-(width / 2f), -height.toFloat() - 10f)
    }
}

