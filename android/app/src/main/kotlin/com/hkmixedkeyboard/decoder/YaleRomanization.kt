package com.hkmixedkeyboard.decoder

/**
 * Renders a Jyutping reading in Yale romanisation.
 *
 * Jyutping is the LSHK standard and what this app's Cantonese data is built on,
 * but Hong Kong schools never teach a Cantonese romanisation, so its letter
 * values read wrong to someone with English or Pinyin instincts: `j` is a *y*
 * sound, `c` is *ts*, and `oe`/`eo` are a vowel English does not write. Yale
 * spells those closer to the reader's intuition and marks tone with diacritics
 * rather than digits, which is why adult Cantonese courses use it.
 *
 * Both notations transcribe the same phonology, so this is a derivation rather
 * than a second data source — no extra corpus is needed.
 */
object YaleRomanization {

    /**
     * Converts a space-separated toned Jyutping reading. Returns null if any
     * syllable falls outside the standard inventory, so an unconvertible reading
     * shows nothing rather than an invented spelling.
     */
    fun fromJyutping(jyutping: String): String? {
        if (jyutping.isEmpty()) return null
        val syllables = jyutping.split(' ')
        val out = ArrayList<String>(syllables.size)
        for (syllable in syllables) {
            out += convertSyllable(syllable) ?: return null
        }
        return out.joinToString(" ")
    }

    private fun convertSyllable(syllable: String): String? {
        if (syllable.length < 2) return null
        val tone = syllable.last().digitToIntOrNull() ?: return null
        if (tone !in 1..6) return null
        val body = syllable.dropLast(1)
        if (body.isEmpty() || body.any { it !in 'a'..'z' }) return null

        val initial = INITIALS.firstOrNull { body.startsWith(it) } ?: ""
        var rest = body.substring(initial.length)
        var yale = YALE_INITIALS[initial] ?: initial
        rest = FINALS.entries.firstOrNull { rest == it.key }?.value ?: rest

        // Jyutping "jyu…" is Yale "yu…": the initial becomes y and the final
        // already starts with yu, so the two must not both be written.
        if (yale == "y" && rest.startsWith("yu")) yale = ""

        return applyTone(yale + rest, tone)
    }

    /**
     * Yale marks the six tones with an accent on the syllable's first vowel plus,
     * for the three low tones, an `h` after the vowel — 月 is `yuht`, not
     * `yuth`, and 十 is `sahp`. The `h` belongs to the nucleus, so it precedes
     * any final consonant.
     *
     * A syllabic nasal (唔 m4, 五 ng5) has no vowel to carry the accent. Those
     * keep the `h` and go unaccented rather than stacking a combining mark on a
     * consonant, which few fonts render legibly.
     */
    private fun applyTone(letters: String, tone: Int): String {
        val firstVowel = letters.indexOfFirst { it in VOWELS }
        val lastVowel = letters.indexOfLast { it in VOWELS }
        val body = when {
            tone !in LOW_TONES -> letters
            lastVowel < 0 -> letters + "h"
            else -> letters.substring(0, lastVowel + 1) + "h" + letters.substring(lastVowel + 1)
        }
        val accent = ACCENTS[tone] ?: return body
        if (firstVowel < 0) return body
        val marked = MARKS[letters[firstVowel]]?.get(accent) ?: return body
        return body.substring(0, firstVowel) + marked + body.substring(firstVowel + 1)
    }

    private const val VOWELS = "aeiou"
    private val LOW_TONES = setOf(4, 5, 6)

    /** Tone -> which accent, or absent for the two unaccented tones (3 and 6). */
    private val ACCENTS = mapOf(1 to MACRON, 2 to ACUTE, 4 to GRAVE, 5 to ACUTE)

    // Longest first, so "ng" and "gw" win over "n" and "g".
    private val INITIALS = listOf(
        "ng", "gw", "kw", "ch", "b", "p", "m", "f", "d", "t", "n", "l",
        "g", "k", "h", "s", "z", "c", "j", "w"
    )

    private val YALE_INITIALS = mapOf("j" to "y", "z" to "j", "c" to "ch")

    /**
     * Only the rounded front vowels differ. Jyutping writes them `oe` and `eo`
     * by length and environment; Yale writes both `eu`.
     */
    private val FINALS = mapOf(
        "oe" to "eu", "oeng" to "eung", "oek" to "euk",
        "eoi" to "eui", "eon" to "eun", "eot" to "eut"
    )

    private val MARKS = mapOf(
        'a' to mapOf(MACRON to "ā", ACUTE to "á", GRAVE to "à"),
        'e' to mapOf(MACRON to "ē", ACUTE to "é", GRAVE to "è"),
        'i' to mapOf(MACRON to "ī", ACUTE to "í", GRAVE to "ì"),
        'o' to mapOf(MACRON to "ō", ACUTE to "ó", GRAVE to "ò"),
        'u' to mapOf(MACRON to "ū", ACUTE to "ú", GRAVE to "ù")
    )
}

private const val MACRON = 0
private const val ACUTE = 1
private const val GRAVE = 2
