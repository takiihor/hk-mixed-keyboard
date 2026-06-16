package com.hkmixedkeyboard.decoder

/**
 * Immutable lookup for user custom words (自訂詞庫), keyed by Quick code.
 * Built from the custom_words table and swapped wholesale when it changes, so it is
 * safe to read on the decode thread while a new one is assembled on the main thread.
 */
class CustomWordIndex(private val byCode: Map<String, List<DecodeCandidate>>) {

    private val prefixSet: Set<String> = buildSet {
        for (code in byCode.keys) for (i in 1..code.length) add(code.substring(0, i))
    }

    fun exact(code: String): List<DecodeCandidate> = byCode[code].orEmpty()

    fun hasPrefix(code: String): Boolean = prefixSet.contains(code)

    /** All candidates whose code starts with [code] (used for in-progress input). */
    fun prefixMatches(code: String): List<DecodeCandidate> =
        if (!hasPrefix(code)) emptyList()
        else byCode.asSequence()
            .filter { it.key.startsWith(code) }
            .flatMap { it.value.asSequence() }
            .toList()

    val isEmpty: Boolean get() = byCode.isEmpty()

    companion object {
        val EMPTY = CustomWordIndex(emptyMap())
    }
}
