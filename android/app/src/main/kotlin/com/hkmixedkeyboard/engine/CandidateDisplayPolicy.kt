package com.hkmixedkeyboard.engine

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.memory.MemorySuggestion

class CandidateDisplayPolicy {
    companion object {
        const val BAR_LIMIT = 15
        // Exact English meanings shown before the code-switching phrases for the
        // same word. CC-CEDICT often supplies five or more glosses for a common
        // verb, which on a scrolling strip pushes send返 out of sight entirely —
        // and in Hong Kong chat that phrase is likelier than the fifth synonym.
        const val LEADING_EXACT_ASSIST = 2
        // Continuations shown before the English gloss of the run just committed.
        // The strip fits roughly seven candidates, so a gloss placed after this
        // many is reachable without scrolling while the characters the user is
        // actively building a word from still lead.
        const val LEADING_PREDICTIONS = 5
        // Latin buffers this short are assumed Chinese unless the user's own
        // history for the buffer says otherwise.
        const val SHORT_BUFFER_LENGTH = 2
        // Commits of this exact buffer before its history outranks the length rule.
        const val CN_PREFERENCE_MIN_COUNT = 3
        const val CN_PREFERENCE_MIN_CONFIDENCE = 0.75
        // Chinese candidates shown before a Hong Kong English token on a buffer
        // that is also a Quick code.
        const val LEADING_CHINESE_BEFORE_LOCAL = 4
        // The expanded grid scrolls. It must not truncate a valid corpus candidate:
        // a rare HKSCS character can share a code with more common entries and rank
        // beyond an arbitrary display cap.
        const val EXPANDED_LIMIT = Int.MAX_VALUE
    }

    fun order(
        buffer: String,
        learned: List<MemorySuggestion>,
        english: List<DecodeCandidate>,
        decoded: List<DecodeCandidate>,
        literal: DecodeCandidate,
        // Put decoded Chinese first regardless of buffer length. True for 粵拼 (the
        // buffer is romanization) and for exact Quick phrase matches (unambiguously
        // Chinese intent). Otherwise long Latin buffers are assumed to be English.
        chineseFirst: Boolean = false,
        // Share of this exact buffer's past commits that were Chinese. 0.5 means
        // "no opinion"; evidence is counted from [learned] so the policy stays pure.
        cnRatio: Double = 0.5,
        limit: Int = BAR_LIMIT
    ): List<DecodeCandidate> {
        // User custom words (自訂詞庫) are an explicit code→word mapping, so they
        // always rank first, ahead of every other source.
        val custom = decoded.filter { it.sourceSchema == SourceSchema.USER_MEMORY }
        val rest = decoded.filterNot { it.sourceSchema == SourceSchema.USER_MEMORY }

        val learnedChinese = learned
            .filter { isChinese(it.candidate) }
            .map { it.candidate.asLearnedCandidate() }
        val learnedEnglish = learned
            .filterNot { isChinese(it.candidate) }
            .map { it.candidate.asLearnedCandidate() }
        val decodedChinese = rest.filter(::isChinese)
        val decodedEnglish = rest.filterNot(::isChinese)
        val exactEnglishAssist = decodedChinese.filter {
            it.sourceSchema == SourceSchema.ENGLISH_ASSIST &&
                it.code.equals(buffer, ignoreCase = true)
        }
        // Code-switching completions for the exact word typed (send → send返).
        // Without their own slot they sort with ordinary decoded Chinese, which on
        // a Latin buffer lands them behind the literal and every learned entry.
        val exactMixed = decodedChinese.filter {
            it.sourceSchema == SourceSchema.MIXED_PHRASE &&
                it.code.equals(buffer, ignoreCase = true)
        }

        // Hong Kong tokens (MTR, HKD …) are marked isHkCore by the completion
        // index. General English completions belong at the tail, but these do
        // not: "mt" is also a Quick code, so its fifteen Chinese candidates fill
        // the bar and truncate the tail away entirely.
        val (localEnglish, generalEnglish) = english.partition { it.isHkCore }

        val ordered = if (chineseFirst || leadsWithChinese(buffer, learned, cnRatio)) {
            custom + learnedChinese + decodedChinese.take(LEADING_CHINESE_BEFORE_LOCAL) +
                localEnglish + decodedChinese.drop(LEADING_CHINESE_BEFORE_LOCAL) +
                learnedEnglish + decodedEnglish + literal + generalEnglish
        } else {
            // Latin buffer assumed to be English: after explicit custom words, an
            // exact Traditional-Chinese meaning and the code-switching phrases for
            // that same word lead all learned and built-in completions. Other
            // Chinese candidates stay immediately after the literal instead of
            // trailing English decoder noise.
            custom + exactEnglishAssist.take(LEADING_EXACT_ASSIST) + exactMixed +
                exactEnglishAssist.drop(LEADING_EXACT_ASSIST) + localEnglish +
                learnedEnglish + generalEnglish + literal + learnedChinese +
                decodedChinese + decodedEnglish
        }
        return ordered.distinctBy { it.text }.take(limit)
    }

    /**
     * Bar contents after a Chinese commit: what commonly follows, then — once the
     * next characters have had their room — the English glosses for the run just
     * committed, completing 中英互相建議 in the Chinese -> English direction.
     */
    fun orderPredictions(
        learned: List<MemorySuggestion>,
        decoded: List<DecodeCandidate>,
        english: List<DecodeCandidate> = emptyList(),
        limit: Int = BAR_LIMIT
    ): List<DecodeCandidate> {
        val learnedChinese = learned
            .filter { isChinese(it.candidate) }
            .map { it.candidate.asLearnedCandidate() }

        val chinese = (learnedChinese + decoded)
            .filter(::isChinese)
            .distinctBy { it.text }

        // Continuations lead, the glosses sit just inside the visible strip, and
        // the remaining continuations follow — so a translation is findable
        // without displacing the character the user was building.
        return (chinese.take(LEADING_PREDICTIONS) + english +
            chinese.drop(LEADING_PREDICTIONS))
            .distinctBy { it.text }
            .take(limit)
    }

    /**
     * Whether Chinese candidates lead for a Latin buffer.
     *
     * Buffer length is a crude proxy for intent, and it is wrong for exactly the
     * buffers a code-switching typist uses most: "ok" and "no" are short but
     * usually English, while a habitual Quick code may be long and always
     * Chinese. The memory layer has tracked a per-buffer Chinese/English ratio all
     * along and the ordering never asked for it — so consult it once the user has
     * shown a consistent habit for this buffer, and fall back to length otherwise.
     */
    private fun leadsWithChinese(
        buffer: String,
        learned: List<MemorySuggestion>,
        cnRatio: Double
    ): Boolean {
        val evidence = learned.sumOf { it.exactCount }
        if (evidence >= CN_PREFERENCE_MIN_COUNT) {
            if (cnRatio >= CN_PREFERENCE_MIN_CONFIDENCE) return true
            if (cnRatio <= 1.0 - CN_PREFERENCE_MIN_CONFIDENCE) return false
        }
        return buffer.length <= SHORT_BUFFER_LENGTH
    }

    private fun isChinese(candidate: DecodeCandidate): Boolean =
        candidate.type != CandidateType.EN_LITERAL

    private fun DecodeCandidate.asLearnedCandidate(): DecodeCandidate =
        if (sourceSchema == SourceSchema.USER_MEMORY || sourceSchema == SourceSchema.PINYIN) this
        else copy(sourceSchema = SourceSchema.USER_MEMORY)
}
