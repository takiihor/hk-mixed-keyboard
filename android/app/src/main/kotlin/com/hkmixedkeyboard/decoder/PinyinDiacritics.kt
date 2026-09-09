package com.hkmixedkeyboard.decoder

/**
 * Renders numbered Pinyin as the tone-marked form people were actually taught.
 *
 * The bundled asset stores `xiang1 gang3`, the CC-CEDICT and input-method
 * convention. Hong Kong schools teach Hanyu Pinyin with diacritics — `xiāng
 * gǎng` — and neutral tone unmarked, so the stored form is a notation the reader
 * never learned. 912 of the bundled readings carry a tone `5` that does not
 * exist in taught Pinyin at all; here it correctly renders as no mark.
 *
 * Conversion happens at display time so the asset stays in the canonical numeric
 * form the corpus tools and the toneless input dictionary share.
 */
object PinyinDiacritics {

    /**
     * Converts a space-separated numbered reading. Returns null if any syllable
     * is malformed, so a bad row shows no hint rather than a wrong one.
     */
    fun format(numbered: String): String? {
        if (numbered.isEmpty()) return null
        val syllables = numbered.split(' ')
        val out = ArrayList<String>(syllables.size)
        for (syllable in syllables) {
            out += formatSyllable(syllable) ?: return null
        }
        return out.joinToString(" ")
    }

    private fun formatSyllable(syllable: String): String? {
        if (syllable.length < 2) return null
        val tone = syllable.last().digitToIntOrNull() ?: return null
        if (tone !in 1..5) return null
        val letters = syllable.dropLast(1)
        if (letters.isEmpty() || letters.any { it !in LETTERS }) return null
        // Neutral tone carries no mark; that is the whole point of writing it as
        // "de" rather than "de5".
        if (tone == NEUTRAL_TONE) return letters

        val target = markedVowelIndex(letters) ?: return null
        val marked = MARKS[letters[target]]?.get(tone - 1) ?: return null
        return letters.substring(0, target) + marked + letters.substring(target + 1)
    }

    /**
     * Which vowel takes the mark, by the standard rule: an `a` always wins, then
     * an `e`, then the `o` of `ou`; otherwise the last vowel, which is what makes
     * `huì` mark the i but `liù` mark the u.
     */
    private fun markedVowelIndex(letters: String): Int? {
        letters.indexOf('a').let { if (it >= 0) return it }
        letters.indexOf('e').let { if (it >= 0) return it }
        letters.indexOf("ou").let { if (it >= 0) return it }
        return letters.indexOfLast { it in VOWELS }.takeIf { it >= 0 }
    }

    private const val NEUTRAL_TONE = 5
    private const val VOWELS = "aeiouü"
    private const val LETTERS = "abcdefghijklmnopqrstuvwxyzü"

    // Index 0..3 = tones 1..4.
    private val MARKS = mapOf(
        'a' to listOf("ā", "á", "ǎ", "à"),
        'e' to listOf("ē", "é", "ě", "è"),
        'i' to listOf("ī", "í", "ǐ", "ì"),
        'o' to listOf("ō", "ó", "ǒ", "ò"),
        'u' to listOf("ū", "ú", "ǔ", "ù"),
        'ü' to listOf("ǖ", "ǘ", "ǚ", "ǜ")
    )
}
