package com.hkmixedkeyboard.ime

/**
 * Extracts the traditional Chinese text that can seed next-character and curated
 * Chinese→English suggestions. Current Space commits do not append a visible
 * delimiter; removeSuffix keeps older persisted/test inputs compatible.
 */
object CommittedChinesePrefixPolicy {
    fun fromSpaceCommit(committedText: String?): String =
        chineseOnly(committedText?.removeSuffix(" "))

    fun fromCandidateTap(committedText: String?): String = chineseOnly(committedText)

    private fun chineseOnly(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        var offset = 0
        while (offset < value.length) {
            val codePoint = value.codePointAt(offset)
            if (Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.HAN) return ""
            offset += Character.charCount(codePoint)
        }
        return value
    }
}
