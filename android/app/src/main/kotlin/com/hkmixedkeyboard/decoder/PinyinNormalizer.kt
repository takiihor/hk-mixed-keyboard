package com.hkmixedkeyboard.decoder

import java.util.Locale

/** Hanyu Pinyin normalisation. Strict output is always first; fuzzy variants are opt-in. */
object PinyinNormalizer {
    private val toneMarks = mapOf(
        'ā' to 'a', 'á' to 'a', 'ǎ' to 'a', 'à' to 'a',
        'ē' to 'e', 'é' to 'e', 'ě' to 'e', 'è' to 'e',
        'ī' to 'i', 'í' to 'i', 'ǐ' to 'i', 'ì' to 'i',
        'ō' to 'o', 'ó' to 'o', 'ǒ' to 'o', 'ò' to 'o',
        'ū' to 'u', 'ú' to 'u', 'ǔ' to 'u', 'ù' to 'u',
        'ǖ' to 'v', 'ǘ' to 'v', 'ǚ' to 'v', 'ǜ' to 'v', 'ü' to 'v'
    )
    private val separators = setOf(' ', '\'', '-')
    private val fuzzyPairs = listOf(
        "zh" to "z", "ch" to "c", "sh" to "s",
        "eng" to "en", "ing" to "in", "ang" to "an"
    )
    private val palatalUmlaut = Regex("([jqxy])v")

    fun normalize(input: String): String? {
        if (input.isBlank()) return null
        val lower = input.lowercase(Locale.ROOT).replace("u:", "v")
        val output = StringBuilder(lower.length)
        val currentSyllable = StringBuilder()
        var syllableHasLetter = false
        var previousWasBoundary = false
        var previousWasSeparator = false
        var hasExplicitBoundary = false

        fun flushExplicitSyllable(): Boolean {
            if (currentSyllable.isEmpty()) return false
            val syllable = canonicalize(currentSyllable.toString())
            if (syllable !in MandarinSyllables.all) return false
            currentSyllable.setLength(0)
            hasExplicitBoundary = true
            return true
        }

        lower.forEachIndexed { index, raw ->
            val ch = toneMarks[raw] ?: raw
            when {
                ch in 'a'..'z' -> {
                    output.append(ch)
                    currentSyllable.append(ch)
                    syllableHasLetter = true
                    previousWasBoundary = false
                    previousWasSeparator = false
                }
                ch in '1'..'5' -> {
                    if (!syllableHasLetter || previousWasBoundary || !flushExplicitSyllable()) {
                        return null
                    }
                    syllableHasLetter = false
                    previousWasBoundary = true
                    previousWasSeparator = false
                }
                ch in separators -> {
                    if (index == 0 || index == lower.lastIndex ||
                        previousWasSeparator || !syllableHasLetter && !previousWasBoundary) return null
                    if (currentSyllable.isNotEmpty() && !flushExplicitSyllable()) return null
                    hasExplicitBoundary = true
                    syllableHasLetter = false
                    previousWasBoundary = true
                    previousWasSeparator = true
                }
                else -> return null
            }
        }
        if (output.isEmpty()) return null
        if (hasExplicitBoundary && currentSyllable.isNotEmpty() &&
            canonicalize(currentSyllable.toString()) !in MandarinSyllables.all) {
            return null
        }
        return canonicalize(output.toString())
    }

    fun variants(input: String, fuzzyEnabled: Boolean): List<String> {
        val strict = normalize(input) ?: return emptyList()
        if (!fuzzyEnabled) return listOf(strict)
        val variants = linkedSetOf(strict)
        fuzzyPairs.forEach { (long, short) ->
            if (strict.contains(long)) variants += strict.replaceFirst(long, short)
            if (strict.contains(short)) variants += strict.replaceFirst(short, long)
        }
        return variants.take(MAX_VARIANTS)
    }

    private const val MAX_VARIANTS = 8

    private fun canonicalize(value: String): String = value.replace(palatalUmlaut, "$1u")
}
