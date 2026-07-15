package com.hkmixedkeyboard.ime

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.JyutpingNormalizer
import com.hkmixedkeyboard.decoder.PinyinNormalizer
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.memory.MemorySuggestion

object CandidateCommitPolicy {
    /** A Chinese→English assist is a standalone insertion, not a word-chain link. */
    fun continuesChinesePrediction(candidate: DecodeCandidate): Boolean =
        candidate.sourceSchema != SourceSchema.CHINESE_ASSIST && candidate.text.containsHan()

    fun isEligibleForSpace(candidate: DecodeCandidate?, scheme: Scheme, buffer: String): Boolean {
        return isEligibleForPunctuation(candidate, scheme, buffer)
    }

    fun isEligibleForPunctuation(
        candidate: DecodeCandidate?,
        scheme: Scheme,
        buffer: String
    ): Boolean {
        candidate ?: return false
        val normalized = when (scheme) {
            Scheme.JYUTPING -> JyutpingNormalizer.normalize(buffer)?.key
            Scheme.PINYIN -> PinyinNormalizer.normalize(buffer)
            else -> buffer.lowercase()
        } ?: return false
        if (candidate.code != normalized || !candidate.text.containsHan()) {
            return false
        }
        return when (scheme) {
            Scheme.QUICK -> candidate.sourceSchema == SourceSchema.USER_MEMORY ||
                candidate.sourceSchema == SourceSchema.CUSTOM_QUICK ||
                candidate.sourceSchema == SourceSchema.QUICK &&
                candidate.type == CandidateType.PHRASE
            Scheme.JYUTPING -> candidate.sourceSchema == SourceSchema.JYUTPING ||
                candidate.sourceSchema == SourceSchema.CUSTOM_JYUTPING
            Scheme.PINYIN -> candidate.sourceSchema == SourceSchema.PINYIN ||
                candidate.sourceSchema == SourceSchema.CUSTOM_PINYIN
            else -> false
        }
    }

    fun selectForAutoCommit(
        scheme: Scheme,
        buffer: String,
        candidates: List<DecodeCandidate>,
        learned: List<MemorySuggestion> = emptyList()
    ): DecodeCandidate? {
        val selected = if (scheme == Scheme.PINYIN) {
            PinyinImePolicy.spaceCandidate(scheme, buffer, candidates, learned)
        } else {
            candidates.firstOrNull { isEligibleForPunctuation(it, scheme, buffer) }
        }
        return selected?.takeIf { isEligibleForPunctuation(it, scheme, buffer) }
    }
}

private fun String.containsHan(): Boolean {
    var offset = 0
    while (offset < length) {
        val codePoint = codePointAt(offset)
        if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) return true
        offset += Character.charCount(codePoint)
    }
    return false
}
