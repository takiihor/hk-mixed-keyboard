package com.hkmixedkeyboard.ui

import com.hkmixedkeyboard.decoder.DecodeCandidate

data class CandidateRenderSnapshot(
    val candidates: List<CandidateIdentity>
) {
    data class CandidateIdentity(
        val text: String,
        val type: String,
        val source: String,
        val isHkCore: Boolean
    )

    companion object {
        fun from(candidates: List<DecodeCandidate>) = CandidateRenderSnapshot(
            candidates.map {
                CandidateIdentity(
                    text = it.text,
                    type = it.type.name,
                    source = it.sourceSchema.name,
                    isHkCore = it.isHkCore
                )
            }
        )
    }
}
