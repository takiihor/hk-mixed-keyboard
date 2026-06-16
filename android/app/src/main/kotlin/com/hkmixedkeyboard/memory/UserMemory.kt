package com.hkmixedkeyboard.memory

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema

data class MemoryEntry(
    val buffer: String,
    val candidate: DecodeCandidate,
    val count: Int,
    val cnCount: Int,
    val enCount: Int
) {
    // Fraction of times the dominant choice was made: 3 consistent CN picks → 1.0
    val confidence: Double get() = if (count == 0) 0.0 else maxOf(cnCount, enCount).toDouble() / count
    val cnRatio: Double get() = if (count == 0) 0.5 else cnCount.toDouble() / count
}

class UserMemory : IUserMemory {

    // buffer -> (candidateText -> entry). Indexing by buffer keeps the per-keystroke
    // exact/override lookups O(entries-for-this-buffer) instead of scanning every
    // entry, and lets prefix suggestions iterate only over distinct buffers.
    private val index = MemoryIndex()

    fun seed(buffer: String, cnText: String, cnCode: String, count: Int, isHkCore: Boolean = false) {
        val cand = DecodeCandidate(cnText, cnCode, SourceSchema.QUICK,
            CandidateType.CHAR, 0.9, isHkCore)
        index.put(MemoryEntry(buffer, cand, count, cnCount = count, enCount = 0))
    }

    fun seedEn(buffer: String, count: Int) {
        val cand = DecodeCandidate(buffer, "", SourceSchema.ENGLISH,
            CandidateType.EN_LITERAL, 0.9, false)
        index.put(MemoryEntry(buffer, cand, count, cnCount = 0, enCount = count))
    }

    override fun record(buffer: String, candidate: DecodeCandidate, isSensitive: Boolean) {
        if (isSensitive) return
        index.record(buffer, candidate)
    }

    fun exactMatch(buffer: String): MemoryEntry? = index.aggregate(buffer)

    override fun cnRatio(buffer: String): Double = exactMatch(buffer)?.cnRatio ?: 0.5

    override fun hardOverride(buffer: String, isSensitive: Boolean): DecodeCandidate? {
        if (isSensitive) return null
        return index.hardOverride(buffer)
    }

    override fun suggestions(
        prefix: String,
        isSensitive: Boolean,
        limit: Int
    ): List<MemorySuggestion> {
        if (isSensitive || prefix.isBlank()) return emptyList()
        return index.suggestions(prefix, limit)
    }

    fun clear() = index.clear()
    fun size() = index.size()
}
