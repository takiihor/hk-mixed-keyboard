package com.hkmixedkeyboard.engine

import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.DecoderContract
import com.hkmixedkeyboard.decoder.Scheme

class Classifier(private val decoder: DecoderContract) {

    private val canonicalCase = EnglishLexicon.CANONICAL_CASE

    fun classify(buffer: String, scheme: Scheme): ClassifyResult {
        val lower = InputCasePolicy.lookupForm(buffer)
        val dr = decoder.decode(lower, scheme)

        return ClassifyResult(
            buffer = buffer,
            cnExactParsed = dr.cnExactParsed,
            cnHasPhraseMatch = dr.cnHasPhraseMatch,
            cnPrefixParsed = dr.cnPrefixParsed,
            cnCandidates = rankCandidates(dr.candidates, scheme)
        )
    }

    fun canonicalForm(buffer: String): String = canonicalCase[buffer.lowercase()] ?: buffer

    private fun rankCandidates(
        candidates: List<DecodeCandidate>,
        scheme: Scheme
    ): List<DecodeCandidate> =
        if (scheme == Scheme.PINYIN) candidates else candidates.sortedWith(
            compareByDescending<DecodeCandidate> { if (it.isHkCore) 1 else 0 }
                .thenByDescending { it.frequency }
        )
}
