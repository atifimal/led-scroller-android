package com.example.ledscroller

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration

/**
 * Detects three quick taps in roughly the same spot, using the same system
 * timeouts and slops as the platform's double-tap detection. Slow taps,
 * long presses, drags and multi-finger touches reset the count.
 *
 * Feed it every event (e.g. from Activity.dispatchTouchEvent); it never
 * consumes events, so it does not interfere with the views underneath.
 */
class TripleTapDetector(context: Context, private val onTripleTap: () -> Unit) {

    private val config = ViewConfiguration.get(context)
    private val touchSlopSquare = config.scaledTouchSlop.let { it * it }
    private val doubleTapSlopSquare = config.scaledDoubleTapSlop.let { it * it }
    private val tapTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val betweenTapsTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

    private var tapCount = 0
    private var firstDownX = 0f
    private var firstDownY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastUpTime = 0L
    private var isTapCandidate = false

    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val continuesSequence = tapCount > 0 &&
                    event.eventTime - lastUpTime <= betweenTapsTimeout &&
                    distanceSquare(event.x, event.y, firstDownX, firstDownY) <= doubleTapSlopSquare
                if (!continuesSequence) {
                    tapCount = 0
                    firstDownX = event.x
                    firstDownY = event.y
                }
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                isTapCandidate = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTapCandidate && distanceSquare(event.x, event.y, downX, downY) > touchSlopSquare) {
                    reset()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isTapCandidate && event.eventTime - downTime <= tapTimeout) {
                    tapCount++
                    lastUpTime = event.eventTime
                    if (tapCount == 3) {
                        reset()
                        onTripleTap()
                    }
                } else {
                    reset()
                }
                isTapCandidate = false
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> reset()
        }
    }

    private fun reset() {
        tapCount = 0
        isTapCandidate = false
    }

    private fun distanceSquare(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }
}
