package com.example.ledscroller

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class DisplayActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_TEXT_COLOR = "extra_text_color"
        const val EXTRA_BG_COLOR = "extra_bg_color"
        const val EXTRA_SIZE = "extra_size"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_DIRECTION = "extra_direction"
        const val EXTRA_BLINK = "extra_blink"
        const val EXTRA_BOLD = "extra_bold"
        const val EXTRA_MIRROR = "extra_mirror"
        const val EXTRA_FONT = "extra_font"
        const val EXTRA_DOT_MATRIX = "extra_dot_matrix"
    }

    private lateinit var ledView: LedScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_display)
        hideSystemBars()
        ledView = findViewById(R.id.ledView)

        val message = intent.getStringExtra(EXTRA_TEXT) ?: "LED SCROLLER"
        val textColor = intent.getIntExtra(EXTRA_TEXT_COLOR, Color.parseColor("#00E676"))
        val bgColor = intent.getIntExtra(EXTRA_BG_COLOR, Color.BLACK)
        val size = intent.getFloatExtra(EXTRA_SIZE, 90f)
        val speed = intent.getIntExtra(EXTRA_SPEED, 10)
        val directionOrdinal = intent.getIntExtra(EXTRA_DIRECTION, 0)
        val blink = intent.getBooleanExtra(EXTRA_BLINK, false)
        val bold = intent.getBooleanExtra(EXTRA_BOLD, false)
        val mirror = intent.getBooleanExtra(EXTRA_MIRROR, false)
        val fontOrdinal = intent.getIntExtra(EXTRA_FONT, 0)
        val dotMatrix = intent.getBooleanExtra(EXTRA_DOT_MATRIX, false)

        ledView.text = message
        ledView.textColor = textColor
        ledView.backgroundColorLed = bgColor
        ledView.textSizePx = size
        ledView.fontFamily = LedFontFamily.entries.getOrElse(fontOrdinal) { LedFontFamily.MONOSPACE }
        ledView.bold = bold
        ledView.mirror = mirror
        ledView.dotMatrix = dotMatrix
        ledView.blinkEnabled = blink
        ledView.speedLevel = speed
        ledView.direction = ScrollDirection.entries.toTypedArray().getOrElse(directionOrdinal) { ScrollDirection.LEFT }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.systemBars())
                it.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }

    override fun onResume() {
        super.onResume()
        ledView.startAnimating()
    }

    override fun onPause() {
        super.onPause()
        ledView.stopAnimating()
    }
}
