package com.hkmixedkeyboard.decoder

enum class JyutpingToneMatch(val rankingPriority: Int) {
    MISMATCH(0),
    UNKNOWN(1),
    MATCH(2)
}

/** Character-level tonal evidence used only when the user supplies tone digits. */
class JyutpingToneIndex(
    val readingsByText: Map<String, List<String>>
) {
    fun classify(text: String, input: NormalizedJyutping): JyutpingToneMatch {
        if (input.toneBySyllable.none { it != null }) return JyutpingToneMatch.UNKNOWN
        val characters = text.codePoints().toArray()
        if (characters.size != input.syllables.size) return JyutpingToneMatch.UNKNOWN

        var missingEvidence = false
        for (index in characters.indices) {
            val tone = input.toneBySyllable[index] ?: continue
            val character = String(characters, index, 1)
            val readings = readingsByText[character]
            if (readings.isNullOrEmpty()) {
                missingEvidence = true
                continue
            }
            val expected = input.syllables[index] + tone
            if (expected !in readings) return JyutpingToneMatch.MISMATCH
        }
        return if (missingEvidence) JyutpingToneMatch.UNKNOWN else JyutpingToneMatch.MATCH
    }
}
