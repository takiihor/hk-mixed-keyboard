package com.hkmixedkeyboard.engine

import com.hkmixedkeyboard.decoder.DecodeCandidate

data class ClassifyResult(
    val buffer: String,
    val cnExactParsed: Boolean,
    val cnHasPhraseMatch: Boolean,
    val cnPrefixParsed: Boolean,
    val cnCandidates: List<DecodeCandidate>,
    val enLiteral: String,
    val enAutocomplete: String?,
    val enIsWord: Boolean,
    val enStrongPrefix: Boolean
)
