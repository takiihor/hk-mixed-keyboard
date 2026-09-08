package com.hkmixedkeyboard.engine

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.EnglishAssistEntry
import com.hkmixedkeyboard.decoder.SourceSchema

class EnglishCompletionIndex(
    entries: List<EnglishAssistEntry>,
    /**
     * Hong Kong tokens the CC-CEDICT-derived word list does not carry — MTR,
     * HKD, MPF, FPS and friends. They were curated long ago but only ever fed
     * the classifier's English half, which nothing read, so typing "mt" never
     * completed to MTR. Values give the canonical casing where an acronym has
     * one; a null value completes in the case the user typed.
     */
    localTokens: Map<String, String?> = emptyMap()
) {
    private data class WordFrequency(
        val word: String,
        val frequency: Double,
        val canonical: String?,
        val isLocal: Boolean
    )

    private val words = run {
        val byWord = entries
            .groupingBy { it.english.lowercase() }
            .fold(0.0) { best, entry -> maxOf(best, entry.freq) }
            .toMutableMap()
        localTokens.keys.forEach { token ->
            // Local tokens outrank the general vocabulary for their own prefix:
            // in Hong Kong "mt" means MTR.
            byWord[token] = maxOf(byWord[token] ?: 0.0, LOCAL_TOKEN_FREQUENCY)
        }
        byWord
            .map { (word, frequency) ->
                WordFrequency(word, frequency, localTokens[word], localTokens.containsKey(word))
            }
            .sortedBy { it.word }
    }

    fun forPrefix(prefix: String, limit: Int = 6): List<DecodeCandidate> {
        val lower = InputCasePolicy.lookupForm(prefix)
        if (lower.length < 2 || limit <= 0) return emptyList()

        val start = lowerBound(lower)
        val matches = ArrayList<WordFrequency>()
        var index = start
        while (index < words.size) {
            val entry = words[index]
            if (!entry.word.startsWith(lower)) break
            if (entry.word != lower) matches += entry
            index++
        }
        return matches
            .sortedByDescending { it.frequency }
            .take(limit)
            .map { entry ->
                DecodeCandidate(
                    text = entry.canonical ?: InputCasePolicy.applyPattern(entry.word, prefix),
                    code = "",
                    sourceSchema = SourceSchema.ENGLISH,
                    type = CandidateType.EN_LITERAL,
                    frequency = entry.frequency,
                    // Marks the Hong Kong tokens so the display policy can lift
                    // them clear of a long list of Quick characters.
                    isHkCore = entry.isLocal
                )
            }
    }

    private companion object {
        const val LOCAL_TOKEN_FREQUENCY = 1.0
    }

    private fun lowerBound(target: String): Int {
        var low = 0
        var high = words.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (words[mid].word < target) low = mid + 1 else high = mid
        }
        return low
    }
}
