package com.hkmixedkeyboard.decoder

/** Parsed rows from the generated HKSCS input supplement. */
object HkscsSupplement {
    data class Entry(
        val text: String,
        val codePoint: String,
        val quickCode: String,
        val jyutping: List<String>
    ) {
        /**
         * A technical, tap-only route for the small number of official HKSCS
         * records that carry neither a Cangjie nor a Cantonese input mapping.
         * It deliberately does not invent a linguistic reading or Quick code.
         */
        val unicodeFallbackCode: String?
            get() = if (quickCode.isBlank() && jyutping.isEmpty()) {
                codePoint.removePrefix("U+")
                    .takeIf { CODE_POINT_PATTERN.matches(it) }
                    ?.lowercase()
                    ?.let { "u$it" }
            } else {
                null
            }
    }

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

    fun isUnicodeFallbackInput(input: String): Boolean =
        UNICODE_FALLBACK_INPUT.matches(input)

    private val CODE_POINT_PATTERN = Regex("[0-9A-Fa-f]{4,6}")
    private val UNICODE_FALLBACK_INPUT = Regex("u[0-9a-f]{4,6}", RegexOption.IGNORE_CASE)
}
