package com.hkmixedkeyboard.engine

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.EnglishAssistEntry
import com.hkmixedkeyboard.decoder.SourceSchema

class EnglishCompletionIndex(entries: List<EnglishAssistEntry>) {
    private data class WordFrequency(val word: String, val frequency: Double)

    private val words = entries
        .groupingBy { it.english.lowercase() }
        .fold(0.0) { best, entry -> maxOf(best, entry.freq) }
        .map { (word, frequency) -> WordFrequency(word, frequency) }
        .sortedBy { it.word }

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
                    text = InputCasePolicy.applyPattern(entry.word, prefix),
                    code = "",
                    sourceSchema = SourceSchema.ENGLISH,
                    type = CandidateType.EN_LITERAL,
                    frequency = entry.frequency,
                    isHkCore = false
                )
            }
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
