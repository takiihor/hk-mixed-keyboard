package com.hkmixedkeyboard.decoder

/**
 * Corpus-backed decoder for Stage 2.
 *
 * Decode priority (first match wins):
 *   1. Direct CJK character / phrase input
 *   2. Quick (速成) exact match
 *   3. Quick prefix match
 *   4. English-meaning assist (english_assist.csv) — exact, then prefix
 *   5. Jyutping romanization (jyutping.csv) — exact, then prefix
 *
 * Assist candidates (ENGLISH_ASSIST, JYUTPING) are returned with isExactCode=false,
 * so cnExactParsed and cnHasPhraseMatch remain false → Space never auto-commits them.
 * Users must tap to commit.
 */
class CorpusBackedDecoder(private val corpus: CorpusLoader) : DecoderContract {

    override val schemeName = "CorpusBackedDecoder (Stage 2)"

    // User-defined words (自訂詞庫), keyed by their Quick code. Set by the IME from
    // the custom_words table; consulted before the built-in Quick dictionary so a
    // user's own code → word mapping always wins and is Space-committable. Read on
    // the decode thread, written on the main thread — @Volatile swaps the whole
    // immutable index atomically.
    @Volatile private var customWords: CustomWordIndex = CustomWordIndex.EMPTY

    fun setCustomWords(byCode: Map<String, List<DecodeCandidate>>) {
        customWords = CustomWordIndex(byCode)
    }

    override fun isSchemeAvailable(scheme: Scheme) =
        scheme == Scheme.QUICK || scheme == Scheme.CANGJIE ||
            scheme == Scheme.JYUTPING || scheme == Scheme.PINYIN

    override fun decode(buffer: String, scheme: Scheme): DecodeResult {
        if (scheme == Scheme.MIXED_EXPERIMENTAL) return empty(buffer, scheme)

        if (scheme == Scheme.PINYIN) return corpus.pinyinDecoder.decode(buffer)

        // JYUTPING primary mode: treat Latin buffer as Jyutping and surface
        // characters directly as committable (cnExactParsed=true) so Space commits.
        if (scheme == Scheme.JYUTPING) {
            val lower = buffer.lowercase()
            // Exact match
            corpus.jyutpingIndex[lower]?.let { cands ->
                val mapped = cands.map { it.copy(sourceSchema = SourceSchema.JYUTPING, type = CandidateType.CHAR) }
                return DecodeResult(buffer, scheme, buffer.length, true, false, mapped)
            }
            // Prefix matches (incremental: a still-incomplete code that begins a
            // dictionary key, including concatenated phrase keys like "hoenggong").
            if (corpus.jyutpingPrefixIndex.hasPrefix(lower)) {
                val pref = corpus.jyutpingPrefixIndex.matching(lower, limit = 24)
                    .flatMap { corpus.jyutpingIndex[it].orEmpty() }
                if (pref.isNotEmpty()) {
                    val mapped = pref.map { it.copy(sourceSchema = SourceSchema.JYUTPING, type = CandidateType.CHAR) }
                    return DecodeResult(buffer, scheme, buffer.length, false, true, mapped)
                }
            }
            // Multi-syllable segmentation: continuous romanization that is neither a
            // single syllable nor a dictionary phrase key (e.g. "neihou" → 你好).
            composeSegmentedPhrase(lower)?.let { return it }
            return empty(buffer, scheme)
        }

        // 1. Direct CJK input
        if (buffer.isNotEmpty() && buffer.all { it.code > 0x2E80 })
            return decodeCjkDirect(buffer, scheme)

        val lower = buffer.lowercase()

        // 2. Exact match — user custom words first, then the built-in Quick dictionary.
        //    Both are committable (cnExactParsed=true), so Space commits the top one.
        val customExact = customWords.exact(lower)
        val quickExact = corpus.quickIndex[lower]
        if (customExact.isNotEmpty() || quickExact != null) {
            return DecodeResult(buffer, scheme, buffer.length, true, false,
                customExact + quickExact.orEmpty())
        }

        // 3. Prefix match — custom words first, then Quick prefixes.
        val customPrefix = customWords.prefixMatches(lower)
        val hasQuickPrefix = corpus.quickPrefixCandidateIndex.contains(lower)
        val quickPrefix = if (hasQuickPrefix)
            corpus.quickPrefixCandidateIndex.candidates(lower) else emptyList()

        // Keep incomplete Quick choices, but never let one hide an exact English
        // meaning (for example cat before cati). Exact custom and Quick codes above
        // retain priority, and this merged result remains tap-only.
        if (corpus.englishAssistIndex.containsKey(lower)) {
            val (assistCands, _) = buildAssistCandidates(lower)
            val exactAssist = assistCands.filter {
                it.sourceSchema == SourceSchema.ENGLISH_ASSIST && it.code == lower
            }
            val remainingAssist = assistCands.filterNot {
                it.sourceSchema == SourceSchema.ENGLISH_ASSIST && it.code == lower
            }
            val cands = (exactAssist + customPrefix + quickPrefix + remainingAssist)
                .distinctBy { it.text }
            return DecodeResult(buffer, scheme, buffer.length, false, false, cands)
        }

        if (customPrefix.isNotEmpty() || hasQuickPrefix) {
            val cands = customPrefix + quickPrefix
            if (cands.isNotEmpty())
                return DecodeResult(buffer, scheme, buffer.length, false, true, cands)
        }

        // 4 + 5. English assist and Jyutping fallback
        val (assistCands, isPrefixOnly) = buildAssistCandidates(lower)
        if (assistCands.isNotEmpty())
            return DecodeResult(buffer, scheme, buffer.length, false, isPrefixOnly, assistCands)

        return empty(buffer, scheme)
    }

