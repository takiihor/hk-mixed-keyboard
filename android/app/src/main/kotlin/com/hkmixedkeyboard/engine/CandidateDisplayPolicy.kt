package com.hkmixedkeyboard.engine

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.memory.MemorySuggestion

class CandidateDisplayPolicy {
    companion object {
        const val BAR_LIMIT = 15
        const val EXPANDED_LIMIT = 80
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
            .map { it.candidate.asUserMemory() }
        val learnedEnglish = learned
            .filterNot { isChinese(it.candidate) }
            .map { it.candidate.asUserMemory() }
        val decodedChinese = rest.filter(::isChinese)
        val decodedEnglish = rest.filterNot(::isChinese)

        val ordered = if (chineseFirst || buffer.length <= 2) {
            custom + learnedChinese + decodedChinese + learnedEnglish +
                decodedEnglish + literal + english
        } else {
            custom + learnedEnglish + english + literal + decodedEnglish +
                learnedChinese + decodedChinese
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
            .map { it.candidate.asUserMemory() }

        return (learnedChinese + decoded)
            .filter(::isChinese)
            .distinctBy { it.text }
            .take(limit)
    }

    private fun isChinese(candidate: DecodeCandidate): Boolean =
        candidate.type != CandidateType.EN_LITERAL

    private fun DecodeCandidate.asUserMemory(): DecodeCandidate =
        if (sourceSchema == SourceSchema.USER_MEMORY) this
        else copy(sourceSchema = SourceSchema.USER_MEMORY)
}
