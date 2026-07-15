package com.hkmixedkeyboard.decoder

object JyutpingAnnotation {
    fun forInput(input: String): String? {
        val normalized = JyutpingNormalizer.normalize(input) ?: return null
        return if (normalized.tones.isNotEmpty()) {
            val tones = normalized.tones.iterator()
            normalized.syllables.joinToString(" ") { syllable ->
                if (tones.hasNext()) "$syllable${tones.next()}" else syllable
            }
        } else {
            normalized.syllables.joinToString(" ")
        }
    }

    fun reverseLookup(text: String, readingsByText: Map<String, List<String>>): String? =
        readingsByText[text]?.distinct()?.take(3)?.joinToString(" / ")
}
