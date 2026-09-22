package com.example.ledscroller

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Direction the message scrolls across the screen.
 */
enum class ScrollDirection {
    LEFT, RIGHT, STATIC_BLINK
}

/**
 * A simple custom View that draws large glowing text and scrolls it
 * across the canvas, similar to a classic LED / dot-matrix sign.
 *
 * Everything is drawn manually on a Canvas so we have full control over
 * speed, direction, color, blinking and the "glow" effect (via a shadow
 * layer), without needing any external libraries or fonts.
 */
class LedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var text: String = "LED SCROLLER"
        set(value) {
            field = value.ifBlank { " " }
            requestLayout()
            invalidate()
        }

    var textColor: Int = Color.parseColor("#00E676")
        set(value) {
            field = value
            paint.color = value
            paint.setShadowLayer(glowRadius, 0f, 0f, value)
            invalidate()
        }

    var backgroundColorLed: Int = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    /** Text size in pixels. */
    var textSizePx: Float = 90f
        set(value) {
            field = value
            paint.textSize = value
            requestLayout()
            invalidate()
        }

    var bold: Boolean = false
        set(value) {
            field = value
            paint.typeface = if (value) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            else Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            invalidate()
        }

    var mirror: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** 1 (slow) .. 30 (fast) */
    var speedLevel: Int = 10
        set(value) {
            field = value.coerceIn(1, 30)
            restartAnimationIfRunning()
        }

    var direction: ScrollDirection = ScrollDirection.LEFT
        set(value) {
            field = value
            restartAnimationIfRunning()
        }

    var blinkEnabled: Boolean = false

    private val glowRadius = 18f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = textSizePx
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        setShadowLayer(glowRadius, 0f, 0f, textColor)
    }

    private var scrollAnimator: ValueAnimator? = null
    private var blinkAnimator: ValueAnimator? = null

    private var offsetX = 0f
    private var currentAlpha = 255

    init {
        // Shadow layers only render with software rendering.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimating()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimating()
    }

    fun startAnimating() {
        stopAnimating()

        if (direction == ScrollDirection.STATIC_BLINK) {
            offsetX = 0f
        } else {
            // Start just off the visible edge so text enters cleanly.
            offsetX = if (direction == ScrollDirection.LEFT) width.toFloat() else -textWidth()
        }

        if (direction != ScrollDirection.STATIC_BLINK) {
            val distance = textWidth() + width
            val durationMs = (distance / (speedLevel * 6f) * 1000f).toLong().coerceAtLeast(300)

            scrollAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMs
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    val fraction = anim.animatedValue as Float
                    offsetX = if (direction == ScrollDirection.LEFT) {
                        width - fraction * distance
                    } else {
                        -textWidth() + fraction * distance
                    }
                    invalidate()
                }
                start()
            }
        }

        if (blinkEnabled) {
            blinkAnimator = ValueAnimator.ofInt(255, 40).apply {
                duration = 500
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { anim ->
                    currentAlpha = anim.animatedValue as Int
                    invalidate()
                }
                start()
            }
        } else {
            currentAlpha = 255
        }
    }

    fun stopAnimating() {
        scrollAnimator?.cancel()
        scrollAnimator = null
        blinkAnimator?.cancel()
        blinkAnimator = null
    }

    private fun restartAnimationIfRunning() {
        if (isAttachedToWindow) startAnimating()
    }

    private fun textWidth(): Float = paint.measureText(text)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        restartAnimationIfRunning()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(backgroundColorLed)

        paint.alpha = currentAlpha

        val baselineY = height / 2f - (paint.descent() + paint.ascent()) / 2f

        val drawX = if (direction == ScrollDirection.STATIC_BLINK) {
            (width - textWidth()) / 2f
        } else {
            offsetX
        }

        if (mirror) {
            canvas.save()
            canvas.scale(-1f, 1f, width / 2f, height / 2f)
        }

        canvas.drawText(text, drawX, baselineY, paint)

        if (mirror) {
            canvas.restore()
        }
    }
}
