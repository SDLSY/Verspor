package com.example.newstart.ui.relax

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.newstart.intervention.ZenInteractionMode
import kotlin.math.PI
import kotlin.math.sin

class ZenInteractionCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Ripple(
        val x: Float,
        val y: Float,
        val strength: Float,
        val createdAt: Long
    )

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D7F2FF")
        alpha = 228
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
        color = Color.WHITE
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
        color = Color.parseColor("#8DE8FF")
        alpha = 190
    }
    private val wavePath = Path()
    private val erasePoints = mutableListOf<Pair<Float, Float>>()
    private val ripples = mutableListOf<Ripple>()
    private val touchedCells = mutableSetOf<Int>()

    private var mode: ZenInteractionMode = ZenInteractionMode.WAVE_GARDEN
    private var interactionEnabled = false
    private var interactionProgress = 0f
    private var touchCount = 0
    private var onInteractionChanged: ((Float, Int) -> Unit)? = null
    private var driftPhase = 0f
    private var lastFrameAt = 0L
    private var waveEnergy = BASE_WAVE_ENERGY

    fun configure(mode: ZenInteractionMode) {
        if (this.mode == mode) return
        this.mode = mode
        resetInteraction()
    }

    fun setInteractionEnabled(enabled: Boolean) {
        interactionEnabled = enabled
        invalidate()
    }

    fun resetInteraction() {
        erasePoints.clear()
        ripples.clear()
        touchedCells.clear()
        interactionProgress = 0f
        touchCount = 0
        waveEnergy = BASE_WAVE_ENERGY
        lastFrameAt = 0L
        invalidate()
    }

    fun getInteractionProgress(): Float = interactionProgress.coerceIn(0f, 1f)

    fun getTouchCount(): Int = touchCount

    fun setOnInteractionChangedListener(listener: ((Float, Int) -> Unit)?) {
        onInteractionChanged = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactionEnabled) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    registerTouch(event.getX(index), event.getY(index))
                }
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas)
        if (mode == ZenInteractionMode.MIST_ERASE) {
            drawMist(canvas)
        } else {
            drawWaves(canvas)
        }
        if (interactionEnabled || mode == ZenInteractionMode.WAVE_GARDEN) {
            postInvalidateOnAnimation()
        }
    }

    private fun registerTouch(x: Float, y: Float) {
        touchCount += 1
        when (mode) {
            ZenInteractionMode.MIST_ERASE -> {
                erasePoints += x to y
                markGridTouched(x, y)
                interactionProgress = touchedCells.size / (GRID_COLUMNS * GRID_ROWS).toFloat()
            }
            ZenInteractionMode.WAVE_GARDEN -> {
                ripples += Ripple(x, y, 0.85f, System.currentTimeMillis())
                if (ripples.size > 14) {
                    ripples.removeAt(0)
                }
                waveEnergy = (waveEnergy + 0.24f).coerceAtMost(1f)
                interactionProgress = (interactionProgress + 0.03f).coerceAtMost(1f)
            }
        }
        onInteractionChanged?.invoke(getInteractionProgress(), touchCount)
    }

    private fun drawBackground(canvas: Canvas) {
        val topColor = if (mode == ZenInteractionMode.MIST_ERASE) "#0E7DB3" else "#0A618A"
        val bottomColor = if (mode == ZenInteractionMode.MIST_ERASE) "#7AD2F1" else "#1BA0C8"
        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            Color.parseColor(topColor),
            Color.parseColor(bottomColor),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), dp(28f), dp(28f), backgroundPaint)
    }

    private fun drawMist(canvas: Canvas) {
        val checkpoint = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), dp(28f), dp(28f), fogPaint)
        erasePoints.forEach { (x, y) ->
            canvas.drawCircle(x, y, width * 0.10f, clearPaint)
        }
        canvas.restoreToCount(checkpoint)
    }

    private fun drawWaves(canvas: Canvas) {
        val now = System.currentTimeMillis()
        val deltaSec = ((now - lastFrameAt).coerceAtLeast(16L) / 1000f).coerceAtMost(0.05f)
        lastFrameAt = now
        driftPhase += deltaSec * 0.55f
        waveEnergy += (BASE_WAVE_ENERGY - waveEnergy) * (deltaSec * 0.9f).coerceAtMost(1f)
        repeat(6) { lineIndex ->
            wavePath.reset()
            val baseY = height * (0.24f + lineIndex * 0.11f)
            val baseAmplitude = height * (0.0035f + waveEnergy * 0.012f)
            val interactionAmplitude = height * (interactionProgress * 0.008f)
            for (x in 0..width step 18) {
                val normalizedX = x / width.toFloat()
                val driftOffset = sin(
                    (normalizedX * 2f * PI).toFloat() * 0.9f +
                        driftPhase +
                        lineIndex * 0.18f
                ) * baseAmplitude
                val rippleOffset = ripples.sumOf { ripple ->
                    val age = ((now - ripple.createdAt).coerceAtLeast(0L) / 2600f).coerceIn(0f, 1.6f)
                    val distanceWeight = (1f - kotlin.math.abs(ripple.x - x) / width.toFloat()).coerceIn(0f, 1f)
                    val damping = (1f - (age / 1.6f)).coerceIn(0f, 1f)
                    (
                        sin((normalizedX * 2f * PI).toFloat() * 1.35f + age * 3.2f + lineIndex * 0.22f) *
                            ripple.strength *
                            distanceWeight *
                            damping
                        ).toDouble()
                }.toFloat()
                val interactionWave = sin(
                    (normalizedX * 2f * PI).toFloat() * 1.1f +
                        driftPhase * 0.8f +
                        lineIndex * 0.14f
                ) * interactionAmplitude
                val y = baseY + driftOffset + interactionWave + rippleOffset * dp(4.5f)
                if (x == 0) {
                    wavePath.moveTo(x.toFloat(), y)
                } else {
                    wavePath.lineTo(x.toFloat(), y)
                }
            }
            canvas.drawPath(wavePath, wavePaint)
            canvas.drawPath(wavePath, accentPaint)
        }
        ripples.removeAll { now - it.createdAt > 3600L }
    }

    private fun markGridTouched(x: Float, y: Float) {
        val cellWidth = width / GRID_COLUMNS.toFloat()
        val cellHeight = height / GRID_ROWS.toFloat()
        val centerColumn = (x / cellWidth).toInt().coerceIn(0, GRID_COLUMNS - 1)
        val centerRow = (y / cellHeight).toInt().coerceIn(0, GRID_ROWS - 1)
        for (row in (centerRow - 1)..(centerRow + 1)) {
            for (column in (centerColumn - 1)..(centerColumn + 1)) {
                if (row in 0 until GRID_ROWS && column in 0 until GRID_COLUMNS) {
                    touchedCells += row * GRID_COLUMNS + column
                }
            }
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private const val BASE_WAVE_ENERGY = 0.08f
        private const val GRID_COLUMNS = 18
        private const val GRID_ROWS = 12
    }
}
