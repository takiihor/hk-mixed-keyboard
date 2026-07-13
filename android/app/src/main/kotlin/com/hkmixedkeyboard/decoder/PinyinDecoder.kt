package com.hkmixedkeyboard.decoder

import java.util.Locale
import java.util.PriorityQueue

data class PinyinEntry(val pinyin: String, val chinese: String, val freq: Double)

object PinyinNormalizer {
    private val UMLAUT_AFTER_STANDARD_INITIAL = Regex("([jqxy])v")

    fun normalize(input: String): String? {
        if (input.isEmpty()) return null
        val lower = input.lowercase(Locale.ROOT)
            .replace("u:", "v")
            .replace('ü', 'v')
        if (!lower.all { it in 'a'..'z' }) return null
        return lower.replace(UMLAUT_AFTER_STANDARD_INITIAL, "$1u")
    }
}

/** Immutable in-memory indices built once from the cached Pinyin rows. */
class PinyinLexicon(entries: List<PinyinEntry>) {
    val exact: Map<String, List<DecodeCandidate>> = buildExactIndex(entries)

    val isUsable: Boolean get() = exact.isNotEmpty()

    val prefixes = SortedPrefixIndex(exact.keys)

    private val syllables: Set<String> = exact.entries
        .filter { (_, candidates) -> candidates.any { it.type == CandidateType.CHAR } }
        .mapTo(HashSet()) { it.key }

    val segmenter = PinyinSegmenter(syllables)

    private fun buildExactIndex(
        entries: List<PinyinEntry>
    ): Map<String, List<DecodeCandidate>> {
        val byKey = HashMap<String, HashMap<String, DecodeCandidate>>()
        for (row in entries) {
            if (!row.chinese.isHanOnly() || !MandarinSyllables.covers(row.pinyin)) continue
            val byText = byKey.getOrPut(row.pinyin) { HashMap() }
            val candidate = DecodeCandidate(
                text = row.chinese,
                code = row.pinyin,
                sourceSchema = SourceSchema.PINYIN,
                type = if (row.chinese.codePointCount(0, row.chinese.length) == 1)
                    CandidateType.CHAR else CandidateType.PHRASE,
                frequency = row.freq,
                isHkCore = false
            )
            val previous = byText[row.chinese]
            if (previous == null || candidate.frequency > previous.frequency)
                byText[row.chinese] = candidate
        }
        return byKey.mapValues { (_, byText) ->
            byText.values.sortedWith(
                compareByDescending<DecodeCandidate> { it.frequency }
                    .thenBy { it.text }
            )
        }
    }
}

private fun String.isHanOnly(): Boolean {
    if (isEmpty()) return false
    var offset = 0
    while (offset < length) {
        val codePoint = codePointAt(offset)
        if (Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.HAN)
            return false
        offset += Character.charCount(codePoint)
    }
    return true
}

/** Bounded dynamic-programming segmentation for continuous, toneless Pinyin. */
class PinyinSegmenter(private val syllables: Set<String>) {
    private val maxSyllableLength = syllables.maxOfOrNull { it.length } ?: 0

    fun segment(buffer: String): List<String>? {
        if (buffer.isEmpty() || buffer.length > MAX_BUFFER_LENGTH || maxSyllableLength == 0)
            return null

        val minCount = IntArray(buffer.length + 1) { Int.MAX_VALUE }
        val back = IntArray(buffer.length + 1) { -1 }
        minCount[0] = 0

        for (end in 1..buffer.length) {
            val firstStart = maxOf(0, end - maxSyllableLength)
            for (start in firstStart until end) {
                if (minCount[start] == Int.MAX_VALUE || minCount[start] >= MAX_SYLLABLES)
                    continue
                if (buffer.substring(start, end) !in syllables) continue
                val candidateCount = minCount[start] + 1
                if (candidateCount < minCount[end]) {
                    minCount[end] = candidateCount
                    back[end] = start
                }
            }
        }

        if (minCount[buffer.length] !in 2..MAX_SYLLABLES) return null
        val result = ArrayDeque<String>()
        var end = buffer.length
        while (end > 0) {
            val start = back[end]
            if (start < 0) return null
            result.addFirst(buffer.substring(start, end))
            end = start
        }
        return result.toList()
    }

    private companion object {
        const val MAX_BUFFER_LENGTH = 48
        const val MAX_SYLLABLES = 8
    }
}

