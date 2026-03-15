package com.example.newstart.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.example.newstart.core.common.R

class SleepStagesProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 睡眠阶段数据（百分比，总和应为 1.0）
    data class SleepStages(
        val deep: Float = 0f,
        val rem: Float = 0f,
        val light: Float = 0f,
        val awake: Float = 0f
    )

    private var barHeight: Float = 0f
    private var cornerRadius: Float = 0f
    private var segmentGap: Float = 0f
    private var animDuration: Long = 600L

    private var deepColor: Int = 0
    private var remColor: Int = 0
    private var lightColor: Int = 0
    private var awakeColor: Int = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()

    private var targetStages = SleepStages()
    private var currentStages = SleepStages()

    init {
        val density = context.resources.displayMetrics.density
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SleepStagesProgressView)

        barHeight = typedArray.getDimension(R.styleable.SleepStagesProgressView_sspv_height, 12f * density)
        cornerRadius = typedArray.getDimension(R.styleable.SleepStagesProgressView_sspv_cornerRadius, 6f * density)
        segmentGap = typedArray.getDimension(R.styleable.SleepStagesProgressView_sspv_segmentGap, 2f * density)
        animDuration = typedArray.getInt(R.styleable.SleepStagesProgressView_sspv_animDuration, 600).toLong()

        deepColor = typedArray.getColor(
            R.styleable.SleepStagesProgressView_sspv_deepSleepColor,
            ContextCompat.getColor(context, R.color.sleep_stage_deep)
        )
        remColor = typedArray.getColor(
            R.styleable.SleepStagesProgressView_sspv_remSleepColor,
            ContextCompat.getColor(context, R.color.sleep_stage_rem)
        )
        lightColor = typedArray.getColor(
            R.styleable.SleepStagesProgressView_sspv_lightSleepColor,
            ContextCompat.getColor(context, R.color.sleep_stage_light)
        )
        awakeColor = typedArray.getColor(
            R.styleable.SleepStagesProgressView_sspv_awakeColor,
            ContextCompat.getColor(context, R.color.sleep_stage_awake)
        )

        typedArray.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = barHeight.toInt() + paddingTop + paddingBottom
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val availableWidth = width - paddingStart - paddingEnd
        val top = (height - barHeight) / 2f
        val bottom = top + barHeight

        var currentX = paddingStart.toFloat()

        // 绘制四个阶段
        val stages = listOf(
            Pair(currentStages.deep, deepColor),
            Pair(currentStages.rem, remColor),
            Pair(currentStages.light, lightColor),
            Pair(currentStages.awake, awakeColor)
        )

        stages.forEachIndexed { index, (ratio, color) ->
            if (ratio <= 0f) return@forEachIndexed

            val segmentWidth = availableWidth * ratio - if (index < stages.size - 1 && ratio > 0) segmentGap else 0f
            if (segmentWidth <= 0f) return@forEachIndexed

            paint.color = color
            rectF.set(currentX, top, currentX + segmentWidth, bottom)
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)

            currentX += segmentWidth + segmentGap
        }
    }

    /**
     * 设置睡眠阶段数据
     * @param deep 深睡占比 (0-1)
     * @param rem REM 占比 (0-1)
     * @param light 浅睡占比 (0-1)
     * @param awake 清醒占比 (0-1)
     * @param animate 是否启用过渡动画
     */
    fun setStages(deep: Float, rem: Float, light: Float, awake: Float = 0f, animate: Boolean = true) {
        // 归一化，确保总和为 1
        val total = deep + rem + light + awake
        val normalizedDeep = if (total > 0) deep / total else 0f
        val normalizedRem = if (total > 0) rem / total else 0f
        val normalizedLight = if (total > 0) light / total else 0f
        val normalizedAwake = if (total > 0) awake / total else 0f

        targetStages = SleepStages(normalizedDeep, normalizedRem, normalizedLight, normalizedAwake)

        if (animate) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = animDuration
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    currentStages = SleepStages(
                        deep = lerp(currentStages.deep, targetStages.deep, fraction),
                        rem = lerp(currentStages.rem, targetStages.rem, fraction),
                        light = lerp(currentStages.light, targetStages.light, fraction),
                        awake = lerp(currentStages.awake, targetStages.awake, fraction)
                    )
                    invalidate()
                }
                start()
            }
        } else {
            currentStages = targetStages
            invalidate()
        }
    }

    /**
     * 使用分钟数设置睡眠阶段（自动计算占比）
     */
    fun setStagesFromMinutes(deepMinutes: Int, remMinutes: Int, lightMinutes: Int, awakeMinutes: Int = 0, animate: Boolean = true) {
        val total = (deepMinutes + remMinutes + lightMinutes + awakeMinutes).toFloat()
        if (total <= 0) return
        setStages(
            deep = deepMinutes / total,
            rem = remMinutes / total,
            light = lightMinutes / total,
            awake = awakeMinutes / total,
            animate = animate
        )
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }
}
