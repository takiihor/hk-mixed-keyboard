package com.hkmixedkeyboard.ui

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.ReadingLookup

/** Which romanizations the learner asked to see above the candidate strip. */
data class ReadingHints(
    val jyutping: Boolean = false,
    val pinyin: Boolean = false
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
                jyutping.readingFor(candidate.text)?.let { add("$JYUTPING_MARK $it") }
            }
            if (hints.pinyin) {
                pinyin.readingFor(candidate.text)?.let { add("$PINYIN_MARK $it") }
            }
        }
        if (readings.isEmpty()) return null
        return (listOf(candidate.text) + readings).joinToString(SEPARATOR)
    }

    private companion object {
        const val SEPARATOR = " · "
        const val JYUTPING_MARK = "粵"
        const val PINYIN_MARK = "拼"
    }
}
