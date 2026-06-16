package com.hkmixedkeyboard.decoder

class QuickPrefixCandidateIndex(
    exactIndex: Map<String, List<DecodeCandidate>>
) {
    private val prefixCandidates: Map<String, List<DecodeCandidate>> = buildMap {
        val accumulated = HashMap<String, MutableList<DecodeCandidate>>()
        exactIndex.forEach { (code, candidates) ->
            for (length in 1 until code.length) {
                accumulated.getOrPut(code.substring(0, length)) { mutableListOf() }
                    .addAll(candidates)
            }
        }
        accumulated.forEach { (prefix, candidates) ->
            put(
                prefix,
                candidates
                    .sortedWith(
                        compareByDescending<DecodeCandidate> { if (it.isHkCore) 1 else 0 }
                            .thenByDescending { if (it.type == CandidateType.PHRASE) 1 else 0 }
                            .thenByDescending { it.frequency }
                    )
                    .distinctBy { it.text }
                    // Only the top results are ever shown; retaining the full tail for
                    // every prefix of every code (21k chars + 40k phrases) wastes a lot
                    // of heap on a process the OS is quick to kill under memory pressure.
                    .take(MAX_CANDIDATES_PER_PREFIX)
            )
        }
    }

    fun candidates(prefix: String, limit: Int = 12): List<DecodeCandidate> =
        prefixCandidates[prefix].orEmpty().take(limit)

    private companion object {
        const val MAX_CANDIDATES_PER_PREFIX = 24
    }

    fun contains(prefix: String): Boolean = prefixCandidates.containsKey(prefix)
}
