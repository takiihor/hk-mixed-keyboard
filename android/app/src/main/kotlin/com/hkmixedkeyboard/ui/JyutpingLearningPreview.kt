package com.hkmixedkeyboard.ui

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.JyutpingReadingLookup
import com.hkmixedkeyboard.decoder.Scheme

class JyutpingLearningPreview(
    private val readings: JyutpingReadingLookup
) {
    fun liveLabel(scheme: Scheme, candidates: List<DecodeCandidate>): String? {
        if (scheme != Scheme.QUICK) return null
        return candidates.firstNotNullOfOrNull(::label)
    }

    fun committedLabel(scheme: Scheme, candidate: DecodeCandidate): String? =
        if (scheme == Scheme.QUICK) label(candidate) else null

    private fun label(candidate: DecodeCandidate): String? {
        if (candidate.type == CandidateType.EN_LITERAL) return null
        val jyutping = readings.readingFor(candidate.text) ?: return null
        return "${candidate.text} · $jyutping"
    }
}
