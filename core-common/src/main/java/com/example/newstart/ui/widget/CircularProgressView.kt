package com.example.newstart.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.example.newstart.core.common.R
import kotlin.math.min

class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress: Float = 0f
    private var maxProgress: Float = 100f
    private var strokeWidth: Float = 0f
    private var trackColor: Int = 0
    private var progressStartColor: Int = 0
    private var progressEndColor: Int = 0
    private var animDuration: Long = 800L
    private var showCenterText: Boolean = false
    private var centerTextSize: Float = 0f
    private var centerTextColor: Int = 0

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rectF = RectF()
    private var gradient: SweepGradient? = null
    private val gradientMatrix = Matrix()

    private var currentProgress: Float = 0f

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CircularProgressView)

        maxProgress = typedArray.getFloat(R.styleable.CircularProgressView_cpv_maxProgress, 100f)
        progress = typedArray.getFloat(R.styleable.CircularProgressView_cpv_progress, 0f)
        currentProgress = progress
        
        // Default 12dp
        val density = context.resources.displayMetrics.density
        strokeWidth = typedArray.getDimension(R.styleable.CircularProgressView_cpv_strokeWidth, 12f * density)
        
        // Default colors
        val defaultStartColor = ContextCompat.getColor(context, R.color.recovery_gradient_start)
        val defaultEndColor = ContextCompat.getColor(context, R.color.recovery_gradient_end)
        val defaultTrackColor = Color.parseColor("#33FFFFFF") // 20% opacity white
        val defaultTextColor = ContextCompat.getColor(context, R.color.text_primary)

        progressStartColor = typedArray.getColor(R.styleable.CircularProgressView_cpv_progressStartColor, defaultStartColor)
        progressEndColor = typedArray.getColor(R.styleable.CircularProgressView_cpv_progressEndColor, defaultEndColor)
        trackColor = typedArray.getColor(R.styleable.CircularProgressView_cpv_trackColor, defaultTrackColor)
        
        animDuration = typedArray.getInt(R.styleable.CircularProgressView_cpv_animDuration, 800).toLong()
        
        showCenterText = typedArray.getBoolean(R.styleable.CircularProgressView_cpv_showCenterText, false)
        centerTextSize = typedArray.getDimension(R.styleable.CircularProgressView_cpv_centerTextSize, 24f * density)
        centerTextColor = typedArray.getColor(R.styleable.CircularProgressView_cpv_centerTextColor, defaultTextColor)

        typedArray.recycle()

        initPaints()
    }

    private fun initPaints() {
        trackPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = this@CircularProgressView.strokeWidth
            color = trackColor
            strokeCap = Paint.Cap.ROUND
        }

        progressPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = this@CircularProgressView.strokeWidth
            strokeCap = Paint.Cap.ROUND
        }

        textPaint.apply {
            color = centerTextColor
            textSize = centerTextSize
            textAlign = Paint.Align.CENTER
            // Center vertically
            // We'll calculate offset in onDraw
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = strokeWidth / 2f
        rectF.set(padding, padding, w - padding, h - padding)
        updateGradient()
    }

    private fun updateGradient() {
        // Gradient needs to follow the arc.
        // SweepGradient starts at 3 o'clock (0 degrees). We start drawing at -90 (12 o'clock).
        // To make gradient start at -90, we need to rotate it.
        // Also, if progress < 360, we might want the gradient to span the full circle or just the progress?
        // Usually for progress views, it spans the full circle or the active arc.
        // Let's make it span the full circle for smooth transition effect.
        
        gradient = SweepGradient(width / 2f, height / 2f, intArrayOf(progressStartColor, progressEndColor), null)
        
        // Rotate gradient to start at -90 degrees
        gradientMatrix.setRotate(-90f, width / 2f, height / 2f)
        gradient?.setLocalMatrix(gradientMatrix)
        
        progressPaint.shader = gradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw track
        canvas.drawOval(rectF, trackPaint)

        // Draw progress
        val sweepAngle = 360f * (currentProgress / maxProgress)
        canvas.drawArc(rectF, -90f, sweepAngle, false, progressPaint)

        // Draw text
        if (showCenterText) {
            val text = "${currentProgress.toInt()}"
            val x = width / 2f
            // Centering vertically
            val fontMetrics = textPaint.fontMetrics
            val y = height / 2f - (fontMetrics.descent + fontMetrics.ascent) / 2f
            canvas.drawText(text, x, y, textPaint)
        }
    }

    fun setProgress(progress: Float, animate: Boolean = true) {
        val targetProgress = progress.coerceIn(0f, maxProgress)
        
        if (animate) {
            val animator = ValueAnimator.ofFloat(currentProgress, targetProgress)
            animator.duration = animDuration
            animator.interpolator = FastOutSlowInInterpolator()
            animator.addUpdateListener { animation ->
                currentProgress = animation.animatedValue as Float
                invalidate()
            }
            animator.start()
        } else {
            currentProgress = targetProgress
            invalidate()
        }
        this.progress = targetProgress
    }

    fun setMaxProgress(max: Float) {
        this.maxProgress = max
        invalidate()
    }
}

