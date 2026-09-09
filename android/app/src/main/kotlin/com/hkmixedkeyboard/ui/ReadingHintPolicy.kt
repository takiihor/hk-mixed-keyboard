package com.hkmixedkeyboard.ui

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.PinyinDiacritics
import com.hkmixedkeyboard.decoder.ReadingLookup
import com.hkmixedkeyboard.decoder.YaleRomanization

/**
 * How the Cantonese reading is spelled.
 *
 * Jyutping is the standard this app's data is built on, but Hong Kong schools
 * teach no Cantonese romanisation, so its `j`, `c`, `oe` and `eo` read wrong to
 * someone with English or Pinyin instincts. Yale spells the same phonology
 * closer to that intuition and marks tone with accents, so a learner can pick
 * whichever they can actually read.
 */
enum class CantoneseNotation { JYUTPING, YALE }

/** Which romanizations the learner asked to see above the candidate strip. */
data class ReadingHints(
    val jyutping: Boolean = false,
    val pinyin: Boolean = false,
    val cantonese: CantoneseNotation = CantoneseNotation.JYUTPING
) {
    val any: Boolean get() = jyutping || pinyin

    companion object {
        val NONE = ReadingHints()
    }
}

/**
 * Formats the one-line pronunciation hint shown above the candidates.
 *
 * The hint is a teaching aid only: it never influences ranking, commit, or which
 * candidates are offered. It is deliberately independent of the active input
 * scheme — a 粵拼 typist enters toneless codes, so a toned 粵拼 hint still teaches
 * tones, and a 拼音 hint teaches Mandarin to a 速成 typist.
 *
 * Each romanization is labelled (`粵` / `拼`) so a learner can tell them apart
 * when both are enabled, and so neither is mistaken for the other when only one
 * is. A candidate with no exact reading contributes nothing rather than a guess.
 */
class ReadingHintPolicy(
    private val jyutping: ReadingLookup,
    private val pinyin: ReadingLookup
) {
    /** Hint for the leading candidate that has a reading, while composing. */
    fun liveLabel(hints: ReadingHints, candidates: List<DecodeCandidate>): String? =
        if (!hints.any) null else candidates.firstNotNullOfOrNull { label(hints, it) }

    /** Hint confirming the candidate the user actually committed. */
    fun committedLabel(hints: ReadingHints, candidate: DecodeCandidate): String? =
        if (hints.any) label(hints, candidate) else null

    private fun label(hints: ReadingHints, candidate: DecodeCandidate): String? {
        if (candidate.type == CandidateType.EN_LITERAL) return null
        val readings = buildList {
            if (hints.jyutping) {
                jyutping.readingFor(candidate.text)
                    ?.let { spellCantonese(it, hints.cantonese) }
                    ?.let { add("${cantoneseMark(hints.cantonese)} $it") }
            }
            if (hints.pinyin) {
                // Stored numbered; shown with the diacritics schools teach.
                pinyin.readingFor(candidate.text)
                    ?.let { PinyinDiacritics.format(it) }
                    ?.let { add("$PINYIN_MARK $it") }
            }
        }
        if (readings.isEmpty()) return null
        return (listOf(candidate.text) + readings).joinToString(SEPARATOR)
    }

    private fun spellCantonese(jyutping: String, notation: CantoneseNotation): String? =
        when (notation) {
            CantoneseNotation.JYUTPING -> jyutping
            // A reading Yale cannot spell shows nothing rather than a Jyutping
            // form mislabelled as Yale.
            CantoneseNotation.YALE -> YaleRomanization.fromJyutping(jyutping)
        }

    private fun cantoneseMark(notation: CantoneseNotation): String = when (notation) {
        CantoneseNotation.JYUTPING -> JYUTPING_MARK
        CantoneseNotation.YALE -> YALE_MARK
    }

    private companion object {
        const val SEPARATOR = " · "
        const val JYUTPING_MARK = "粵"
        const val YALE_MARK = "耶"
        const val PINYIN_MARK = "拼"
    }
}
