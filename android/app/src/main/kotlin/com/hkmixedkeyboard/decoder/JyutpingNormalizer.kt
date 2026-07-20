package com.hkmixedkeyboard.decoder

import java.util.Locale

data class NormalizedJyutping(
    val key: String,
    val syllables: List<String>,
    val tones: List<Int>,
    val toneBySyllable: List<Int?>,
    val explicitBoundaries: Boolean
)

/** Canonical Jyutping input normalisation with optional tones and boundaries. */
object JyutpingNormalizer {
    private val separators = setOf(' ', '\'', '-')

    fun normalize(input: String): NormalizedJyutping? {
        if (input.isBlank()) return null
        val lower = input.lowercase(Locale.ROOT)
        val key = StringBuilder(lower.length)
        val syllables = mutableListOf<String>()
        val tones = mutableListOf<Int>()
        val toneBySyllable = mutableListOf<Int?>()
        val current = StringBuilder()
        var explicitBoundaries = false
        var canEndSyllable = false
        var previousWasSeparator = false

        fun flush(tone: Int? = null, requireCanonical: Boolean = false): Boolean {
            if (current.isEmpty()) return false
            val syllable = current.toString()
            if (requireCanonical && syllable !in JyutpingSyllables.all) return false
            syllables += syllable
            toneBySyllable += tone
            tone?.let(tones::add)
            current.setLength(0)
            return true
        }

        lower.forEachIndexed { index, ch ->
            when {
                ch in 'a'..'z' -> {
                    current.append(ch)
                    key.append(ch)
                    canEndSyllable = true
                    previousWasSeparator = false
                }
                ch in '1'..'6' -> {
                    if (!flush(ch.digitToInt(), requireCanonical = true)) return null
                    explicitBoundaries = true
                    canEndSyllable = true
                    previousWasSeparator = false
                }
                ch in separators -> {
                    if (index == 0 || index == lower.lastIndex || !canEndSyllable || previousWasSeparator) {
                        return null
                    }
                    if (current.isNotEmpty() && !flush(requireCanonical = true)) return null
                    explicitBoundaries = true
                    canEndSyllable = false
                    previousWasSeparator = true
                }
                else -> return null
            }
        }
        if (current.isNotEmpty() && !flush(requireCanonical = explicitBoundaries)) return null
        if (key.isEmpty()) return null
        return NormalizedJyutping(
            key.toString(),
            syllables,
            tones,
            toneBySyllable,
            explicitBoundaries
        )
    }
}
