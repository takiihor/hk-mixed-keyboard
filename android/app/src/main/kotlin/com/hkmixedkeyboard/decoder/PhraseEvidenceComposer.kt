package com.hkmixedkeyboard.decoder

import kotlin.math.ln1p

/**
 * Bounded phrase composition over exact dictionary evidence.
 *
 * A valid result must cover the complete romanized input with at least two
 * dictionary chunks, and at least one chunk must map to a reviewed
 * multi-character phrase. This deliberately rejects character-only joins:
 * those look plausible but have no phrase-level language evidence.
 */
class PhraseEvidenceComposer(
    private val exact: Map<String, List<DecodeCandidate>>
) {
    data class Composition(
        val text: String,
        val score: Double,
        val isHkCore: Boolean
    )

    private data class Path(
        val text: String,
        val score: Double,
        val chunks: Int,
        val phraseCharacters: Int,
        val isHkCore: Boolean
    )

    private val maxCodeLength = exact.keys.maxOfOrNull(String::length) ?: 0

    fun compose(input: String, limit: Int = DEFAULT_RESULT_LIMIT): List<Composition> {
        if (input.isEmpty() || input.length > MAX_INPUT_LENGTH || maxCodeLength == 0 || limit <= 0) {
            return emptyList()
        }

        val paths = Array(input.length + 1) { mutableListOf<Path>() }
        paths[0] += Path("", 0.0, 0, 0, true)

        for (start in input.indices) {
            if (paths[start].isEmpty()) continue
            val lastEnd = minOf(input.length, start + maxCodeLength)
            for (end in start + 1..lastEnd) {
                val choices = evidenceChoices(exact[input.substring(start, end)].orEmpty())
                if (choices.isEmpty()) continue
                for (path in paths[start]) {
                    for (candidate in choices) {
                        val characterCount = candidate.text.codePointCount(0, candidate.text.length)
                        paths[end] += Path(
                            text = path.text + candidate.text,
                            score = path.score + ln1p(candidate.frequency.coerceAtLeast(0.0)) +
                                PHRASE_EVIDENCE_BONUS * (characterCount - 1).coerceAtLeast(0),
                            chunks = path.chunks + 1,
                            phraseCharacters = path.phraseCharacters +
                                characterCount.takeIf { it > 1 }.orZero(),
                            isHkCore = path.isHkCore && candidate.isHkCore
                        )
                    }
                }
                paths[end].retainBest(MAX_PATHS_PER_POSITION)
            }
        }

        return paths[input.length]
            .asSequence()
            .filter { it.chunks >= 2 && it.phraseCharacters > 0 }
            .groupBy(Path::text)
            .mapNotNull { (_, alternatives) -> alternatives.minWithOrNull(PATH_ORDER) }
            .sortedWith(PATH_ORDER)
            .take(limit)
            .map { Composition(it.text, it.score, it.isHkCore) }
    }

    private fun evidenceChoices(candidates: List<DecodeCandidate>): List<DecodeCandidate> {
        if (candidates.isEmpty()) return emptyList()
        val ranked = candidates
            .asSequence()
            .filter { it.text.isNotEmpty() }
            .sortedWith(CANDIDATE_ORDER)
            .toList()
        return (ranked.take(TOP_GENERAL_CHOICES) +
            ranked.filter { it.text.codePointCount(0, it.text.length) > 1 }
                .take(TOP_PHRASE_CHOICES))
            .distinctBy(DecodeCandidate::text)
            .take(MAX_CHOICES_PER_CODE)
    }

    private fun MutableList<Path>.retainBest(limit: Int) {
        if (size <= limit) return
        val deduped = groupBy { Pair(it.text, it.phraseCharacters > 0) }
            .mapNotNull { (_, alternatives) -> alternatives.minWithOrNull(PATH_ORDER) }
        val reservedPerClass = limit / 2
        val seeded = (
            deduped.filter { it.phraseCharacters > 0 }.sortedWith(PATH_ORDER)
                .take(reservedPerClass) +
                deduped.filter { it.phraseCharacters == 0 }.sortedWith(PATH_ORDER)
                    .take(reservedPerClass)
            ).toMutableList()
        val selected = seeded.toHashSet()
        val best = (seeded + deduped.asSequence()
            .filterNot(selected::contains)
            .sortedWith(PATH_ORDER)
            .take(limit - seeded.size))
            .sortedWith(PATH_ORDER)
            .take(limit)
        clear()
        addAll(best)
    }

    private fun Int?.orZero(): Int = this ?: 0

    private companion object {
        const val MAX_INPUT_LENGTH = 72
        const val DEFAULT_RESULT_LIMIT = 12
        const val MAX_PATHS_PER_POSITION = 24
        const val TOP_GENERAL_CHOICES = 4
        const val TOP_PHRASE_CHOICES = 2
        const val MAX_CHOICES_PER_CODE = TOP_GENERAL_CHOICES + TOP_PHRASE_CHOICES
        const val PHRASE_EVIDENCE_BONUS = 0.25

        val CANDIDATE_ORDER = compareByDescending<DecodeCandidate> { it.frequency }
            .thenByDescending { it.text.codePointCount(0, it.text.length) }
            .thenBy { it.text }

        // Prefer coverage backed by reviewed multi-character phrases, then the
        // fewest dictionary chunks. Raw frequency is only a tie-breaker: summing
        // positive character scores would otherwise reward implausible over-
        // segmentation (for example ma -> mu + a).
        val PATH_ORDER = compareBy<Path> { -it.phraseCharacters }
            .thenBy { it.chunks }
            .thenBy { -it.score }
            .thenBy { it.text }
    }
}
