package com.hkmixedkeyboard.engine

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.memory.MemorySuggestion

class CandidateDisplayPolicy {
    companion object {
        const val BAR_LIMIT = 15
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

        val ordered = if (chineseFirst || buffer.length <= 2) {
            custom + learnedChinese + decodedChinese + learnedEnglish +
                decodedEnglish + literal + english
        } else {
            // Latin buffer assumed to be English: after explicit custom words, an
            // exact Traditional-Chinese meaning leads all learned and built-in
            // completions. Other Chinese candidates stay immediately after the
            // literal instead of trailing English decoder noise.
            custom + exactEnglishAssist + learnedEnglish + english + literal +
                learnedChinese + decodedChinese + decodedEnglish
        }
        return ordered.distinctBy { it.text }.take(limit)
    }

    fun orderPredictions(
        learned: List<MemorySuggestion>,
        decoded: List<DecodeCandidate>,
        limit: Int = BAR_LIMIT
    ): List<DecodeCandidate> {
        val learnedChinese = learned
            .filter { isChinese(it.candidate) }
            .map { it.candidate.asLearnedCandidate() }

        return (learnedChinese + decoded)
            .filter(::isChinese)
            .distinctBy { it.text }
            .take(limit)
    }

    private fun isChinese(candidate: DecodeCandidate): Boolean =
        candidate.type != CandidateType.EN_LITERAL

    private fun DecodeCandidate.asLearnedCandidate(): DecodeCandidate =
        if (sourceSchema == SourceSchema.USER_MEMORY || sourceSchema == SourceSchema.PINYIN) this
        else copy(sourceSchema = SourceSchema.USER_MEMORY)
}