    /**
     * Combines English-meaning and Jyutping candidates for a buffer that had no Quick match.
     * Returns (candidates, isPrefixOnly). isPrefixOnly=true when ALL results came from prefix
     * lookups (no exact entry matched the buffer itself).
     */
    private fun buildAssistCandidates(lower: String): Pair<List<DecodeCandidate>, Boolean> {
        // Ranking honours the decode priority: English meaning (4) before Jyutping
        // fallback (5), and within each source the exact match (the buffer IS this
        // word / syllable) before prefix completions — so typing "act" surfaces its
        // own 動作 before action→作用, and an English word like "cat" keeps its
        // meanings ahead of Cantonese homophones (cat = 柒/七) that a Quick-mode
        // typist didn't intend.
        val englishExact = mutableListOf<DecodeCandidate>()
        val englishPrefix = mutableListOf<DecodeCandidate>()
        val jyutpingExact = mutableListOf<DecodeCandidate>()
        val jyutpingPrefix = mutableListOf<DecodeCandidate>()

        // English assist — exact
        corpus.englishAssistIndex[lower]?.let { englishExact.addAll(it) }

        // English assist — prefix completions. Run even when there is an exact
        // match so typing "disc" surfaces discuss→討論 alongside the exact
        // disc→碟片 (English autocomplete → Chinese meaning).
        if (lower.length >= 3 && corpus.englishAssistPrefixIndex.hasPrefix(lower)) {
            corpus.englishAssistPrefixIndex.matching(lower, limit = 24)
                .asSequence()
                .filter { it != lower }
                .flatMap { corpus.englishAssistIndex[it].orEmpty().asSequence() }
                .forEach { englishPrefix.add(it) }
        }

        // Jyutping — exact
        corpus.jyutpingIndex[lower]?.let { jyutpingExact.addAll(it) }

        // Jyutping — prefix (only if no exact Jyutping match)
        if (!corpus.jyutpingIndex.containsKey(lower) && corpus.jyutpingPrefixIndex.hasPrefix(lower)) {
            corpus.jyutpingPrefixIndex.matching(lower, limit = 24)
                .flatMapTo(jyutpingPrefix) { corpus.jyutpingIndex[it].orEmpty() }
        }

        val hasExact = englishExact.isNotEmpty() || jyutpingExact.isNotEmpty()
        if (!hasExact && englishPrefix.isEmpty() && jyutpingPrefix.isEmpty())
            return Pair(emptyList(), false)

        // English (exact → prefix) then Jyutping (exact → prefix); frequency orders
        // within each group. distinctBy keeps the first, highest-priority copy.
        val deduped = (englishExact.sortedByDescending { it.frequency } +
            englishPrefix.sortedByDescending { it.frequency } +
            jyutpingExact.sortedByDescending { it.frequency } +
            jyutpingPrefix.sortedByDescending { it.frequency })
            .distinctBy { it.text }
            .take(15)

        return Pair(deduped, !hasExact)
    }

