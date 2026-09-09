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

    /**
     * Buckets are keyed by the lowercased buffer. Two things follow: a code typed
     * with and without Shift accumulates one set of counts instead of splitting
     * its learning across case variants, and [suggestions] can walk a contiguous
     * range of the sorted map rather than filtering every bucket. Entries keep the
     * buffer exactly as it was typed.
     */
    private fun key(buffer: String) = buffer.lowercase()

    /** Insert or replace an entry verbatim (used for seeding and cache hydration). */
    fun put(entry: MemoryEntry) {
        byBuffer.computeIfAbsent(key(entry.buffer)) { ConcurrentHashMap() }[entry.candidate.text] = entry
    }

    /**
     * Hydrate cache data without clobbering choices already recorded in this live
     * session. Room init runs asynchronously, so it may finish after the user has
     * already tapped candidates.
     */
    fun putIfNewer(entry: MemoryEntry) {
        val bufMap = byBuffer.computeIfAbsent(key(entry.buffer)) { ConcurrentHashMap() }
        bufMap.compute(entry.candidate.text) { _, existing ->
            if (existing == null || entry.count > existing.count) entry else existing
        }
    }

    /** Record one more selection of [candidate] for [buffer]; returns the updated entry. */
    fun record(buffer: String, candidate: DecodeCandidate): MemoryEntry {
        val isCn = candidate.type != CandidateType.EN_LITERAL
        val bufMap = byBuffer.computeIfAbsent(key(buffer)) { ConcurrentHashMap() }
        // compute() keeps the read-modify-write atomic per (buffer, candidate): a
        // concurrent record or cache hydration (putIfNewer runs on the IO thread during
        // Room init) can no longer lose an increment or clobber a freshly stored entry.
        return bufMap.compute(candidate.text) { _, existing ->
            if (existing == null) {
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
        }!!
    }

    private fun entriesFor(buffer: String): Collection<MemoryEntry> =
        byBuffer[key(buffer)]?.values ?: emptyList()

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
        // Buckets are sorted and lowercase-keyed, so the matches form one
        // contiguous run: start at the prefix and stop at the first key that no
        // longer begins with it, instead of touching every buffer ever learned.
        // At 10k learned buffers the old full scan cost ~0.9 ms of decode latency
        // per keystroke, and ~4 ms at 50k.
        val lower = key(prefix)
        return byBuffer.tailMap(lower, true).asSequence()
            .takeWhile { it.key.startsWith(lower) }
            .flatMap { it.value.values.asSequence() }
            .groupBy { it.candidate.text }
            .map { (_, entries) ->
                val exact = entries.filter { it.buffer.equals(prefix, ignoreCase = true) }
                val selected = (if (exact.isNotEmpty()) exact else entries)
                    .maxWithOrNull(MEMORY_ENTRY_ORDER)!!
                MemorySuggestion(
                    candidate = selected.candidate,
                    count = entries.sumOf { it.count },
                    isExactBuffer = exact.isNotEmpty(),
                    exactCount = exact.sumOf { it.count }
                )
            }
            .sortedWith(
                compareByDescending<MemorySuggestion> { it.exactCount }
                    .thenByDescending { it.count }
                    .thenByDescending { it.candidate.frequency }
                    .thenBy { it.candidate.sourceSchema.name }
                    .thenBy { it.candidate.code }
                    .thenBy { it.candidate.text }
            )
            .take(limit)
    }

    fun clear() = byBuffer.clear()

    fun size() = byBuffer.values.sumOf { it.size }

    private companion object {
        val MEMORY_ENTRY_ORDER = compareBy<MemoryEntry> { it.count }
            .thenBy { it.candidate.frequency }
            .thenByDescending { it.candidate.sourceSchema.name }
            .thenByDescending { it.candidate.code }
            .thenByDescending { it.candidate.text }
    }
}
