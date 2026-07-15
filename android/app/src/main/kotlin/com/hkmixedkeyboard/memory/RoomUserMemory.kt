package com.hkmixedkeyboard.memory

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Persisted user memory backed by Room.
 * Keeps an in-memory cache for fast IME callbacks; persists asynchronously.
 */
class RoomUserMemory(
    private val dao: UserMemoryDao,
    private val scope: CoroutineScope
) : IUserMemory {
    private val index = MemoryIndex()

    /**
     * Loads persisted entries into the in-memory index. Entries whose candidate
     * candidate [isStale] deems outdated (e.g. simplified characters removed from the
     * corpus) are deleted from the database instead of hydrated — otherwise a
     * previously learned 因爲 would keep outranking the corrected 因為 forever.
     */
    suspend fun init(isStale: (DecodeCandidate) -> Boolean = { false }) = withContext(Dispatchers.IO) {
        dao.loadAll().forEach { e ->
            val cand = DecodeCandidate(
                text = e.candidateText,
                code = e.candidateCode,
                sourceSchema = runCatching { SourceSchema.valueOf(e.sourceSchema) }.getOrDefault(SourceSchema.ENGLISH),
                type = runCatching { CandidateType.valueOf(e.candidateType) }.getOrDefault(CandidateType.EN_LITERAL),
                frequency = e.frequency,
                isHkCore = e.isHkCore
            )
            if (isStale(cand)) {
                dao.deleteEntry(e.buffer, e.candidateText)
                return@forEach
            }
            index.putIfNewer(MemoryEntry(e.buffer, cand, e.count, e.cnCount, e.enCount))
        }
    }

    override fun record(buffer: String, candidate: DecodeCandidate, isSensitive: Boolean) {
        if (isSensitive) return
        index.record(buffer, candidate)
        index.entriesSnapshot(buffer).forEach(::persist)
    }

    override fun cnRatio(buffer: String): Double = exactMatch(buffer)?.cnRatio ?: 0.5

    override fun hardOverride(buffer: String, isSensitive: Boolean): DecodeCandidate? {
        if (isSensitive) return null
        return index.hardOverride(buffer)
    }

    fun exactMatch(buffer: String): MemoryEntry? = index.aggregate(buffer)

    /** Drop the live in-memory cache (the on-disk table is cleared separately). */
    fun clearCache() = index.clear()

    override fun suggestions(
        prefix: String,
        isSensitive: Boolean,
        limit: Int
    ): List<MemorySuggestion> {
        if (isSensitive || prefix.isBlank()) return emptyList()
        return index.suggestions(prefix, limit)
    }

    fun seed(buffer: String, cnText: String, cnCode: String, count: Int, isHkCore: Boolean = false) {
        val cand = DecodeCandidate(cnText, cnCode, SourceSchema.QUICK, CandidateType.CHAR, 0.9, isHkCore)
        val entry = MemoryEntry(buffer, cand, count, cnCount = count, enCount = 0)
        index.put(entry)
        persist(entry)
    }

    fun seedEn(buffer: String, count: Int) {
        val cand = DecodeCandidate(buffer, "", SourceSchema.ENGLISH, CandidateType.EN_LITERAL, 0.9, false)
        val entry = MemoryEntry(buffer, cand, count, cnCount = 0, enCount = count)
        index.put(entry)
        persist(entry)
    }

    private fun persist(entry: MemoryEntry) {
        scope.launch(Dispatchers.IO) {
            dao.upsert(entry.toEntity())
        }
    }

    private fun MemoryEntry.toEntity() = UserMemoryEntity(
        buffer = buffer,
        candidateText = candidate.text,
        candidateCode = candidate.code,
        sourceSchema = candidate.sourceSchema.name,
        candidateType = candidate.type.name,
        frequency = candidate.frequency,
        isHkCore = candidate.isHkCore,
        count = count,
        cnCount = cnCount,
        enCount = enCount
    )
}
