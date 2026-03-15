package com.example.newstart.ui.relax

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.example.newstart.intervention.RelaxRealtimeFeedback
import com.example.newstart.intervention.RelaxSignalState
import kotlin.math.sin

class BiofeedbackBreathingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
    }

    private var phaseProgress = 0f
    private var feedback = RelaxRealtimeFeedback()
    private var pulse = 0f

    fun render(progress: Int, feedback: RelaxRealtimeFeedback) {
        this.phaseProgress = progress.coerceIn(0, 100) / 100f
        this.feedback = feedback
        pulse += 0.07f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val widthF = width.toFloat()
        val heightF = height.toFloat()
        val baseColor = when (feedback.signalState) {
            RelaxSignalState.CALM -> Color.parseColor("#91D9FF")
            RelaxSignalState.STEADY -> Color.parseColor("#5CCBEA")
            RelaxSignalState.ACTIVE -> Color.parseColor("#FFAE6E")
            RelaxSignalState.FALLBACK -> Color.parseColor("#B6D9FF")
        }
        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            heightF,
            Color.argb(76, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),
            Color.argb(14, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(0f, 0f, widthF, heightF), dp(22f), dp(22f), backgroundPaint)

        val centerX = widthF * 0.5f
        val centerY = heightF * 0.52f
        val signal = feedback.relaxSignal.coerceIn(0f, 1f)
        val breathingRadius = widthF.coerceAtMost(heightF) * (0.22f + phaseProgress * 0.10f)
        val calmOffset = sin(pulse) * dp(4f)
        val glowRadius = breathingRadius * (1.7f + signal * 0.55f)
        glowPaint.shader = RadialGradient(
            centerX,
            centerY,
            glowRadius,
            Color.argb((110 + signal * 70f).toInt(), Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),
            Color.argb(0, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, glowRadius, glowPaint)

        ringPaint.color = Color.argb(180, 255, 255, 255)
        canvas.drawCircle(centerX, centerY, breathingRadius + calmOffset, ringPaint)
        ringPaint.alpha = 120
        canvas.drawCircle(centerX, centerY, breathingRadius * 1.35f, ringPaint)

        corePaint.shader = RadialGradient(
            centerX,
            centerY,
            breathingRadius,
            Color.argb(255, 255, 255, 255),
            baseColor,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, breathingRadius, corePaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
