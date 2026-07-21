package com.hkmixedkeyboard.decoder

/** Parsed rows from the generated HKSCS input supplement. */
object HkscsSupplement {
    data class Entry(
        val text: String,
        val codePoint: String,
        val quickCode: String,
        val jyutping: List<String>
    )

    fun parse(rows: List<List<String>>): List<Entry> = rows.mapNotNull { cols ->
        val text = cols.getOrNull(0)?.trim().orEmpty()
        if (text.codePointCount(0, text.length) != 1) return@mapNotNull null

        val codePoint = cols.getOrNull(1)?.trim().orEmpty()
        val quickCode = cols.getOrNull(2)?.trim()?.lowercase().orEmpty()
        val jyutping = cols.getOrNull(3).orEmpty()
            .split(';')
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .map(String::lowercase)
            .distinct()
        Entry(text, codePoint, quickCode, jyutping)
    }
}
