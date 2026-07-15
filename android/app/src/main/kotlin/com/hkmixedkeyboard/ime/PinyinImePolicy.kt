package com.hkmixedkeyboard.ime

import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.PinyinNormalizer
import com.hkmixedkeyboard.decoder.PinyinLexicon
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.engine.EnglishLexicon
import com.hkmixedkeyboard.settings.InputSchemePreference
import com.hkmixedkeyboard.memory.MemorySuggestion

/** Candidate/display decisions shared by the three production input modes. */
object PinyinImePolicy {
    fun isCorpusReady(lexicon: PinyinLexicon): Boolean = lexicon.isUsable

    fun shouldOfferEnglishCompletions(scheme: Scheme): Boolean =
        !InputSchemePreference.isRomanization(scheme)

    fun isChineseFirst(scheme: Scheme, phraseExact: Boolean): Boolean =
        InputSchemePreference.isRomanization(scheme) || phraseExact

    fun filterLearnedSuggestions(
        scheme: Scheme,
        learned: List<MemorySuggestion>
    ): List<MemorySuggestion> = if (scheme != Scheme.PINYIN) learned else learned.filter {
        it.isExactBuffer &&
            it.candidate.sourceSchema == SourceSchema.PINYIN &&
            it.candidate.text.containsHan()
    }

    fun spaceCandidate(
        scheme: Scheme,
        buffer: String,
        candidates: List<DecodeCandidate>,
        learned: List<MemorySuggestion> = emptyList()
    ): DecodeCandidate? {
        val expectedSource = when (scheme) {
            Scheme.QUICK -> SourceSchema.QUICK
            Scheme.JYUTPING -> SourceSchema.JYUTPING
            Scheme.PINYIN -> SourceSchema.PINYIN
            else -> return null
        }
        val normalized = if (scheme == Scheme.PINYIN) {
            PinyinNormalizer.normalize(buffer) ?: return null
        } else {
            buffer.lowercase()
        }
        if (scheme == Scheme.QUICK && normalized in EnglishLexicon.ENGLISH_WORDS) {
            return null
        }
        if (scheme == Scheme.PINYIN) {
            filterLearnedSuggestions(scheme, learned).firstOrNull()?.let {
                return it.candidate
            }
        }
        return candidates.firstOrNull {
            it.sourceSchema == expectedSource && it.code == normalized
        }
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
