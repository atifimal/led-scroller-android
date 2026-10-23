package com.example.ledscroller

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

    private val fontOptions = LedFontFamily.entries.toList()

    private var selectedTextColor = textColorOptions[0]
    private var selectedBgColor = bgColorOptions[0]
    private var selectedFont = fontOptions[0]

    private lateinit var preview: LedScrollView
    private lateinit var editMessage: EditText
    private lateinit var radioDirection: RadioGroup
    private lateinit var seekSpeed: SeekBar
    private lateinit var seekSize: SeekBar
    private lateinit var checkBlink: CheckBox
    private lateinit var checkBold: CheckBox
    private lateinit var checkMirror: CheckBox
    private lateinit var checkDotMatrix: CheckBox
    private lateinit var savedList: LinearLayout
    private lateinit var textSavedEmpty: TextView
    private lateinit var spinnerFont: Spinner

    private lateinit var store: MessageStore

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
        checkDotMatrix = findViewById(R.id.checkDotMatrix)
        savedList = findViewById(R.id.savedList)
        textSavedEmpty = findViewById(R.id.textSavedEmpty)
        spinnerFont = findViewById(R.id.spinnerFont)

        spinnerFont.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, fontOptions.map { it.label }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerFont.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedFont = fontOptions[position]
                updatePreview()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

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
        checkDotMatrix.setOnCheckedChangeListener { _, _ -> updatePreview() }
        seekSpeed.setOnSeekBarChangeListener(simpleSeekListener { updatePreview() })
        seekSize.setOnSeekBarChangeListener(simpleSeekListener { updatePreview() })

        refreshSavedList()

        findViewById<Button>(R.id.btnSave).setOnClickListener { onSaveClicked() }
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
        preview.fontFamily = selectedFont
        preview.bold = checkBold.isChecked
        preview.mirror = checkMirror.isChecked
        preview.dotMatrix = checkDotMatrix.isChecked
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
            putExtra(DisplayActivity.EXTRA_FONT, selectedFont.ordinal)
            putExtra(DisplayActivity.EXTRA_DOT_MATRIX, checkDotMatrix.isChecked)
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
                mirror = checkMirror.isChecked,
                fontOrdinal = selectedFont.ordinal,
                dotMatrix = checkDotMatrix.isChecked
            )
        )
        Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show()
        refreshSavedList()
    }

    private fun loadSaved(saved: SavedMessage) {
        editMessage.setText(saved.text)
        selectedTextColor = saved.textColor
        selectedBgColor = saved.bgColor
        seekSize.progress = saved.sizeProgress
        seekSpeed.progress = saved.speedProgress
        checkBlink.isChecked = saved.blink
        checkBold.isChecked = saved.bold
        checkMirror.isChecked = saved.mirror
        checkDotMatrix.isChecked = saved.dotMatrix
        selectedFont = fontOptions.getOrElse(saved.fontOrdinal) { fontOptions[0] }
        spinnerFont.setSelection(selectedFont.ordinal)
        radioDirection.check(
            when (ScrollDirection.entries.toTypedArray().getOrElse(saved.directionOrdinal) { ScrollDirection.LEFT }) {
                ScrollDirection.LEFT -> R.id.radioLeft
                ScrollDirection.RIGHT -> R.id.radioRight
                ScrollDirection.STATIC_BLINK -> R.id.radioStatic
            }
        )
        updatePreview()
    }

    private fun confirmDelete(position: Int, saved: SavedMessage) {
        AlertDialog.Builder(this)
            .setMessage("\"${saved.text.lines().first()}\" silinsin mi?")
            .setPositiveButton("Sil") { _, _ ->
                store.deleteAt(position)
                refreshSavedList()
                Toast.makeText(this, "Silindi", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun refreshSavedList() {
        val messages = store.loadAll()
        savedList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        messages.forEachIndexed { position, saved ->
            val row = inflater.inflate(R.layout.item_saved_message, savedList, false)
            row.findViewById<TextView>(R.id.savedSwatch).apply {
                setBackgroundColor(saved.bgColor)
                setTextColor(saved.textColor)
            }
            row.findViewById<TextView>(R.id.savedName).text = saved.text.replace('\n', ' ')
            row.findViewById<Button>(R.id.savedDelete).setOnClickListener { confirmDelete(position, saved) }
            row.setOnClickListener {
                loadSaved(saved)
                Toast.makeText(this, "Yüklendi", Toast.LENGTH_SHORT).show()
            }
            savedList.addView(row)
        }
        textSavedEmpty.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
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
