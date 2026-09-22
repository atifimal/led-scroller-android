package com.example.ledscroller

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val textColorOptions = listOf(
        Color.parseColor("#00E676"), // neon green
        Color.parseColor("#FF1744"), // red
        Color.parseColor("#2979FF"), // blue
        Color.parseColor("#FFEA00"), // yellow
        Color.parseColor("#00E5FF"), // cyan
        Color.parseColor("#FF00E5"), // magenta/pink
        Color.parseColor("#FFFFFF"), // white
        Color.parseColor("#FF9100")  // orange
    )

    private val bgColorOptions = listOf(
        Color.parseColor("#000000"), // black
        Color.parseColor("#0D1B2A"), // dark navy
        Color.parseColor("#0B2E13"), // dark green
        Color.parseColor("#2A0D0D"), // dark red
        Color.parseColor("#1A1A1A"), // dark gray
        Color.parseColor("#FFFFFF")  // white
    )

    private var selectedTextColor = textColorOptions[0]
    private var selectedBgColor = bgColorOptions[0]

    private lateinit var preview: LedScrollView
    private lateinit var editMessage: EditText
    private lateinit var radioDirection: RadioGroup
    private lateinit var seekSpeed: SeekBar
    private lateinit var seekSize: SeekBar
    private lateinit var checkBlink: CheckBox
    private lateinit var checkBold: CheckBox
    private lateinit var checkMirror: CheckBox
    private lateinit var spinnerSaved: Spinner

    private lateinit var store: MessageStore
    private var savedNames: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = MessageStore(this)

        preview = findViewById(R.id.previewLed)
        editMessage = findViewById(R.id.editMessage)
        radioDirection = findViewById(R.id.radioDirection)
        seekSpeed = findViewById(R.id.seekSpeed)
        seekSize = findViewById(R.id.seekSize)
        checkBlink = findViewById(R.id.checkBlink)
        checkBold = findViewById(R.id.checkBold)
        checkMirror = findViewById(R.id.checkMirror)
        spinnerSaved = findViewById(R.id.spinnerSaved)

        buildColorRow(findViewById(R.id.rowTextColor), textColorOptions) { color ->
            selectedTextColor = color
            updatePreview()
        }
        buildColorRow(findViewById(R.id.rowBgColor), bgColorOptions) { color ->
            selectedBgColor = color
            updatePreview()
        }

        editMessage.setText("MERHABA!")
        updatePreview()

        editMessage.addTextChangedListener(simpleWatcher { updatePreview() })
        radioDirection.setOnCheckedChangeListener { _, _ -> updatePreview() }
        checkBlink.setOnCheckedChangeListener { _, _ -> updatePreview() }
        checkBold.setOnCheckedChangeListener { _, _ -> updatePreview() }
        checkMirror.setOnCheckedChangeListener { _, _ -> updatePreview() }
        seekSpeed.setOnSeekBarChangeListener(simpleSeekListener { updatePreview() })
        seekSize.setOnSeekBarChangeListener(simpleSeekListener { updatePreview() })

        refreshSavedSpinner()

        findViewById<Button>(R.id.btnSave).setOnClickListener { onSaveClicked() }
        findViewById<Button>(R.id.btnLoad).setOnClickListener { onLoadClicked() }
        findViewById<Button>(R.id.btnDelete).setOnClickListener { onDeleteClicked() }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startFullscreen() }
    }

    private fun currentDirectionOrdinal(): Int = when (radioDirection.checkedRadioButtonId) {
        R.id.radioRight -> ScrollDirection.RIGHT.ordinal
        R.id.radioStatic -> ScrollDirection.STATIC_BLINK.ordinal
        else -> ScrollDirection.LEFT.ordinal
    }

    private fun currentSizePx(): Float {
        val sp = (seekSize.progress + 20).toFloat() // 20sp..160sp
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
    }

    private fun updatePreview() {
        preview.text = editMessage.text.toString().ifBlank { " " }
        preview.textColor = selectedTextColor
        preview.backgroundColorLed = selectedBgColor
        preview.textSizePx = currentSizePx() / 2.2f // smaller for the in-app preview box
        preview.bold = checkBold.isChecked
        preview.mirror = checkMirror.isChecked
        preview.blinkEnabled = checkBlink.isChecked
        preview.speedLevel = (seekSpeed.progress).coerceAtLeast(1)
        preview.direction = ScrollDirection.entries.toTypedArray()[currentDirectionOrdinal()]
        preview.startAnimating()
    }

    private fun startFullscreen() {
        val message = editMessage.text.toString().ifBlank { "LED SCROLLER" }
        val intent = Intent(this, DisplayActivity::class.java).apply {
            putExtra(DisplayActivity.EXTRA_TEXT, message)
            putExtra(DisplayActivity.EXTRA_TEXT_COLOR, selectedTextColor)
            putExtra(DisplayActivity.EXTRA_BG_COLOR, selectedBgColor)
            putExtra(DisplayActivity.EXTRA_SIZE, currentSizePx())
            putExtra(DisplayActivity.EXTRA_SPEED, seekSpeed.progress.coerceAtLeast(1))
            putExtra(DisplayActivity.EXTRA_DIRECTION, currentDirectionOrdinal())
            putExtra(DisplayActivity.EXTRA_BLINK, checkBlink.isChecked)
            putExtra(DisplayActivity.EXTRA_BOLD, checkBold.isChecked)
            putExtra(DisplayActivity.EXTRA_MIRROR, checkMirror.isChecked)
        }
        startActivity(intent)
    }

    // ---- Saved messages ----

    private fun onSaveClicked() {
        val message = editMessage.text.toString().ifBlank {
            Toast.makeText(this, "Önce bir mesaj yazın", Toast.LENGTH_SHORT).show()
            return
        }
        val name = message.take(20)
        store.save(
            SavedMessage(
                name = name,
                text = message,
                textColor = selectedTextColor,
                bgColor = selectedBgColor,
                sizeProgress = seekSize.progress,
                speedProgress = seekSpeed.progress,
                directionOrdinal = currentDirectionOrdinal(),
                blink = checkBlink.isChecked,
                bold = checkBold.isChecked,
                mirror = checkMirror.isChecked
            )
        )
        Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show()
        refreshSavedSpinner()
    }

    private fun onLoadClicked() {
        val position = spinnerSaved.selectedItemPosition
        if (position < 0 || position >= savedNames.size) return
        val saved = store.loadAll().getOrNull(position) ?: return

        editMessage.setText(saved.text)
        selectedTextColor = saved.textColor
        selectedBgColor = saved.bgColor
        seekSize.progress = saved.sizeProgress
        seekSpeed.progress = saved.speedProgress
        checkBlink.isChecked = saved.blink
        checkBold.isChecked = saved.bold
        checkMirror.isChecked = saved.mirror
        radioDirection.check(
            when (ScrollDirection.entries.toTypedArray().getOrElse(saved.directionOrdinal) { ScrollDirection.LEFT }) {
                ScrollDirection.LEFT -> R.id.radioLeft
                ScrollDirection.RIGHT -> R.id.radioRight
                ScrollDirection.STATIC_BLINK -> R.id.radioStatic
            }
        )
        updatePreview()
    }

    private fun onDeleteClicked() {
        val position = spinnerSaved.selectedItemPosition
        if (position < 0 || position >= savedNames.size) return
        store.deleteAt(position)
        refreshSavedSpinner()
        Toast.makeText(this, "Silindi", Toast.LENGTH_SHORT).show()
    }

    private fun refreshSavedSpinner() {
        savedNames = store.loadAll().map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, savedNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSaved.adapter = adapter
    }

    // ---- UI helpers ----

    private fun buildColorRow(container: LinearLayout, colors: List<Int>, onPick: (Int) -> Unit) {
        val sizeDp = 40
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
        val marginPx = (6 * resources.displayMetrics.density).toInt()

        val swatches = mutableListOf<View>()

        colors.forEachIndexed { index, color ->
            val swatch = View(this)
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(if (index == 0) (3 * resources.displayMetrics.density).toInt() else 0, Color.WHITE)
            }
            swatch.background = drawable
            val params = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                marginEnd = marginPx
            }
            swatch.layoutParams = params
            swatch.setOnClickListener {
                onPick(color)
                swatches.forEach { v ->
                    (v.background as GradientDrawable).setStroke(0, Color.WHITE)
                }
                (swatch.background as GradientDrawable).setStroke(
                    (3 * resources.displayMetrics.density).toInt(), Color.WHITE
                )
            }
            swatches.add(swatch)
            container.addView(swatch)
        }
        container.gravity = Gravity.START
    }

    private fun simpleWatcher(onChanged: () -> Unit) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChanged() }
        override fun afterTextChanged(s: android.text.Editable?) {}
    }

    private fun simpleSeekListener(onChanged: () -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { onChanged() }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    override fun onResume() {
        super.onResume()
        preview.startAnimating()
    }

    override fun onPause() {
        super.onPause()
        preview.stopAnimating()
    }
}
