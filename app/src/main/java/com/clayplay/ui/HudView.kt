package com.clayplay.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * The only piece of 2D UI.
 *
 * Drawn by hand rather than composed from widgets so it can inherit the clay
 * look: soft shadows, warm ink, generous radii. The middle of the screen is
 * left untouched so taps fall through to the GL surface underneath.
 */
class HudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8352A22")
        textSize = 22f * density
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A83A3128")
        textSize = 13.5f * density
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3B3026")
        textSize = 14f * density
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8FBF3E6")
        style = Paint.Style.FILL
    }

    private val pillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A2E2418")
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }

    private val togglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D9FBF3E6")
        style = Paint.Style.FILL
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C43A2E22")
        style = Paint.Style.FILL
        strokeWidth = 1.6f * density
    }

    private val pressRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333A2E22")
        style = Paint.Style.FILL
    }

    private val rect = RectF()
    private val path = Path()

    private var selectedName: String? = null

    /** Which toggle the current gesture started on, if any. -1 = none. */
    private var claimedButton = -1

    /** True while a button is held down, used only to draw press feedback. */
    private var isPressed = false

    var audioOn = true
        set(v) { field = v; invalidate() }

    var hapticsOn = true
        set(v) { field = v; invalidate() }

    var onToggleAudio: (() -> Unit)? = null
    var onToggleHaptics: (() -> Unit)? = null

    fun setSelectedToy(name: String?) {
        if (selectedName == name) return
        selectedName = name
        invalidate()
    }

    private fun buttonSize() = 40f * density

    private fun audioButtonRect(): RectF {
        val size = buttonSize()
        val margin = 16f * density
        return RectF(width - margin - size, margin, width - margin, margin + size)
    }

    private fun hapticsButtonRect(): RectF {
        val size = buttonSize()
        val margin = 16f * density
        val right = width - margin - size - 10f * density
        return RectF(right - size, margin, right, margin + size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val pad = 20f * density

        canvas.drawText("黏土小屋", pad, pad + titlePaint.textSize * 0.85f, titlePaint)

        val hint = if (selectedName == null) "轻点黏土玩具，捏一捏" else "捏一捏 · 左右滑动环视"
        canvas.drawText(hint, pad, pad + titlePaint.textSize * 1.80f, hintPaint)

        selectedName?.let { name ->
            val w = labelPaint.measureText(name) + 34f * density
            val h = 34f * density
            val cx = width * 0.5f
            val bottom = height - 30f * density
            rect.set(cx - w / 2f, bottom - h, cx + w / 2f, bottom)

            canvas.drawRoundRect(rect, h / 2f, h / 2f, pillPaint)
            canvas.drawRoundRect(rect, h / 2f, h / 2f, pillStroke)
            val baseline = rect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f
            canvas.drawText(name, rect.centerX(), baseline, labelPaint)
        }

        drawToggle(
            canvas, audioButtonRect(), audioOn, isAudio = true,
            pressed = isPressed && claimedButton == 0
        )
        drawToggle(
            canvas, hapticsButtonRect(), hapticsOn, isAudio = false,
            pressed = isPressed && claimedButton == 1
        )
    }

    private fun drawToggle(
        canvas: Canvas,
        r: RectF,
        on: Boolean,
        isAudio: Boolean,
        pressed: Boolean
    ) {
        val radius = min(r.width(), r.height()) / 2f
        canvas.drawCircle(r.centerX(), r.centerY(), radius, togglePaint)

        // Press feedback: a soft ring, so a held button reads as held.
        if (pressed) {
            canvas.drawCircle(r.centerX(), r.centerY(), radius, pressRingPaint)
        }

        val cx = r.centerX()
        val cy = r.centerY()
        val s = radius * 0.46f

        val alpha = if (on) 0xC4 else 0x40
        iconPaint.color = Color.argb(alpha, 0x3A, 0x2E, 0x22)
        iconPaint.style = Paint.Style.FILL

        if (isAudio) {
            path.reset()
            path.moveTo(cx - s, cy - s * 0.36f)
            path.lineTo(cx - s * 0.34f, cy - s * 0.36f)
            path.lineTo(cx + s * 0.28f, cy - s * 0.95f)
            path.lineTo(cx + s * 0.28f, cy + s * 0.95f)
            path.lineTo(cx - s * 0.34f, cy + s * 0.36f)
            path.lineTo(cx - s, cy + s * 0.36f)
            path.close()
            canvas.drawPath(path, iconPaint)

            iconPaint.style = Paint.Style.STROKE
            iconPaint.strokeCap = Paint.Cap.ROUND
            if (on) {
                for (i in 1..2) {
                    val rr = s * (0.52f + i * 0.44f)
                    canvas.drawArc(
                        cx + s * 0.22f - rr, cy - rr,
                        cx + s * 0.22f + rr, cy + rr,
                        -50f, 100f, false, iconPaint
                    )
                }
            } else {
                canvas.drawLine(
                    cx + s * 0.50f, cy - s * 0.52f,
                    cx + s * 1.10f, cy + s * 0.52f,
                    iconPaint
                )
            }
        } else {
            rect.set(cx - s * 0.52f, cy - s * 0.95f, cx + s * 0.52f, cy + s * 0.95f)
            canvas.drawRoundRect(rect, s * 0.22f, s * 0.22f, iconPaint)

            iconPaint.style = Paint.Style.STROKE
            iconPaint.strokeCap = Paint.Cap.ROUND
            if (on) {
                for (i in 1..2) {
                    val off = s * (1.00f + i * 0.40f)
                    canvas.drawLine(cx - off, cy - s * 0.40f, cx - off, cy + s * 0.40f, iconPaint)
                    canvas.drawLine(cx + off, cy - s * 0.40f, cx + off, cy + s * 0.40f, iconPaint)
                }
            } else {
                canvas.drawLine(
                    cx - s * 1.30f, cy + s * 1.00f,
                    cx + s * 1.30f, cy - s * 1.00f,
                    iconPaint
                )
            }
        }
        iconPaint.style = Paint.Style.FILL
    }

    /** 0 = audio toggle, 1 = haptics toggle, -1 = neither. */
    private fun buttonAt(x: Float, y: Float): Int {
        if (audioButtonRect().contains(x, y)) return 0
        if (hapticsButtonRect().contains(x, y)) return 1
        return -1
    }

    /**
     * The HUD is a full-screen transparent overlay, so it must only claim
     * touches that actually land on one of its buttons. Returning false for a
     * DOWN lets the event fall through to the GLSurfaceView underneath, which
     * is what makes poking a toy possible at all.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                claimedButton = buttonAt(event.x, event.y)
                if (claimedButton < 0) return false
                isPressed = true
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (claimedButton < 0) return false
                // Cancel the press if the finger slides off the button.
                val stillInside = buttonAt(event.x, event.y) == claimedButton
                if (isPressed != stillInside) {
                    isPressed = stillInside
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (claimedButton < 0) return false
                val inside = buttonAt(event.x, event.y) == claimedButton
                if (inside) {
                    if (claimedButton == 0) onToggleAudio?.invoke() else onToggleHaptics?.invoke()
                }
                isPressed = false
                claimedButton = -1
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                val claimed = claimedButton >= 0
                isPressed = false
                claimedButton = -1
                invalidate()
                return claimed
            }
        }
        return false
    }
}
