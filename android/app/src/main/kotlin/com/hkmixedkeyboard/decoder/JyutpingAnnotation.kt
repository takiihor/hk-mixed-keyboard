package com.hkmixedkeyboard.decoder

object JyutpingAnnotation {
    fun forInput(input: String): String? {
        val normalized = JyutpingNormalizer.normalize(input) ?: return null
        return if (normalized.tones.isNotEmpty()) {
            normalized.syllables.mapIndexed { index, syllable ->
                normalized.toneBySyllable[index]?.let { "$syllable$it" } ?: syllable
            }.joinToString(" ")
        } else {
            normalized.syllables.joinToString(" ")
        }
    }

    fun reverseLookup(text: String, readingsByText: Map<String, List<String>>): String? =
        readingsByText[text]?.distinct()?.take(3)?.joinToString(" / ")
}
