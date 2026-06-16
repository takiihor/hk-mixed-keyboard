package com.hkmixedkeyboard.memory

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.commit.Thresholds

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

class UserMemory {

    private val store: MutableMap<String, MemoryEntry> = mutableMapOf()

    fun seed(buffer: String, cnText: String, cnCode: String, count: Int, isHkCore: Boolean = false) {
        val cand = DecodeCandidate(cnText, cnCode, SourceSchema.QUICK,
            CandidateType.CHAR, 0.9, isHkCore)
        store[buffer] = MemoryEntry(buffer, cand, count, cnCount = count, enCount = 0)
    }

    fun seedEn(buffer: String, count: Int) {
        val cand = DecodeCandidate(buffer, "", SourceSchema.ENGLISH,
            CandidateType.EN_LITERAL, 0.9, false)
        store[buffer] = MemoryEntry(buffer, cand, count, cnCount = 0, enCount = count)
    }

    fun record(buffer: String, candidate: DecodeCandidate, isSensitive: Boolean) {
        if (isSensitive) return
        val existing = store[buffer]
        val isCn = candidate.type == CandidateType.CHAR || candidate.type == CandidateType.PHRASE
        if (existing == null) {
            store[buffer] = MemoryEntry(buffer, candidate, 1,
                cnCount = if (isCn) 1 else 0, enCount = if (isCn) 0 else 1)
        } else {
            store[buffer] = existing.copy(
                candidate = candidate,
                count = existing.count + 1,
                cnCount = existing.cnCount + if (isCn) 1 else 0,
                enCount = existing.enCount + if (isCn) 0 else 1
            )
        }
    }

    fun exactMatch(buffer: String): MemoryEntry? = store[buffer]

    fun cnRatio(buffer: String): Double = store[buffer]?.cnRatio ?: 0.5

    fun hardOverride(buffer: String, isSensitive: Boolean): DecodeCandidate? {
        if (isSensitive) return null
        val m = store[buffer] ?: return null
        return if (m.count >= Thresholds.USER_MEM_OVERRIDE_MIN_COUNT
            && m.confidence >= Thresholds.USER_MEM_OVERRIDE_MIN_CONF)
            m.candidate else null
    }

    fun clear() = store.clear()
    fun size() = store.size
}
