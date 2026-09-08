package com.hkmixedkeyboard.engine

import com.hkmixedkeyboard.decoder.DecodeCandidate

/**
 * What the candidate bar needs from one decode.
 *
 * This used to carry an English half too — enIsWord, enAutocomplete and
 * enStrongPrefix — computed on every keystroke and read by nothing. Space and
 * punctuation stopped consulting the classifier when they became unconditionally
 * literal, and no caller was ever wired back up. The live English paths are
 * EnglishCompletionIndex for the bar and EnglishLexicon.CANONICAL_CASE for
 * commit casing.
 */
data class ClassifyResult(
    val buffer: String,
    val cnExactParsed: Boolean,
    val cnHasPhraseMatch: Boolean,
    val cnPrefixParsed: Boolean,
    val cnCandidates: List<DecodeCandidate>
)
