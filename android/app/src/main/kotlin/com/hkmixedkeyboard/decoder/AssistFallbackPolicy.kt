package com.hkmixedkeyboard.decoder

/**
 * What a romanization scheme offers when its own dictionary produced nothing.
 *
 * 粵拼 and 拼音 returned before ever reaching the English-meaning path, so a
 * romanization typist got the raw Latin literal and nothing else — the 中英
 * feature was absent from two of three modes rather than deprioritised within
 * them. This only ever runs on an otherwise empty result, so a real Cantonese or
 * Mandarin candidate can never be displaced by an English meaning.
 */
object AssistFallbackPolicy {

    const val LIMIT = 8

    /** English meanings first, then code-switching phrases; de-duplicated and capped. */
    fun merge(
        english: List<DecodeCandidate>,
        mixed: List<DecodeCandidate>
    ): List<DecodeCandidate> =
        (english.sortedByDescending { it.frequency } + mixed)
            .distinctBy { it.text }
            .take(LIMIT)
}
