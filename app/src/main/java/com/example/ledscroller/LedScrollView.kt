package com.example.ledscroller

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

/**
 * Direction the message scrolls across the screen.
 */
enum class ScrollDirection {
    LEFT, RIGHT, STATIC_BLINK
}

/**
 * Font families available for the LED text, backed by Android's built-in
 * typeface families so no extra font assets are needed.
 */
enum class LedFontFamily(val base: Typeface, val label: String) {
    MONOSPACE(Typeface.MONOSPACE, "Monospace"),
    SANS_SERIF(Typeface.SANS_SERIF, "Sans Serif"),
    SERIF(Typeface.SERIF, "Serif"),
    DEFAULT(Typeface.DEFAULT, "Varsayılan")
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
            updateTypeface()
            invalidate()
        }

    var fontFamily: LedFontFamily = LedFontFamily.MONOSPACE
        set(value) {
            field = value
            updateTypeface()
            requestLayout()
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

    /** Renders the text as a grid of round LED-style dots instead of smooth glyphs. */
    var dotMatrix: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val glowRadius = 18f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = textSizePx
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        setShadowLayer(glowRadius, 0f, 0f, textColor)
    }

    private val dimDotPaint = Paint()
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dstInXfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    private var dotShader: BitmapShader? = null
    private var dotShaderCellPx = -1f

    private var blinkAnimator: ValueAnimator? = null

    private val flickerHandler = Handler(Looper.getMainLooper())
    private var flickerRunnable: Runnable? = null
    private var flickerAlpha = 255

    private val scrollHandler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null
    private var lastFrameTimeNs = 0L

    private var isDragging = false
    private var dragStartRawX = 0f
    private var dragStartOffsetX = 0f

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
            startScrollLoop()
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

        startFlicker()
    }

    fun stopAnimating() {
        stopScrollLoop()
        blinkAnimator?.cancel()
        blinkAnimator = null
        stopFlicker()
    }

    /** Continuous, time-based scroll loop (instead of a fixed-duration animator) so a
     *  touch-drag can pause it and resume smoothly from wherever the user left it. */
    private fun startScrollLoop() {
        stopScrollLoop()
        lastFrameTimeNs = System.nanoTime()
        val runnable = object : Runnable {
            override fun run() {
                if (!isDragging) {
                    val now = System.nanoTime()
                    val dtSeconds = (now - lastFrameTimeNs) / 1_000_000_000f
                    lastFrameTimeNs = now

                    val distance = textWidth() + width
                    val pxPerSecond = speedLevel * 24f
                    if (direction == ScrollDirection.LEFT) {
                        offsetX -= pxPerSecond * dtSeconds
                        if (offsetX < -textWidth()) offsetX += distance
                    } else {
                        offsetX += pxPerSecond * dtSeconds
                        if (offsetX > width) offsetX -= distance
                    }
                    invalidate()
                } else {
                    // Keep the clock fresh while paused so we don't jump on resume.
                    lastFrameTimeNs = System.nanoTime()
                }
                scrollHandler.postDelayed(this, FRAME_DELAY_MS)
            }
        }
        scrollRunnable = runnable
        scrollHandler.post(runnable)
    }

    private fun stopScrollLoop() {
        scrollRunnable?.let { scrollHandler.removeCallbacks(it) }
        scrollRunnable = null
    }

    /** Subtle, always-on brightness jitter so the sign feels like real, slightly imperfect LEDs. */
    private fun startFlicker() {
        stopFlicker()
        val runnable = object : Runnable {
            override fun run() {
                flickerAlpha = if (Random.nextFloat() < 0.12f) (210..240).random() else 255
                invalidate()
                flickerHandler.postDelayed(this, (70..170).random().toLong())
            }
        }
        flickerRunnable = runnable
        flickerHandler.post(runnable)
    }

    private fun stopFlicker() {
        flickerRunnable?.let { flickerHandler.removeCallbacks(it) }
        flickerRunnable = null
        flickerAlpha = 255
    }

    private fun restartAnimationIfRunning() {
        if (isAttachedToWindow) startAnimating()
    }

    private fun updateTypeface() {
        paint.typeface = Typeface.create(fontFamily.base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun textWidth(): Float = paint.measureText(text)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        restartAnimationIfRunning()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (direction == ScrollDirection.STATIC_BLINK) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                dragStartRawX = event.x
                dragStartOffsetX = offsetX
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    offsetX = dragStartOffsetX + (event.x - dragStartRawX)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(backgroundColorLed)

        paint.alpha = (currentAlpha * flickerAlpha / 255f).toInt().coerceIn(0, 255)

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

        if (dotMatrix) {
            drawDotMatrixText(canvas, drawX, baselineY)
        } else {
            drawGlowText(canvas, drawX, baselineY)
        }

        if (mirror) {
            canvas.restore()
        }
    }

    /** Draws the text twice: a wide soft "bloom" pass, then a sharper pass on top. */
    private fun drawGlowText(canvas: Canvas, x: Float, y: Float) {
        val baseAlpha = paint.alpha

        paint.setShadowLayer(glowRadius * 2.4f, 0f, 0f, textColor)
        paint.alpha = (baseAlpha * 0.45f).toInt()
        canvas.drawText(text, x, y, paint)

        paint.setShadowLayer(glowRadius, 0f, 0f, textColor)
        paint.alpha = baseAlpha
        canvas.drawText(text, x, y, paint)
    }

    /** Masks the glowing text down to a repeating grid of round dots, like a real LED panel. */
    private fun drawDotMatrixText(canvas: Canvas, x: Float, y: Float) {
        ensureDotShader()

        // Dim, always-visible dots across the whole panel (the "unlit" LEDs).
        dimDotPaint.shader = dotShader
        dimDotPaint.color = textColor
        dimDotPaint.alpha = 28
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimDotPaint)

        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        drawGlowText(canvas, x, y)
        maskPaint.shader = dotShader
        maskPaint.xfermode = dstInXfermode
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
        maskPaint.xfermode = null
        canvas.restoreToCount(layer)
    }

    private fun ensureDotShader() {
        val cellPx = (DOT_SPACING_DP * resources.displayMetrics.density).coerceAtLeast(4f)
        if (dotShader != null && dotShaderCellPx == cellPx) return
        dotShaderCellPx = cellPx

        val size = cellPx.toInt().coerceAtLeast(4)
        val tile = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val tileCanvas = Canvas(tile)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        tileCanvas.drawCircle(size / 2f, size / 2f, size * 0.38f, dotPaint)

        dotShader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private companion object {
        const val DOT_SPACING_DP = 6f
        const val FRAME_DELAY_MS = 16L
    }
}
