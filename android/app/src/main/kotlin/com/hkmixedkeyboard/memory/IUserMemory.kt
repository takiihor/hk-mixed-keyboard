package com.hkmixedkeyboard.memory

import com.hkmixedkeyboard.decoder.DecodeCandidate

data class MemorySuggestion(
    val candidate: DecodeCandidate,
    val count: Int
)

interface IUserMemory {
    fun record(buffer: String, candidate: DecodeCandidate, isSensitive: Boolean)
    fun cnRatio(buffer: String): Double
    fun hardOverride(buffer: String, isSensitive: Boolean): DecodeCandidate?
    fun suggestions(prefix: String, isSensitive: Boolean, limit: Int = 8): List<MemorySuggestion>
}
