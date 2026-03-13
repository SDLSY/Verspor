package com.example.newstart.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatTextView
import java.text.DecimalFormat

/**
 * 数字滚动计数器
 * 数字变化时显示平滑的滚动动画
 */
class AnimatedCounterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var animator: ValueAnimator? = null
    private var currentValue: Float = 0f
    private var decimalFormat: DecimalFormat = DecimalFormat("#")
    private var suffix: String = ""
    private var prefix: String = ""
    private var duration: Long = 800L

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            com.example.newstart.R.styleable.AnimatedCounterView,
            0, 0
        ).apply {
            try {
                getString(com.example.newstart.R.styleable.AnimatedCounterView_acv_format)?.let {
                    decimalFormat = DecimalFormat(it)
                }
                getString(com.example.newstart.R.styleable.AnimatedCounterView_acv_prefix)?.let {
                    prefix = it
                }
                getString(com.example.newstart.R.styleable.AnimatedCounterView_acv_suffix)?.let {
                    suffix = it
                }
                getInt(com.example.newstart.R.styleable.AnimatedCounterView_acv_duration, 800).toLong().let {
                    duration = it
                }
            } finally {
                recycle()
            }
        }
    }

    /**
     * 设置显示格式
     * @param pattern 格式模式，如 "#.#" 表示一位小数
     */
    fun setFormat(pattern: String) {
        decimalFormat = DecimalFormat(pattern)
    }

    /**
     * 设置前缀和后缀
     */
    fun setAffixes(prefix: String = "", suffix: String = "") {
        this.prefix = prefix
        this.suffix = suffix
    }

    /**
     * 设置目标值并播放动画
     * @param targetValue 目标数值
     * @param animDuration 动画时长（毫秒）
     * @param animate 是否使用动画
     */
    fun setCounterValue(targetValue: Float, animDuration: Long = duration, animate: Boolean = true) {
        animator?.cancel()
        
        if (!animate) {
            currentValue = targetValue
            updateText(targetValue)
            return
        }

        animator = ValueAnimator.ofFloat(currentValue, targetValue).apply {
            this.duration = animDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                // Don't update currentValue here, keep it for next animation start point
                updateText(value)
            }
            start()
        }
        
        currentValue = targetValue
    }

    /**
     * 设置整数值
     */
    fun setCounterValue(targetValue: Int, animDuration: Long = duration, animate: Boolean = true) {
        setCounterValue(targetValue.toFloat(), animDuration, animate)
    }

    private fun updateText(value: Float) {
        text = "$prefix${decimalFormat.format(value)}$suffix"
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
