package com.hkmixedkeyboard.decoder

/**
 * Chooses which of the committed characters to predict the next one from.
 *
 * [CorpusLoader.nextCharIndex] only holds keys of length 1..[MAX_KEY_LENGTH],
 * but the committed run grows without bound as the user keeps tapping
 * predictions. Looking the whole run up therefore goes permanently null once it
 * passes that length — and starts null outright when the run begins with a
 * four-character phrase candidate, which is exactly when a continuation would be
 * most welcome.
 *
 * So back off to the longest suffix the index actually knows. Suffixes are then
 * tried in decreasing length, most specific first: a continuation supported by
 * three characters of context outranks one supported by a single character,
 * regardless of the raw phrase frequency behind it.
 */
object NextCharPredictionPolicy {

    /** Longest key [CorpusLoader.nextCharIndex] can contain. */
    const val MAX_KEY_LENGTH = 3

    /**
     * Suffixes of [run] to look up, longest first. Empty for an empty run.
     *
     * Counted in code points so a run ending in a supplementary character — the
     * HKSCS range is full of them — is not split through the middle of a
     * surrogate pair into a key that can never match.
     */
    fun lookupKeys(run: String): List<String> {
        if (run.isEmpty()) return emptyList()
        val count = run.codePointCount(0, run.length)
        val longest = minOf(MAX_KEY_LENGTH, count)
        return (longest downTo 1).map { length ->
            run.substring(run.offsetByCodePoints(run.length, -length))
        }
    }

    /**
     * Builds a next-character index over arbitrary text, in the same shape as
     * [CorpusLoader.nextCharIndex]. Used for the user's 自訂詞庫 words, which
     * reach the decoder keyed by Quick code and so were never reachable as
     * continuations: adding 曬冷 let you type its code but never offered 冷 after
     * committing 曬 — the one place a personal dictionary should feel personal.
     */
    fun indexOf(texts: Collection<String>): Map<String, List<DecodeCandidate>> {
        val acc = LinkedHashMap<String, LinkedHashSet<String>>()
        for (text in texts) {
            val count = text.codePointCount(0, text.length)
            if (count < 2) continue
            val longest = minOf(MAX_KEY_LENGTH, count - 1)
            for (length in 1..longest) {
                val split = text.offsetByCodePoints(0, length)
                val prefix = text.substring(0, split)
                val next = text.substring(split, text.offsetByCodePoints(split, 1))
                acc.getOrPut(prefix) { LinkedHashSet() }.add(next)
            }
        }
        return acc.mapValues { (prefix, nexts) ->
            nexts.map {
                // Frequency 1.0: an explicit user mapping outranks any corpus
                // continuation it shares a prefix with.
                DecodeCandidate(it, prefix, SourceSchema.USER_MEMORY, CandidateType.CHAR, 1.0, true)
            }
        }
    }

    /**
     * Continuations for [run], most specific context first and de-duplicated, so
     * a character offered by a longer suffix is never displaced by the same
     * character offered by a shorter one.
     */
    fun predict(
        index: Map<String, List<DecodeCandidate>>,
        run: String
    ): List<DecodeCandidate> {
        val keys = lookupKeys(run)
        if (keys.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<DecodeCandidate>()
        for (key in keys) {
            for (candidate in index[key].orEmpty()) {
                if (seen.add(candidate.text)) out.add(candidate)
            }
        }
        return out
    }
}
