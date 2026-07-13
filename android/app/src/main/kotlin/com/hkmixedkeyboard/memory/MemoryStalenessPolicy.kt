package com.hkmixedkeyboard.memory

import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema

class MemoryStalenessPolicy(
    private val quickContains: (String) -> Boolean,
    private val purgeVariant: (String) -> Boolean
) {
    fun isStale(candidate: DecodeCandidate): Boolean {
        val codePoints = candidate.text.codePoints().toArray()
        val allHan = codePoints.isNotEmpty() && codePoints.all {
            Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN
        }
        if (candidate.sourceSchema == SourceSchema.PINYIN) return !allHan

        return codePoints.any { codePoint ->
            val text = String(Character.toChars(codePoint))
            Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN &&
                (!quickContains(text) || purgeVariant(text))
        }
    }
}
