package com.hkmixedkeyboard.ime

/**
 * How much a whole-word backspace removes.
 *
 * A held backspace escalates from characters to words, so it needs to agree with
 * what a reader would call "the last word" in mixed Hong Kong text: a run of
 * Latin letters and digits is one word, and so is a run of Han characters, but
 * the two do not merge — deleting the word from "send個file" should leave
 * "send個", not clear the lot. Trailing spaces go with the word they follow, so
 * one escalated step never stalls on whitespace alone.
 */
object WordDeletePolicy {

    /** Characters to delete before the cursor, given the text preceding it. */
    fun deleteCount(before: CharSequence): Int {
        if (before.isEmpty()) return 0
        var index = before.length
        while (index > 0 && before[index - 1].isWhitespace()) index--
        if (index == 0) return before.length

        val end = index
        val kind = kindOf(before[index - 1])
        while (index > 0 && kindOf(before[index - 1]) == kind) index--
        // A run of punctuation or symbols has no word shape, so take one glyph and
        // let the next repeat decide again rather than swallowing a whole line.
        if (kind == Kind.OTHER) index = end - 1
        return before.length - index
    }

    private enum class Kind { LATIN, HAN, OTHER }

    private fun kindOf(c: Char): Kind = when {
        c.isLetterOrDigit() && c.code < 0x2E80 -> Kind.LATIN
        c.code >= 0x2E80 -> Kind.HAN
        else -> Kind.OTHER
    }
}
