package com.hkmixedkeyboard.engine

import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.DecoderContract
import com.hkmixedkeyboard.decoder.Scheme

class Classifier(private val decoder: DecoderContract) {

    private val shortWhitelist = EnglishLexicon.SHORT_WHITELIST
    private val canonicalCase = EnglishLexicon.CANONICAL_CASE
    private val englishWords = EnglishLexicon.ENGLISH_WORDS
    private val highFreqCompletions = EnglishLexicon.HIGH_FREQ_COMPLETIONS

    fun classify(buffer: String, scheme: Scheme): ClassifyResult {
        val lower = InputCasePolicy.lookupForm(buffer)
        val dr = decoder.decode(lower, scheme)

        val enIsWord = englishWords.contains(lower) || shortWhitelist.contains(lower)
        val enAutocomplete = highFreqCompletions[lower]?.let {
            InputCasePolicy.applyPattern(it, buffer)
        }
        val enStrongPrefix = buffer.length >= 3 && !dr.isExactCode && enAutocomplete != null

        return ClassifyResult(
            buffer = buffer,
            cnExactParsed = dr.cnExactParsed,
            cnHasPhraseMatch = dr.cnHasPhraseMatch,
            cnPrefixParsed = dr.cnPrefixParsed,
            cnCandidates = rankCandidates(dr.candidates, scheme),
            enLiteral = buffer,
            enAutocomplete = enAutocomplete,
            enIsWord = enIsWord,
            enStrongPrefix = enStrongPrefix
        )
    }

    fun canonicalForm(buffer: String): String = canonicalCase[buffer.lowercase()] ?: buffer

    private fun rankCandidates(
        candidates: List<DecodeCandidate>,
        scheme: Scheme
    ): List<DecodeCandidate> =
        when (scheme) {
            // PinyinDecoder already preserves exact/composed/prefix ordering.
            Scheme.PINYIN -> candidates
            // Keep English→Chinese assists available, but never let their corpus
            // frequency displace a valid Jyutping interpretation of the buffer.
            Scheme.JYUTPING -> candidates.sortedWith(
                compareBy<DecodeCandidate> {
                    if (it.sourceSchema == com.hkmixedkeyboard.decoder.SourceSchema.ENGLISH_ASSIST) 1 else 0
                }
                    .thenByDescending { if (it.isHkCore) 1 else 0 }
                    .thenByDescending { it.frequency }
            )
            else -> candidates.sortedWith(
                compareByDescending<DecodeCandidate> { if (it.isHkCore) 1 else 0 }
                    .thenByDescending { it.frequency }
            )
        }
}
