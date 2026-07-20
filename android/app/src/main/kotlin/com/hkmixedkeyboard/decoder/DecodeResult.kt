package com.hkmixedkeyboard.decoder

data class DecodeCandidate(
    val text: String,
    val code: String,
    val sourceSchema: SourceSchema,
    val type: CandidateType,
    val frequency: Double,
    val isHkCore: Boolean,
    val annotation: String? = null
)

data class DecodeResult(
    val buffer: String,
    val scheme: Scheme,
    val consumedLen: Int,
    val isExactCode: Boolean,
    val isPrefixOnly: Boolean,
    val candidates: List<DecodeCandidate>
) {
    // Three parse states required by Gate 1
    val cnExactParsed: Boolean
        get() = isExactCode && candidates.any { it.type == CandidateType.CHAR }

    val cnHasPhraseMatch: Boolean
        get() = isExactCode && candidates.any { it.type == CandidateType.PHRASE }

    val cnPrefixParsed: Boolean
        get() = isPrefixOnly || consumedLen < buffer.length
}
