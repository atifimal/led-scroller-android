package com.example.ledscroller

import android.content.Context

data class SavedMessage(
    val name: String,
    val text: String,
    val textColor: Int,
    val bgColor: Int,
    val sizeProgress: Int,
    val speedProgress: Int,
    val directionOrdinal: Int,
    val blink: Boolean,
    val bold: Boolean,
    val mirror: Boolean
)

/**
 * Very small persistence helper. Avoids any external JSON library by
 * encoding each saved message as a single delimited line and storing
 * the whole list as one string in SharedPreferences.
 */
class MessageStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadAll(): List<SavedMessage> {
        val raw = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return raw.split(RECORD_SEP).mapNotNull { record ->
            if (record.isBlank()) return@mapNotNull null
            val fields = record.split(FIELD_SEP)
            if (fields.size < 10) return@mapNotNull null
            try {
                SavedMessage(
                    name = fields[0],
                    text = fields[1],
                    textColor = fields[2].toInt(),
                    bgColor = fields[3].toInt(),
                    sizeProgress = fields[4].toInt(),
                    speedProgress = fields[5].toInt(),
                    directionOrdinal = fields[6].toInt(),
                    blink = fields[7] == "1",
                    bold = fields[8] == "1",
                    mirror = fields[9] == "1"
                )
            } catch (e: NumberFormatException) {
                null
            }
        }
    }

    fun save(message: SavedMessage) {
        val current = loadAll().toMutableList()
        current.add(message)
        persist(current)
    }

    fun deleteAt(index: Int) {
        val current = loadAll().toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            persist(current)
        }
    }

    private fun persist(list: List<SavedMessage>) {
        val encoded = list.joinToString(RECORD_SEP) { m ->
            listOf(
                m.name, m.text, m.textColor, m.bgColor, m.sizeProgress,
                m.speedProgress, m.directionOrdinal,
                if (m.blink) "1" else "0",
                if (m.bold) "1" else "0",
                if (m.mirror) "1" else "0"
            ).joinToString(FIELD_SEP)
        }
        prefs.edit().putString(KEY_MESSAGES, encoded).apply()
    }

    companion object {
        private const val PREFS_NAME = "led_scroller_prefs"
        private const val KEY_MESSAGES = "saved_messages"
        // Field/record names can't contain these delimiter characters.
        private const val FIELD_SEP = "\u001F"
        private const val RECORD_SEP = "\u001E"
    }
}