    /**
     * Segments a continuous Jyutping buffer and composes phrase candidates from the
     * per-syllable readings. The primary candidate joins each syllable's top reading
     * (求其-style); a handful of alternates vary the first and last syllable so the
     * user still has choices. Returns null if the buffer doesn't fully segment or any
     * syllable lacks a single-character reading.
     */
    private fun composeSegmentedPhrase(lower: String): DecodeResult? {
        val segments = corpus.jyutpingSegmenter.segment(lower) ?: return null

        // Per-syllable single-character readings, most frequent first.
        val perSyllable = segments.map { syl ->
            corpus.jyutpingIndex[syl].orEmpty()
                .asSequence()
                .filter { it.text.length == 1 }
                .sortedByDescending { it.frequency }
                .toList()
        }
        if (perSyllable.any { it.isEmpty() }) return null

        val tops = perSyllable.map { it.first().text }
        val ordered = LinkedHashSet<String>()
        ordered.add(tops.joinToString(""))                              // primary
        for (alt in perSyllable.last().take(ALT_PER_SYLLABLE))          // vary last
            ordered.add((tops.dropLast(1) + alt.text).joinToString(""))
        for (alt in perSyllable.first().take(ALT_PER_SYLLABLE))         // vary first
            ordered.add((listOf(alt.text) + tops.drop(1)).joinToString(""))

        var freq = 1.0
        val candidates = ordered.take(MAX_COMPOSED).map { text ->
            DecodeCandidate(text, lower, SourceSchema.JYUTPING, CandidateType.PHRASE,
                freq.also { freq -= 0.01 }, false)
        }
        // isExactCode=true + PHRASE type ⇒ cnHasPhraseMatch, so the composed phrase
        // surfaces as the top candidate in the bar (one tap to commit).
        return DecodeResult(lower, Scheme.JYUTPING, lower.length, true, false, candidates)
    }

    private fun decodeCjkDirect(buffer: String, scheme: Scheme): DecodeResult {
        if (buffer.length == 1) {
            val charEntry = corpus.charByText[buffer]
            if (charEntry != null) {
                return DecodeResult(buffer, scheme, 1, true, false, listOf(
                    DecodeCandidate(charEntry.char, charEntry.quickCode,
                        SourceSchema.QUICK, CandidateType.CHAR, charEntry.freq, charEntry.isHkCore)
                ))
            }
        }
        val phraseEntry = corpus.phraseByText[buffer]
        if (phraseEntry != null) {
            return DecodeResult(buffer, scheme, buffer.length, true, false, listOf(
                DecodeCandidate(phraseEntry.phrase, phraseEntry.quickCode,
                    SourceSchema.QUICK, CandidateType.PHRASE, phraseEntry.freq, phraseEntry.isHkCore)
            ))
        }
        return empty(buffer, scheme)
    }

    private fun empty(buffer: String, scheme: Scheme) =
        DecodeResult(buffer, scheme, buffer.length, false, false, emptyList())

    private companion object {
        // Alternate readings to offer for the first and last syllable of a composed
        // phrase, and the overall cap on composed candidates.
        const val ALT_PER_SYLLABLE = 4
        const val MAX_COMPOSED = 12
    }
}
