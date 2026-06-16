package com.hkmixedkeyboard.memory

import com.hkmixedkeyboard.commit.Thresholds
import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap

/**
 * Buffer-indexed store of [MemoryEntry], shared by [UserMemory] and
 * [RoomUserMemory]. Layout: buffer -> (candidateText -> entry).
 *
 * Indexing by buffer makes the per-keystroke exact / override / ratio lookups
 * O(entries-for-this-buffer) instead of a scan of every entry, and lets prefix
 * suggestions iterate over distinct buffers rather than every (buffer, candidate)
 * pair. Thread-safe: writes happen on the commit thread, reads on the decode thread.
 */
class MemoryIndex {

    private val byBuffer = ConcurrentSkipListMap<String, ConcurrentHashMap<String, MemoryEntry>>()

    /** Insert or replace an entry verbatim (used for seeding and cache hydration). */
    fun put(entry: MemoryEntry) {
        byBuffer.computeIfAbsent(entry.buffer) { ConcurrentHashMap() }[entry.candidate.text] = entry
    }

    /** Record one more selection of [candidate] for [buffer]; returns the updated entry. */
    fun record(buffer: String, candidate: DecodeCandidate): MemoryEntry {
        val isCn = candidate.type != CandidateType.EN_LITERAL
        val bufMap = byBuffer.computeIfAbsent(buffer) { ConcurrentHashMap() }
        val existing = bufMap[candidate.text]
        val updated = if (existing == null) {
            MemoryEntry(buffer, candidate, 1,
                cnCount = if (isCn) 1 else 0, enCount = if (isCn) 0 else 1)
        } else {
            existing.copy(
                candidate = candidate,
                count = existing.count + 1,
                cnCount = existing.cnCount + if (isCn) 1 else 0,
                enCount = existing.enCount + if (isCn) 0 else 1
            )
        }
        bufMap[candidate.text] = updated
        return updated
    }

    private fun entriesFor(buffer: String): Collection<MemoryEntry> =
        byBuffer[buffer]?.values ?: emptyList()

    /** Top entry for [buffer] with counts summed across all of its candidates. */
    fun aggregate(buffer: String): MemoryEntry? {
        val entries = entriesFor(buffer)
        if (entries.isEmpty()) return null
        val top = entries.maxBy { it.count }
        return top.copy(
            count = entries.sumOf { it.count },
            cnCount = entries.sumOf { it.cnCount },
            enCount = entries.sumOf { it.enCount }
        )
    }

    fun hardOverride(buffer: String): DecodeCandidate? {
        val entries = entriesFor(buffer)
        if (entries.isEmpty()) return null
        val top = entries.maxBy { it.count }
        val total = entries.sumOf { it.count }
        val confidence = top.count.toDouble() / total
        return if (total >= Thresholds.USER_MEM_OVERRIDE_MIN_COUNT
            && confidence >= Thresholds.USER_MEM_OVERRIDE_MIN_CONF)
            top.candidate else null
    }

    fun suggestions(prefix: String, limit: Int): List<MemorySuggestion> {
        // Exact-buffer entries are the strongest signal: the user committed this
        // candidate when their buffer was exactly `prefix`. Prefix-only entries
        // (from longer buffers like "au" when typing "a") are predictive only.
        // Sort exact-buffer count first so frequently chosen characters for THIS
        // buffer rank above characters accumulated from deeper buffers.
        val exactEntries = byBuffer[prefix] ?: emptyMap<String, MemoryEntry>()
        return byBuffer.asSequence()
            .filter { it.key.startsWith(prefix, ignoreCase = true) }
            .flatMap { it.value.values.asSequence() }
            .groupBy { it.candidate.text }
            .map { (_, entries) ->
                MemorySuggestion(
                    candidate = entries.maxBy { it.count }.candidate,
                    count = entries.sumOf { it.count }
                )
            }
            .sortedWith(
                compareByDescending<MemorySuggestion> { exactEntries[it.candidate.text]?.count ?: 0 }
                    .thenByDescending { it.count }
                    .thenByDescending { it.candidate.frequency }
            )
            .take(limit)
    }

    fun clear() = byBuffer.clear()

    fun size() = byBuffer.values.sumOf { it.size }
}
