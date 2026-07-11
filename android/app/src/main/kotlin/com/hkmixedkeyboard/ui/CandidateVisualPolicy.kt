package com.hkmixedkeyboard.ui

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema

object CandidateVisualPolicy {
    fun isPriority(candidate: DecodeCandidate): Boolean =
        candidate.sourceSchema == SourceSchema.USER_MEMORY ||
            candidate.isHkCore ||
            candidate.type == CandidateType.PHRASE ||
            candidate.type == CandidateType.MIXED_PHRASE
}
