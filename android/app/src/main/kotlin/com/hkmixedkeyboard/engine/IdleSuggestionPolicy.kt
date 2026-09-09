package com.hkmixedkeyboard.engine

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.memory.MemorySuggestion

/**
 * What the candidate strip shows before anything has been typed.
 *
 * The strip reserves a fixed 42–58dp whether or not it has content, and on a
 * fresh field it had none — which reads as a broken bar rather than as breathing
 * room, since iOS never leaves that space idle. There is nothing to predict from
 * yet, so the honest thing to offer is what this user reaches for most: their own
 * most-committed words, falling back to everyday Hong Kong phrases until they
 * have a history worth showing.
 */
object IdleSuggestionPolicy {

    const val LIMIT = 8

    /** Everyday Cantonese, used until the user's own history has enough in it. */
    val STARTERS: List<String> = listOf(
        "唔該", "多謝", "好呀", "係咪", "點解", "而家", "邊度", "冇問題"
    )

    fun suggestions(learned: List<MemorySuggestion>): List<DecodeCandidate> {
        val mine = learned.asSequence()
            .map { it.candidate }
            .filter { it.type != CandidateType.EN_LITERAL && it.text.isNotBlank() }
            .distinctBy { it.text }
            .take(LIMIT)
            .toList()
        if (mine.size >= LIMIT) return mine
        // Top up rather than replace, so a light user still sees their own words
        // first and the strip is never half empty.
        val seen = mine.mapTo(HashSet()) { it.text }
        return mine + STARTERS.asSequence()
            .filterNot { it in seen }
            .map(::starter)
            .take(LIMIT - mine.size)
            .toList()
    }

    private fun starter(text: String) = DecodeCandidate(
        text, "", SourceSchema.QUICK, CandidateType.PHRASE, 0.0, isHkCore = true
    )
}
