package com.hkmixedkeyboard.decoder

import java.util.PriorityQueue

data class PinyinEntry(val pinyin: String, val chinese: String, val freq: Double)

/** Immutable in-memory indices built once from the cached Pinyin rows. */
class PinyinLexicon(entries: List<PinyinEntry>) {
    val exact: Map<String, List<DecodeCandidate>> = buildExactIndex(entries)

    val isUsable: Boolean get() = exact.isNotEmpty()

    val prefixes = SortedPrefixIndex(exact.keys)

    private val typoKeysByLength: Map<Int, List<String>> = exact.keys.groupBy(String::length)

    private val syllables: Set<String> = exact.entries
        .filter { (_, candidates) -> candidates.any { it.type == CandidateType.CHAR } }
        .mapTo(HashSet()) { it.key }

    val segmenter = PinyinSegmenter(syllables)

    val phraseComposer = PhraseEvidenceComposer(exact)

    fun adjacentTypoCandidates(input: String): List<DecodeCandidate> {
        if (input.length !in 2..MAX_TYPO_INPUT_LENGTH) return emptyList()
        val key = typoKeysByLength[input.length].orEmpty().asSequence()
            .filter { differsByOneAdjacentKey(input, it) }
            .maxByOrNull { candidate -> exact[candidate].orEmpty().maxOfOrNull { it.frequency } ?: 0.0 }
            ?: return emptyList()
        return exact[key].orEmpty().map {
            it.copy(
                sourceSchema = SourceSchema.PINYIN_CORRECTION,
                annotation = "↪ $key"
            )
        }
    }

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

    private fun differsByOneAdjacentKey(input: String, candidate: String): Boolean {
        var differences = 0
        for (index in input.indices) {
            if (input[index] == candidate[index]) continue
            differences++
            if (differences > 1 || candidate[index] !in QWERTY_NEIGHBOURS[input[index]].orEmpty()) {
                return false
            }
        }
        return differences == 1
    }

    private companion object {
        const val MAX_TYPO_INPUT_LENGTH = 72

        val QWERTY_NEIGHBOURS = mapOf(
            'q' to "wa", 'w' to "qeas", 'e' to "wrsd", 'r' to "etdf", 't' to "ryfg",
            'y' to "tugh", 'u' to "yihj", 'i' to "uojk", 'o' to "ipkl", 'p' to "ol",
            'a' to "qwsz", 's' to "weadzx", 'd' to "erfsxc", 'f' to "rtgdvc",
            'g' to "tyfhvb", 'h' to "yugjbn", 'j' to "uihknm", 'k' to "iojlm",
            'l' to "opk", 'z' to "asx", 'x' to "zsdc", 'c' to "xdfv",
            'v' to "cfgb", 'b' to "vghn", 'n' to "bhjm", 'm' to "njk"
        )
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
        // This must stay aligned with Thresholds.PINYIN_MAX_BUFFER_LEN. The
        // bounded input length keeps dynamic programming inexpensive; allowing
        // the same number of syllables avoids a second, lower reachability cap.
        const val MAX_BUFFER_LENGTH = 72
        const val MAX_SYLLABLES = 72
    }
}

class PinyinDecoder(
    private val lexicon: PinyinLexicon,
    private val fuzzyEnabled: () -> Boolean = { false }
) {
    @Volatile private var fuzzyOverride: Boolean? = null

    fun setFuzzyEnabled(enabled: Boolean) {
        fuzzyOverride = enabled
    }

    fun decode(buffer: String): DecodeResult {
        val variants = PinyinNormalizer.variants(buffer, fuzzyOverride ?: fuzzyEnabled())
        if (variants.isEmpty()) return empty(buffer)
        variants.forEachIndexed { index, normalized ->
            val decoded = decodeNormalized(buffer, normalized)
            if (decoded.candidates.isNotEmpty()) {
                return if (index == 0) decoded else decoded.copy(isExactCode = false)
            }
        }
        val strict = variants.first()
        val correction = lexicon.adjacentTypoCandidates(strict)
        if (correction.isNotEmpty()) {
            return DecodeResult(buffer, Scheme.PINYIN, buffer.length, false, false, correction)
        }
        return empty(buffer)
    }

    private fun decodeNormalized(buffer: String, normalized: String): DecodeResult {

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
        val compositions = lexicon.phraseComposer.compose(normalized)
        if (compositions.isEmpty()) return null
        val candidates = compositions.map { composition ->
            DecodeCandidate(
                text = composition.text,
                code = normalized,
                sourceSchema = SourceSchema.PINYIN,
                type = CandidateType.PHRASE,
                frequency = composition.score,
                isHkCore = composition.isHkCore
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

        val PREFIX_QUALITY = compareBy<DecodeCandidate> { it.frequency }
            .thenByDescending { it.code }
            .thenByDescending { it.text }
    }
}
