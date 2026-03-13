package com.example.newstart.ui.relax

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.withSave
import com.example.newstart.core.common.R
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class MedicalAvatarStageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface Callback {
        fun onZoneSelected(zone: HumanBody3DView.BodyZone, source: HumanBody3DView.ZonePickSource)
        fun onRendererUnavailable() {}
        fun onStageReady() {}
        fun onFocusStart(zone: HumanBody3DView.BodyZone) {}
        fun onFocusEnd(zone: HumanBody3DView.BodyZone) {}
    }

    private data class ZonePreset(
        val scale: Float,
        val offsetXRatio: Float,
        val offsetYRatio: Float,
        val holdMs: Long
    )

    private enum class FocusAnimState {
        IDLE,
        FOCUSING,
        HOLDING,
        RETURNING
    }

    private data class Hotspot(
        val zone: HumanBody3DView.BodyZone,
        val label: String,
        val xOffsetRatio: Float,
        val yOffsetRatio: Float
    )

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val doctorCoatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FDFEFF") }
    private val coatShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C9E2F5") }
    private val coatHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyGlassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val skinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFEADB") }
    private val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8B96AF") }
    private val tiePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8ED7F8") }
    private val collarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EFF8FF") }
    private val sleevePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EEF8FF") }
    private val floorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f)
        color = Color.parseColor("#77AECF")
    }

    private val organPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val organStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f)
        color = Color.parseColor("#4A97BD")
    }
    private val organGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hotspotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F7FDFF") }
    private val hotspotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
        color = Color.parseColor("#5AAED7")
    }
    private val hotspotTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D5D86")
        textSize = dp(10.5f)
        textAlign = Paint.Align.CENTER
    }
    private val selectedGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#66AEE4FF")
    }
    private val selectedRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = Color.parseColor("#3AA8DE")
    }
    private val selectedLabelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E9F7FF") }
    private val selectedLabelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0B507A")
        textSize = dp(13.5f)
        textAlign = Paint.Align.CENTER
    }
    private val doctorBodyBounds = RectF()
    private val fallbackBodyBounds = RectF()

    private val heartPath = Path()
    private val intestinePath = Path()
    private val ambientBubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ambientHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private data class AmbientBubble(
        val xRatio: Float,
        val yRatio: Float,
        val radiusDp: Float,
        val alpha: Int,
        val phaseOffset: Float
    )

    private val ambientBubbles = listOf(
        AmbientBubble(0.18f, 0.22f, 24f, 90, 0.12f),
        AmbientBubble(0.82f, 0.26f, 30f, 80, 0.34f),
        AmbientBubble(0.24f, 0.70f, 18f, 70, 0.52f),
        AmbientBubble(0.76f, 0.74f, 26f, 82, 0.67f),
        AmbientBubble(0.50f, 0.86f, 34f, 64, 0.84f)
    )

    private val hotspots = listOf(
        Hotspot(HumanBody3DView.BodyZone.HEAD, "Head", 0.50f, 0.17f),
        Hotspot(HumanBody3DView.BodyZone.NECK, "Neck", 0.50f, 0.29f),
        Hotspot(HumanBody3DView.BodyZone.CHEST, "Chest", 0.50f, 0.42f),
        Hotspot(HumanBody3DView.BodyZone.ABDOMEN, "Abdomen", 0.50f, 0.56f),
        Hotspot(HumanBody3DView.BodyZone.LEFT_ARM, "L-Arm", 0.25f, 0.43f),
        Hotspot(HumanBody3DView.BodyZone.RIGHT_ARM, "R-Arm", 0.75f, 0.43f),
        Hotspot(HumanBody3DView.BodyZone.LEFT_LEG, "L-Leg", 0.43f, 0.81f),
        Hotspot(HumanBody3DView.BodyZone.RIGHT_LEG, "R-Leg", 0.57f, 0.81f),
        Hotspot(HumanBody3DView.BodyZone.UPPER_BACK, "U-Back", 0.34f, 0.36f),
        Hotspot(HumanBody3DView.BodyZone.LOWER_BACK, "L-Back", 0.66f, 0.62f)
    )

    private val zonePresets = mapOf(
        HumanBody3DView.BodyZone.HEAD to ZonePreset(1.34f, 0f, 0.18f, 1100L),
        HumanBody3DView.BodyZone.NECK to ZonePreset(1.30f, 0f, 0.11f, 1050L),
        HumanBody3DView.BodyZone.CHEST to ZonePreset(1.26f, 0f, 0.03f, 1150L),
        HumanBody3DView.BodyZone.UPPER_BACK to ZonePreset(1.24f, -0.04f, 0f, 1150L),
        HumanBody3DView.BodyZone.ABDOMEN to ZonePreset(1.26f, 0f, -0.05f, 1200L),
        HumanBody3DView.BodyZone.LOWER_BACK to ZonePreset(1.22f, 0.03f, -0.10f, 1200L),
        HumanBody3DView.BodyZone.LEFT_ARM to ZonePreset(1.22f, 0.11f, -0.01f, 1100L),
        HumanBody3DView.BodyZone.RIGHT_ARM to ZonePreset(1.22f, -0.11f, -0.01f, 1100L),
        HumanBody3DView.BodyZone.LEFT_LEG to ZonePreset(1.21f, 0.07f, -0.17f, 1100L),
        HumanBody3DView.BodyZone.RIGHT_LEG to ZonePreset(1.21f, -0.07f, -0.17f, 1100L)
    )

    private var callback: Callback? = null
    private var selectedZone: HumanBody3DView.BodyZone? = null
    private var focusState = FocusAnimState.IDLE
    private var focusProgress = 0f
    private var currentPreset = zonePresets[HumanBody3DView.BodyZone.CHEST] ?: ZonePreset(1f, 0f, 0f, 0)
    private var focusAnimator: ValueAnimator? = null
    private var holdRunnable: Runnable? = null
    private var stageReadyNotified = false
    private var interactionLocked = false
    private var ambientAnimator: ValueAnimator? = null
    private var ambientPhase = 0f
    private var lastFocusSource = HumanBody3DView.ZonePickSource.BUTTON

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    @Suppress("UNUSED_PARAMETER")
    fun focusZone(zone: HumanBody3DView.BodyZone, useProjectedAnchor: Boolean = false) {
        focusZoneCinematic(zone, HumanBody3DView.ZonePickSource.BUTTON)
    }

    fun focusZoneCinematic(zone: HumanBody3DView.BodyZone, source: HumanBody3DView.ZonePickSource) {
        selectedZone = zone
        lastFocusSource = source
        currentPreset = zonePresets[zone] ?: ZonePreset(1f, 0f, 0f, 0)
        startFocusAnimation(zone)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAmbientAnimationState()
        if (!stageReadyNotified) {
            stageReadyNotified = true
            post { callback?.onStageReady() }
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAmbientAnimationState()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        holdRunnable?.let { removeCallbacks(it) }
        holdRunnable = null
        focusAnimator?.cancel()
        focusAnimator = null
        stopAmbientAnimation()
        interactionLocked = false
    }

    private fun updateAmbientAnimationState() {
        if (visibility == View.VISIBLE) {
            startAmbientAnimation()
        } else {
            stopAmbientAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP || interactionLocked) {
            return true
        }
        val hotspot = findTouchedHotspot(event.x, event.y) ?: return true
        selectedZone = hotspot.zone
        startFocusAnimation(hotspot.zone)
        callback?.onZoneSelected(hotspot.zone, HumanBody3DView.ZonePickSource.RAY_PICK)
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas)

        canvas.withSave {
            val idleRock = if (focusState == FocusAnimState.IDLE) {
                sin(ambientPhase * TWO_PI) * 1.8f
            } else {
                0f
            }
            rotate(idleRock, width * 0.5f, height * 0.56f)
            applyFocusTransform(this)
            drawDoctorBody(this)
            drawOrgans(this)
            drawHotspots(this)
        }

        drawSelectedLabel(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        val top = Color.parseColor("#E8F6FF")
        val bottom = Color.parseColor("#B8DCF6")
        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            top,
            bottom,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        ambientBubblePaint.color = Color.parseColor("#94C4E7")
        val leftAccent = Path().apply {
            moveTo(0f, height * 0.60f)
            lineTo(width * 0.35f, height * 0.49f)
            lineTo(width * 0.47f, height.toFloat())
            lineTo(0f, height.toFloat())
            close()
        }
        canvas.drawPath(leftAccent, ambientBubblePaint)

        ambientBubblePaint.color = Color.parseColor("#7CB5E1")
        val rightAccent = Path().apply {
            moveTo(width * 0.58f, height * 0.43f)
            lineTo(width.toFloat(), height * 0.26f)
            lineTo(width.toFloat(), height.toFloat())
            lineTo(width * 0.48f, height.toFloat())
            close()
        }
        canvas.drawPath(rightAccent, ambientBubblePaint)

        ambientBubbles.forEach { bubble ->
            val drift = sin((ambientPhase + bubble.phaseOffset) * TWO_PI) * dp(7f)
            val radius = dp(bubble.radiusDp) * (0.95f + 0.07f * sin((ambientPhase + bubble.phaseOffset) * TWO_PI))
            ambientBubblePaint.color = Color.argb((bubble.alpha * 0.72f).toInt(), 113, 186, 232)
            canvas.drawCircle(
                width * bubble.xRatio,
                (height * bubble.yRatio) + drift,
                radius,
                ambientBubblePaint
            )
        }

        val highlightWidth = width * 0.86f
        val highlightHeight = height * 0.24f
        val highlightTop = height * 0.12f + sin(ambientPhase * TWO_PI) * dp(3f)
        ambientHighlightPaint.shader = LinearGradient(
            width * 0.08f,
            highlightTop,
            width * 0.92f,
            highlightTop + highlightHeight,
            Color.argb(132, 144, 210, 246),
            Color.argb(18, 144, 210, 246),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            RectF(
                (width - highlightWidth) * 0.5f,
                highlightTop,
                (width + highlightWidth) * 0.5f,
                highlightTop + highlightHeight
            ),
            dp(26f),
            dp(26f),
            ambientHighlightPaint
        )

        floorShadowPaint.shader = RadialGradient(
            width * 0.5f,
            height * 0.90f,
            width * 0.30f,
            Color.argb(48, 96, 156, 198),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawOval(
            RectF(
                width * 0.20f,
                height * 0.82f,
                width * 0.80f,
                height * 0.98f
            ),
            floorShadowPaint
        )
    }

    private fun drawDoctorBody(canvas: Canvas) {
        val cx = width * 0.5f
        val stageMin = min(width, height).toFloat()
        val bodyTop = height * 0.20f
        val bodyBottom = height * 0.95f
        val coatWidth = stageMin * 0.60f
        val headRadius = stageMin * 0.112f
        val headCenterY = height * 0.14f
        val coatRect = RectF(cx - coatWidth / 2f, bodyTop, cx + coatWidth / 2f, bodyBottom)

        canvas.drawRoundRect(
            RectF(
                coatRect.left + dp(8f),
                coatRect.top + dp(10f),
                coatRect.right + dp(8f),
                coatRect.bottom + dp(10f)
            ),
            dp(46f),
            dp(46f),
            coatShadowPaint
        )

        doctorCoatPaint.shader = LinearGradient(
            coatRect.left,
            coatRect.top,
            coatRect.right,
            coatRect.bottom,
            Color.parseColor("#FEFFFF"),
            Color.parseColor("#EAF7FF"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(coatRect, dp(46f), dp(46f), doctorCoatPaint)
        canvas.drawRoundRect(coatRect, dp(46f), dp(46f), strokePaint)

        coatHighlightPaint.shader = LinearGradient(
            coatRect.left,
            coatRect.top,
            coatRect.left + coatRect.width() * 0.70f,
            coatRect.bottom,
            Color.argb(114, 255, 255, 255),
            Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            RectF(
                coatRect.left + dp(14f),
                coatRect.top + dp(16f),
                coatRect.left + coatRect.width() * 0.62f,
                coatRect.bottom - dp(28f)
            ),
            dp(30f),
            dp(30f),
            coatHighlightPaint
        )

        canvas.drawRoundRect(
            RectF(coatRect.left - dp(42f), coatRect.top + dp(74f), coatRect.left + dp(10f), coatRect.top + dp(248f)),
            dp(26f),
            dp(26f),
            sleevePaint
        )
        canvas.drawRoundRect(
            RectF(coatRect.right - dp(10f), coatRect.top + dp(74f), coatRect.right + dp(42f), coatRect.top + dp(248f)),
            dp(26f),
            dp(26f),
            sleevePaint
        )

        canvas.drawCircle(cx, headCenterY, headRadius, skinPaint)
        val hairPath = Path().apply {
            addRoundRect(
                RectF(
                    cx - headRadius * 1.02f,
                    headCenterY - headRadius * 1.20f,
                    cx + headRadius * 1.02f,
                    headCenterY + headRadius * 0.18f
                ),
                headRadius * 0.62f,
                headRadius * 0.62f,
                Path.Direction.CW
            )
        }
        canvas.drawPath(hairPath, hairPaint)
        canvas.drawCircle(cx - headRadius * 0.33f, headCenterY - headRadius * 0.02f, dp(3.4f), ColorPaints.eyePaint)
        canvas.drawCircle(cx + headRadius * 0.33f, headCenterY - headRadius * 0.02f, dp(3.4f), ColorPaints.eyePaint)
        canvas.drawRoundRect(
            RectF(cx - dp(14f), headCenterY + dp(18f), cx + dp(14f), headCenterY + dp(21f)),
            dp(1.5f),
            dp(1.5f),
            ColorPaints.mouthPaint
        )

        val collar = Path().apply {
            moveTo(cx - dp(62f), bodyTop + dp(10f))
            lineTo(cx - dp(9f), bodyTop + dp(74f))
            lineTo(cx + dp(9f), bodyTop + dp(74f))
            lineTo(cx + dp(62f), bodyTop + dp(10f))
            close()
        }
        canvas.drawPath(collar, collarPaint)
        canvas.drawPath(collar, strokePaint)

        bodyGlassPaint.shader = LinearGradient(
            cx,
            bodyTop + dp(62f),
            cx,
            bodyBottom - dp(138f),
            Color.argb(220, 176, 227, 248),
            Color.argb(120, 176, 227, 248),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            RectF(cx - dp(88f), bodyTop + dp(72f), cx + dp(88f), bodyBottom - dp(138f)),
            dp(50f),
            dp(50f),
            bodyGlassPaint
        )
        canvas.drawRoundRect(
            RectF(cx - dp(88f), bodyTop + dp(72f), cx + dp(88f), bodyBottom - dp(138f)),
            dp(50f),
            dp(50f),
            strokePaint
        )

        canvas.drawRoundRect(
            RectF(cx - dp(12f), bodyTop + dp(40f), cx + dp(12f), bodyTop + dp(86f)),
            dp(9f),
            dp(9f),
            tiePaint
        )
        canvas.drawRoundRect(
            RectF(cx - dp(46f), bodyTop + dp(88f), cx + dp(46f), bodyTop + dp(96f)),
            dp(4f),
            dp(4f),
            tiePaint
        )

        ambientBubblePaint.color = Color.parseColor("#F4FBFF")
        canvas.drawRoundRect(
            RectF(cx - dp(48f), bodyBottom - dp(126f), cx - dp(9f), bodyBottom),
            dp(20f),
            dp(20f),
            ambientBubblePaint
        )
        canvas.drawRoundRect(
            RectF(cx + dp(9f), bodyBottom - dp(126f), cx + dp(48f), bodyBottom),
            dp(20f),
            dp(20f),
            ambientBubblePaint
        )
        doctorBodyBounds.set(
            cx - coatWidth * 0.44f,
            headCenterY - headRadius * 0.95f,
            cx + coatWidth * 0.44f,
            bodyBottom
        )
    }

    private fun drawOrgans(canvas: Canvas) {
        val body = effectiveBodyBounds()
        val cx = body.centerX()
        val bodyW = body.width()
        val bodyH = body.height()

        drawOrganOval(
            canvas,
            cx,
            body.top + bodyH * 0.16f,
            bodyW * 0.16f,
            bodyH * 0.055f,
            "#8CE4C0",
            HumanBody3DView.BodyZone.HEAD
        )
        drawOrganOval(
            canvas,
            cx,
            body.top + bodyH * 0.29f,
            bodyW * 0.075f,
            bodyH * 0.032f,
            "#77D8F8",
            HumanBody3DView.BodyZone.NECK
        )

        val lungY = body.top + bodyH * 0.44f
        drawOrganOval(canvas, cx - bodyW * 0.12f, lungY, bodyW * 0.16f, bodyH * 0.10f, "#79E0BE", HumanBody3DView.BodyZone.CHEST)
        drawOrganOval(canvas, cx + bodyW * 0.12f, lungY, bodyW * 0.16f, bodyH * 0.10f, "#79E0BE", HumanBody3DView.BodyZone.CHEST)
        drawHeart(canvas, cx, body.top + bodyH * 0.462f, HumanBody3DView.BodyZone.CHEST, bodyW)

        drawOrganOval(canvas, cx - bodyW * 0.09f, body.top + bodyH * 0.58f, bodyW * 0.19f, bodyH * 0.06f, "#FFCB73", HumanBody3DView.BodyZone.ABDOMEN)
        drawOrganOval(canvas, cx + bodyW * 0.14f, body.top + bodyH * 0.60f, bodyW * 0.17f, bodyH * 0.058f, "#76DEAF", HumanBody3DView.BodyZone.ABDOMEN)
        drawIntestine(canvas, cx, body.top + bodyH * 0.72f, HumanBody3DView.BodyZone.ABDOMEN, bodyW, bodyH)
        drawOrganOval(canvas, cx, body.top + bodyH * 0.81f, bodyW * 0.10f, bodyH * 0.032f, "#74D3F6", HumanBody3DView.BodyZone.LOWER_BACK)
    }

    private fun drawHeart(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        zone: HumanBody3DView.BodyZone,
        bodyWidth: Float
    ) {
        val selected = selectedZone == zone
        val color = if (selected) Color.parseColor("#FFAA3F") else Color.parseColor("#FFCA69")
        organPaint.color = color
        organPaint.alpha = if (selected) 242 else 168
        heartPath.reset()
        val pulseFactor = 1f + (if (selected) 0.06f else 0.018f) * sin((ambientPhase + zone.ordinal * 0.11f) * TWO_PI)
        val size = bodyWidth * 0.18f * pulseFactor
        heartPath.moveTo(cx, cy + size * 1.45f)
        heartPath.cubicTo(cx + size * 1.7f, cy + size * 0.2f, cx + size * 1.25f, cy - size * 1.2f, cx, cy - size * 0.3f)
        heartPath.cubicTo(cx - size * 1.25f, cy - size * 1.2f, cx - size * 1.7f, cy + size * 0.2f, cx, cy + size * 1.45f)
        heartPath.close()
        canvas.drawPath(heartPath, organPaint)
        canvas.drawPath(heartPath, organStrokePaint)
    }

    private fun drawIntestine(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        zone: HumanBody3DView.BodyZone,
        bodyWidth: Float,
        bodyHeight: Float
    ) {
        val selected = selectedZone == zone
        organPaint.color = if (selected) Color.parseColor("#6BCFF4") else Color.parseColor("#9ADFFB")
        organPaint.alpha = if (selected) 236 else 164

        val pulseFactor = 1f + (if (selected) 0.05f else 0.012f) * sin((ambientPhase + zone.ordinal * 0.08f) * TWO_PI)
        val w = bodyWidth * 0.30f * pulseFactor
        val h = bodyHeight * 0.050f * pulseFactor
        intestinePath.reset()
        intestinePath.addRoundRect(RectF(cx - w, cy - h, cx + w, cy + h), dp(18f), dp(18f), Path.Direction.CW)
        canvas.drawPath(intestinePath, organPaint)
        canvas.drawPath(intestinePath, organStrokePaint)
    }

    private fun drawOrganOval(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        colorHex: String,
        zone: HumanBody3DView.BodyZone
    ) {
        val selected = selectedZone == zone
        val pulseFactor = 1f + (if (selected) 0.06f else 0.014f) * sin((ambientPhase + zone.ordinal * 0.11f) * TWO_PI)
        val rect = RectF(cx - rx * pulseFactor, cy - ry * pulseFactor, cx + rx * pulseFactor, cy + ry * pulseFactor)
        organGlowPaint.shader = RadialGradient(
            cx,
            cy,
            rect.width() * 0.74f,
            Color.argb(if (selected) 132 else 66, 167, 231, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawOval(
            RectF(
                rect.left - dp(8f),
                rect.top - dp(8f),
                rect.right + dp(8f),
                rect.bottom + dp(8f)
            ),
            organGlowPaint
        )
        organPaint.color = Color.parseColor(colorHex)
        organPaint.alpha = if (selected) 236 else 166
        canvas.drawOval(rect, organPaint)
        canvas.drawOval(rect, organStrokePaint)
    }

    private fun drawHotspots(canvas: Canvas) {
        val bodyWidth = effectiveBodyBounds().width()
        val radius = max(dp(14f), bodyWidth * 0.04f)
        hotspots.forEach { hotspot ->
            val p = hotspotPoint(hotspot)
            val selected = selectedZone == hotspot.zone
            if (selected) {
                val pulse = 1f + 0.14f * sin((ambientPhase + hotspot.zone.ordinal * 0.1f) * TWO_PI)
                canvas.drawCircle(p.x, p.y, radius * (1.60f + 0.08f * pulse), selectedGlowPaint)
                selectedRingPaint.alpha = if (lastFocusSource == HumanBody3DView.ZonePickSource.RAY_PICK) 220 else 180
                canvas.drawCircle(p.x, p.y, radius * (1.20f + 0.08f * pulse), selectedRingPaint)
            }
            hotspotFillPaint.alpha = if (selected) 255 else 136
            canvas.drawCircle(p.x, p.y, radius, hotspotFillPaint)
            hotspotStrokePaint.color = if (selected) Color.parseColor("#33AFDF") else Color.parseColor("#97D3EE")
            canvas.drawCircle(p.x, p.y, radius, hotspotStrokePaint)
            hotspotTextPaint.alpha = if (selected) 255 else 208
            hotspotTextPaint.textSize = if (selected) radius * 0.45f else radius * 0.40f
            canvas.drawText(hotspot.label, p.x, p.y + radius * 0.18f, hotspotTextPaint)
        }
    }

    private fun drawSelectedLabel(canvas: Canvas) {
        val zone = selectedZone ?: return
        val hotspot = hotspots.firstOrNull { it.zone == zone } ?: return
        val p = hotspotPoint(hotspot)
        val text = zone.defaultLabel()
        val textWidth = selectedLabelTextPaint.measureText(text)
        val rawRect = RectF(p.x - textWidth * 0.5f - dp(14f), p.y - dp(52f), p.x + textWidth * 0.5f + dp(14f), p.y - dp(18f))
        val dx = when {
            rawRect.left < dp(8f) -> dp(8f) - rawRect.left
            rawRect.right > width - dp(8f) -> (width - dp(8f)) - rawRect.right
            else -> 0f
        }
        val dy = if (rawRect.top < dp(8f)) dp(8f) - rawRect.top else 0f
        val rect = RectF(rawRect.left + dx, rawRect.top + dy, rawRect.right + dx, rawRect.bottom + dy)
        selectedLabelBgPaint.color = Color.parseColor("#F2FBFF")
        canvas.drawRoundRect(rect, dp(17f), dp(17f), selectedLabelBgPaint)
        selectedRingPaint.alpha = 80
        selectedRingPaint.strokeWidth = dp(2f)
        canvas.drawRoundRect(
            RectF(rect.left - dp(2f), rect.top - dp(2f), rect.right + dp(2f), rect.bottom + dp(2f)),
            dp(19f),
            dp(19f),
            selectedRingPaint
        )
        selectedRingPaint.strokeWidth = dp(3f)
        canvas.drawRoundRect(rect, dp(14f), dp(14f), hotspotStrokePaint)
        canvas.drawText(text, rect.centerX(), rect.centerY() + dp(4.2f), selectedLabelTextPaint)
    }

    private fun applyFocusTransform(canvas: Canvas) {
        if (focusState == FocusAnimState.IDLE || selectedZone == null) {
            return
        }
        val preset = currentPreset
        val scale = lerp(1f, preset.scale, focusProgress)
        val tx = width * preset.offsetXRatio * focusProgress
        val ty = height * preset.offsetYRatio * focusProgress
        canvas.translate(tx, ty)
        canvas.scale(scale, scale, width * 0.5f, height * 0.5f)
    }

    private fun hotspotPoint(hotspot: Hotspot): PointF {
        val body = effectiveBodyBounds()
        return PointF(
            body.left + body.width() * hotspot.xOffsetRatio,
            body.top + body.height() * hotspot.yOffsetRatio
        )
    }

    private fun findTouchedHotspot(x: Float, y: Float): Hotspot? {
        val radius = dp(28f)
        var best: Hotspot? = null
        var bestDistance = Float.MAX_VALUE
        for (hotspot in hotspots) {
            val p = hotspotPoint(hotspot)
            val d = hypot(p.x - x, p.y - y)
            if (d <= radius && d < bestDistance) {
                best = hotspot
                bestDistance = d
            }
        }
        return best
    }

    private fun startFocusAnimation(zone: HumanBody3DView.BodyZone) {
        holdRunnable?.let { removeCallbacks(it) }
        holdRunnable = null
        focusAnimator?.cancel()
        focusAnimator = null

        interactionLocked = true
        callback?.onFocusStart(zone)
        focusState = FocusAnimState.FOCUSING
        val preset = currentPreset

        val focusIn = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360L
            addUpdateListener {
                focusProgress = (it.animatedValue as Float).coerceIn(0f, 1f)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    focusState = FocusAnimState.HOLDING
                    holdRunnable = Runnable {
                        focusState = FocusAnimState.RETURNING
                        val focusOut = ValueAnimator.ofFloat(1f, 0f).apply {
                            duration = 320L
                            addUpdateListener { va ->
                                focusProgress = (va.animatedValue as Float).coerceIn(0f, 1f)
                                invalidate()
                            }
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    focusState = FocusAnimState.IDLE
                                    interactionLocked = false
                                    callback?.onFocusEnd(zone)
                                }
                            })
                        }
                        focusAnimator = focusOut
                        focusOut.start()
                    }
                    postDelayed(holdRunnable, max(600L, preset.holdMs))
                }
            })
        }
        focusAnimator = focusIn
        focusIn.start()
    }

    private fun startAmbientAnimation() {
        if (ambientAnimator?.isStarted == true) return
        ambientAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                ambientPhase = (it.animatedValue as Float).coerceIn(0f, 1f)
                if (focusState == FocusAnimState.IDLE) {
                    invalidate()
                }
            }
            start()
        }
    }

    private fun stopAmbientAnimation() {
        ambientAnimator?.cancel()
        ambientAnimator = null
        ambientPhase = 0f
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private fun lerp(from: Float, to: Float, t: Float): Float {
        return from + (to - from) * t
    }

    private fun effectiveBodyBounds(): RectF {
        if (!doctorBodyBounds.isEmpty) {
            return doctorBodyBounds
        }
        fallbackBodyBounds.set(
            width * 0.32f,
            height * 0.18f,
            width * 0.68f,
            height * 0.94f
        )
        return fallbackBodyBounds
    }

    private object ColorPaints {
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#82A0B4")
            style = Paint.Style.FILL
        }
        val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9AB9CC")
            style = Paint.Style.FILL
        }
    }

    companion object {
        private const val TWO_PI = (Math.PI * 2.0).toFloat()
    }

}

