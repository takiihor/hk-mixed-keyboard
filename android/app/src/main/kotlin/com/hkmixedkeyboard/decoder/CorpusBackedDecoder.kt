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

    // User-defined words (自訂詞庫), keyed by normalized code for each scheme. Set by
    // the IME from the custom_words table; consulted before the built-in dictionary
    // so a user's own code → word mapping wins candidate ranking and remains selectable by tap.
    // Read on the decode thread, written on the main thread — @Volatile swaps the whole
    // immutable index atomically.
    @Volatile private var customWordsByScheme: Map<Scheme, CustomWordIndex> = emptyMap()

    private val jyutpingPhraseComposer by lazy { PhraseEvidenceComposer(corpus.jyutpingIndex) }

    fun setCustomWords(byCode: Map<String, List<DecodeCandidate>>) {
        setCustomWordsByScheme(mapOf(Scheme.QUICK to byCode))
    }

    fun setCustomWordsByScheme(byScheme: Map<Scheme, Map<String, List<DecodeCandidate>>>) {
        customWordsByScheme = byScheme.mapValues { CustomWordIndex(it.value) }
    }

    override fun isSchemeAvailable(scheme: Scheme) =
        scheme == Scheme.QUICK || scheme == Scheme.CANGJIE ||
            scheme == Scheme.JYUTPING || scheme == Scheme.PINYIN

    override fun decode(buffer: String, scheme: Scheme): DecodeResult {
        if (scheme == Scheme.MIXED_EXPERIMENTAL) return empty(buffer, scheme)

        val lower = buffer.lowercase()
        if (HkscsSupplement.isUnicodeFallbackInput(lower)) {
            corpus.hkscsUnicodeFallbackIndex[lower]?.let { fallback ->
                return DecodeResult(buffer, scheme, buffer.length, false, false, listOf(fallback))
            }
        }

        if (scheme == Scheme.PINYIN) {
            val normalized = PinyinNormalizer.normalize(buffer)
            val customIndex = customWordsByScheme[Scheme.PINYIN]
            val custom = normalized?.let {
                customIndex?.exact(it)
            }.orEmpty()
            if (custom.isNotEmpty()) {
                return appendEnglishAssist(
                    DecodeResult(buffer, scheme, buffer.length, true, false, custom),
                    normalized.orEmpty()
                )
            }
            val decoded = corpus.pinyinDecoder.decode(buffer)
            val customPrefix = normalized?.let { customIndex?.prefixMatches(it) }.orEmpty()
                .filterNot { candidate -> custom.any { it.text == candidate.text } }
            val combined = if (customPrefix.isEmpty()) decoded else decoded.copy(
                isPrefixOnly = decoded.candidates.isEmpty() || decoded.isPrefixOnly,
                candidates = (customPrefix + decoded.candidates).distinctBy { it.text }
            )
            return appendEnglishAssist(combined, normalized ?: lower)
        }

        // JYUTPING primary mode: treat Latin buffer as Jyutping and surface
        // characters directly as committable (cnExactParsed=true) so Space commits.
        if (scheme == Scheme.JYUTPING) {
            val normalizedInput = JyutpingNormalizer.normalize(buffer)
                ?: return appendEnglishAssist(empty(buffer, scheme), lower)
            val normalized = normalizedInput.key
            val customIndex = customWordsByScheme[Scheme.JYUTPING]
            val custom = customIndex?.exact(normalized).orEmpty()
            if (custom.isNotEmpty()) {
                return appendEnglishAssist(
                    DecodeResult(buffer, scheme, buffer.length, true, false, custom),
                    normalized
                )
            }
            // Exact match
            corpus.jyutpingIndex[normalized]?.let { cands ->
                val typedAnnotation = JyutpingAnnotation.forInput(buffer)
                val usesTones = normalizedInput.toneBySyllable.any { it != null }
                val ranked = if (usesTones) {
                    cands.withIndex().sortedWith(
                        compareByDescending<IndexedValue<DecodeCandidate>> {
                            if (JYUTPING_TONAL_PREFERENCES[typedAnnotation] == it.value.text) 1
                            else 0
                        }.thenByDescending {
                            corpus.jyutpingToneIndex.classify(it.value.text, normalizedInput)
                                .rankingPriority
                        }.thenBy { it.index }
                    ).map { it.value }
                } else {
                    cands
                }
                val mapped = ranked.map {
                    val toneMatch = if (usesTones) {
                        corpus.jyutpingToneIndex.classify(it.text, normalizedInput)
                    } else {
                        JyutpingToneMatch.UNKNOWN
                    }
                    it.copy(
                        sourceSchema = SourceSchema.JYUTPING,
                        type = if (it.text.codePointCount(0, it.text.length) == 1) {
                            CandidateType.CHAR
                        } else {
                            CandidateType.PHRASE
                        },
                        annotation = when {
                            usesTones && toneMatch == JyutpingToneMatch.MATCH -> typedAnnotation
                            else -> jyutpingAnnotation(it.text)
                        }
                    )
                }
                return appendEnglishAssist(
                    DecodeResult(buffer, scheme, buffer.length, true, false, mapped),
                    normalized
                )
            }
            JYUTPING_ABBREVIATIONS[normalized]?.let { fullCode ->
                val abbreviationCandidates = corpus.jyutpingIndex[fullCode].orEmpty().map {
                    it.copy(annotation = "↪ $fullCode")
                }
                if (abbreviationCandidates.isNotEmpty()) {
                    return appendEnglishAssist(
                        DecodeResult(
                            buffer,
                            scheme,
                            buffer.length,
                            false,
                            false,
                            abbreviationCandidates
                        ),
                        normalized
                    )
                }
            }
            // Prefix matches (incremental: a still-incomplete code that begins a
            // dictionary key, including concatenated phrase keys like "hoenggong").
            if (corpus.jyutpingPrefixIndex.hasPrefix(normalized)) {
                val pref = corpus.jyutpingPrefixIndex.matching(normalized, limit = 24)
                    .flatMap { corpus.jyutpingIndex[it].orEmpty() }
                if (pref.isNotEmpty()) {
                    val customPrefix = customIndex?.prefixMatches(normalized).orEmpty()
                    val mapped = pref.map {
                        it.copy(
                            sourceSchema = SourceSchema.JYUTPING,
                            type = if (it.text.codePointCount(0, it.text.length) == 1) {
                                CandidateType.CHAR
                            } else {
                                CandidateType.PHRASE
                            },
                            annotation = jyutpingAnnotation(it.text)
                        )
                    }
                    return appendEnglishAssist(
                        DecodeResult(
                            buffer,
                            scheme,
                            buffer.length,
                            false,
                            true,
                            (customPrefix + mapped).distinctBy { it.text }
                        ),
                        normalized
                    )
                }
            }
            val customPrefix = customIndex?.prefixMatches(normalized).orEmpty()
            if (customPrefix.isNotEmpty()) {
                return appendEnglishAssist(
                    DecodeResult(buffer, scheme, buffer.length, false, true, customPrefix),
                    normalized
                )
            }
            // Phrase-evidence composition: cover the whole continuous input with
            // dictionary chunks, including at least one reviewed phrase chunk.
            composeEvidencePhrase(buffer, normalized)?.let {
                return appendEnglishAssist(it, normalized)
            }
            return appendEnglishAssist(empty(buffer, scheme), normalized)
        }

        // 1. Direct CJK input
        if (buffer.isNotEmpty() && buffer.all { it.code > 0x2E80 })
            return decodeCjkDirect(buffer, scheme)

        // 2. Exact match — user custom words first, then the built-in Quick dictionary.
        //    Both decode as exact candidates (cnExactParsed=true); in Quick they remain tap
        //    candidates and may use the separate punctuation path where policy allows.
        val customWords = customWordsByScheme[Scheme.QUICK] ?: CustomWordIndex.EMPTY
        val customExact = customWords.exact(lower)
        val quickExact = corpus.quickIndex[lower]
        // Reviewed English→Cantonese mixed phrases are deliberately available only
        // from the Quick layout. They remain MIXED_PHRASE tap targets, never an
        // exact Space/punctuation resolution.
        val mixedExact = if (scheme == Scheme.QUICK) corpus.mixedIndex[lower].orEmpty()
        else emptyList()
        if (customExact.isNotEmpty() || quickExact != null || mixedExact.isNotEmpty()) {
            return DecodeResult(
                buffer,
                scheme,
                buffer.length,
                customExact.isNotEmpty() || quickExact != null,
                false,
                customExact + quickExact.orEmpty() + mixedExact
            )
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
        val jyutpingExact = mutableListOf<DecodeCandidate>()
        val jyutpingPrefix = mutableListOf<DecodeCandidate>()

        val (englishAssist, englishPrefixOnly) = englishAssistCandidates(lower)

        // Jyutping — exact
        corpus.jyutpingIndex[lower]?.let { jyutpingExact.addAll(it) }

        // Jyutping — prefix (only if no exact Jyutping match)
        if (!corpus.jyutpingIndex.containsKey(lower) && corpus.jyutpingPrefixIndex.hasPrefix(lower)) {
            corpus.jyutpingPrefixIndex.matching(lower, limit = 24)
                .flatMapTo(jyutpingPrefix) { corpus.jyutpingIndex[it].orEmpty() }
        }

        val hasEnglishExact = englishAssist.isNotEmpty() && !englishPrefixOnly
        val hasExact = hasEnglishExact || jyutpingExact.isNotEmpty()
        if (!hasExact && englishAssist.isEmpty() && jyutpingPrefix.isEmpty())
            return Pair(emptyList(), false)

        // English (exact → prefix) then Jyutping (exact → prefix); frequency orders
        // within each group. distinctBy keeps the first, highest-priority copy.
        val deduped = (englishAssist +
            jyutpingExact.sortedByDescending { it.frequency } +
            jyutpingPrefix.sortedByDescending { it.frequency })
            .distinctBy { it.text }
            .take(15)

        return Pair(deduped, !hasExact)
    }

    /** English→Chinese assist candidates are always additive and tap-only. */
    private fun appendEnglishAssist(result: DecodeResult, lower: String): DecodeResult {
        val (assist, _) = englishAssistCandidates(lower)
        if (assist.isEmpty()) return result
        return result.copy(candidates = (result.candidates + assist).distinctBy { it.text })
    }

    /**
     * Exact meaning then completion matches, kept separate so romanization modes
     * can append them after their own candidates without changing Space semantics.
     */
    private fun englishAssistCandidates(lower: String): Pair<List<DecodeCandidate>, Boolean> {
        val exact = corpus.englishAssistIndex[lower].orEmpty()
        val prefix = if (lower.length >= 3 && corpus.englishAssistPrefixIndex.hasPrefix(lower)) {
            corpus.englishAssistPrefixIndex.matching(lower, limit = 24)
                .asSequence()
                .filter { it != lower }
                .flatMap { corpus.englishAssistIndex[it].orEmpty().asSequence() }
                .toList()
        } else {
            emptyList()
        }
        if (exact.isEmpty() && prefix.isEmpty()) return Pair(emptyList(), false)
        return Pair(
            (exact.sortedByDescending { it.frequency } + prefix.sortedByDescending { it.frequency })
                .distinctBy { it.text }
                .take(15),
            exact.isEmpty()
        )
    }

    private fun composeEvidencePhrase(buffer: String, lower: String): DecodeResult? {
        val compositions = jyutpingPhraseComposer.compose(lower)
        if (compositions.isEmpty()) return null
        val candidates = compositions.map { composition ->
            DecodeCandidate(
                composition.text,
                lower,
                SourceSchema.JYUTPING,
                CandidateType.PHRASE,
                composition.score,
                composition.isHkCore,
                annotation = jyutpingAnnotation(composition.text) ?: "↪ $lower"
            )
        }
        return DecodeResult(buffer, Scheme.JYUTPING, buffer.length, true, false, candidates)
    }

    private fun decodeCjkDirect(buffer: String, scheme: Scheme): DecodeResult {
        if (buffer.codePointCount(0, buffer.length) == 1) {
            val charEntry = corpus.charByText[buffer]
            if (charEntry != null) {
                return DecodeResult(buffer, scheme, buffer.length, true, false, listOf(
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

    private fun jyutpingAnnotation(text: String): String? =
        JyutpingAnnotation.reverseLookup(text, corpus.jyutpingTonalReadingsByText)
            ?: JyutpingAnnotation.reverseLookup(text, corpus.jyutpingReadingsByText)

    private fun empty(buffer: String, scheme: Scheme) =
        DecodeResult(buffer, scheme, buffer.length, false, false, emptyList())

    private companion object {
        val JYUTPING_ABBREVIATIONS = mapOf(
            "nh" to "neihou",
            "mg" to "mgoi",
            "dgaai" to "dimgaai"
        )

        // Explicit tone input is deliberate. Keep this tiny reviewed layer
        // separate from safe toneless ranking so hai remains 喺-first while hai1
        // selects the idiomatic Hong Kong written form of the requested profanity.
        val JYUTPING_TONAL_PREFERENCES = mapOf("hai1" to "閪")
    }
}