class PinyinDecoder(private val lexicon: PinyinLexicon) {
    fun decode(buffer: String): DecodeResult {
        val normalized = PinyinNormalizer.normalize(buffer)
            ?: return empty(buffer)

        val exactCandidates = lexicon.exact[normalized]
        val prefixCandidates = longerPrefixCandidates(
            normalized,
            exactCandidates.orEmpty().mapTo(HashSet()) { it.text }
        )
        exactCandidates?.let { exact ->
            return DecodeResult(
                buffer = buffer,
                scheme = Scheme.PINYIN,
                consumedLen = buffer.length,
                isExactCode = true,
                isPrefixOnly = false,
                candidates = (exact + prefixCandidates).distinctBy { it.text }
            )
        }

        compose(buffer, normalized)?.let { composed ->
            return composed.copy(
                candidates = mergeComposedWithPrefixes(composed.candidates, prefixCandidates)
            )
        }

        if (prefixCandidates.isNotEmpty()) {
            return DecodeResult(
                buffer = buffer,
                scheme = Scheme.PINYIN,
                consumedLen = buffer.length,
                isExactCode = false,
                isPrefixOnly = true,
                candidates = prefixCandidates
            )
        }

        return empty(buffer)
    }

    private fun mergeComposedWithPrefixes(
        composed: List<DecodeCandidate>,
        prefixes: List<DecodeCandidate>
    ): List<DecodeCandidate> {
        if (prefixes.isEmpty()) return composed
        val prefixScore = composed.associateWith { candidate ->
            prefixes.asSequence()
                .filter { it.text.startsWith(candidate.text) }
                .maxOfOrNull { it.frequency }
        }
        val promoted = composed.filter { prefixScore[it] != null }
            .sortedByDescending { prefixScore[it] }
        return (promoted + composed.filterNot { it in promoted } + prefixes)
            .distinctBy { it.text }
    }

    private fun longerPrefixCandidates(
        normalized: String,
        excludedTexts: Set<String>
    ): List<DecodeCandidate> {
        if (!lexicon.prefixes.hasPrefix(normalized)) return emptyList()

        val selected = HashMap<String, DecodeCandidate>(PREFIX_CANDIDATE_LIMIT)
        val leastCommonFirst = PriorityQueue(PREFIX_CANDIDATE_LIMIT, PREFIX_QUALITY)
        lexicon.prefixes.forEachMatching(normalized) { key ->
            if (key == normalized) return@forEachMatching
            for (candidate in lexicon.exact[key].orEmpty()) {
                if (candidate.text in excludedTexts) continue
                val previous = selected[candidate.text]
                if (previous != null) {
                    if (PREFIX_QUALITY.compare(candidate, previous) > 0) {
                        leastCommonFirst.remove(previous)
                        leastCommonFirst += candidate
                        selected[candidate.text] = candidate
                    }
                    continue
                }

                if (leastCommonFirst.size < PREFIX_CANDIDATE_LIMIT) {
                    leastCommonFirst += candidate
                    selected[candidate.text] = candidate
                } else if (PREFIX_QUALITY.compare(candidate, leastCommonFirst.peek()) > 0) {
                    selected.remove(leastCommonFirst.remove().text)
                    leastCommonFirst += candidate
                    selected[candidate.text] = candidate
                }
            }
        }
        return leastCommonFirst.sortedWith(PREFIX_QUALITY.reversed())
    }

    private fun compose(buffer: String, normalized: String): DecodeResult? {
        val segments = lexicon.segmenter.segment(normalized) ?: return null
        val perSyllable = segments.map { syllable ->
            lexicon.exact[syllable].orEmpty()
                .asSequence()
                .filter { it.type == CandidateType.CHAR }
                .take(ALT_PER_SYLLABLE)
                .toList()
        }
        if (perSyllable.any { it.isEmpty() }) return null

        val top = perSyllable.map { it.first().text }
        val composed = LinkedHashSet<String>()
        composed += top.joinToString("")
        for (alternate in perSyllable.last()) {
            composed += (top.dropLast(1) + alternate.text).joinToString("")
        }
        for (alternate in perSyllable.first()) {
            composed += (listOf(alternate.text) + top.drop(1)).joinToString("")
        }

        val candidates = composed.take(MAX_COMPOSED).mapIndexed { index, text ->
            DecodeCandidate(
                text = text,
                code = normalized,
                sourceSchema = SourceSchema.PINYIN,
                type = CandidateType.PHRASE,
                frequency = 1.0 - index * 0.01,
                isHkCore = false
            )
        }
        return DecodeResult(
            buffer = buffer,
            scheme = Scheme.PINYIN,
            consumedLen = buffer.length,
            isExactCode = true,
            isPrefixOnly = false,
            candidates = candidates
        )
    }

    private fun empty(buffer: String) = DecodeResult(
        buffer = buffer,
        scheme = Scheme.PINYIN,
        consumedLen = buffer.length,
        isExactCode = false,
        isPrefixOnly = false,
        candidates = emptyList()
    )

    private companion object {
        const val PREFIX_CANDIDATE_LIMIT = 24
        const val ALT_PER_SYLLABLE = 4
        const val MAX_COMPOSED = 12

        val PREFIX_QUALITY = compareBy<DecodeCandidate> { it.frequency }
            .thenByDescending { it.code }
            .thenByDescending { it.text }
    }
}
