package com.hkmixedkeyboard.ui

import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema

/**
 * Produces a visible candidate label without changing the text that is committed.
 *
 * A system font can lack rare HKSCS glyphs. Keeping the candidate selectable is
 * more useful than silently removing it, so unsupported code points are shown as
 * an unambiguous Unicode label (for example `U+2003E`).
 */
object CandidatePresentation {
    fun label(
        candidate: DecodeCandidate,
        hasGlyph: (String) -> Boolean,
        chineseAssistPrefix: String = "中→英"
    ): String {
        val textLabel = label(candidate.text, hasGlyph)
        val directionLabel = if (candidate.sourceSchema == SourceSchema.CHINESE_ASSIST) {
            "$chineseAssistPrefix $textLabel"
        } else {
            textLabel
        }
        return candidate.annotation?.takeIf { it.isNotBlank() }?.let {
            "$directionLabel · $it"
        } ?: directionLabel
    }

    fun label(text: String, hasGlyph: (String) -> Boolean): String {
        if (text.isEmpty()) return text

        val parts = mutableListOf<String>()
        val supportedRun = StringBuilder()

        fun flushSupportedRun() {
            if (supportedRun.isNotEmpty()) {
                parts += supportedRun.toString()
                supportedRun.setLength(0)
            }
        }

        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val glyph = String(Character.toChars(codePoint))
            if (hasGlyph(glyph)) {
                supportedRun.append(glyph)
            } else {
                flushSupportedRun()
                parts += "U+" + codePoint.toString(16).uppercase().padStart(4, '0')
            }
            index += Character.charCount(codePoint)
        }
        flushSupportedRun()
        return parts.joinToString(" ")
    }
}
