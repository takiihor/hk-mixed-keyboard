package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.*
import com.hkmixedkeyboard.decoder.*
import com.hkmixedkeyboard.engine.ClassifyResult
import com.hkmixedkeyboard.memory.UserMemory

// ── Candidate factories ────────────────────────────────────────────────────

fun cnChar(text: String, code: String, isHkCore: Boolean = false, freq: Double = 0.9) =
    DecodeCandidate(text, code, SourceSchema.QUICK, CandidateType.CHAR, freq, isHkCore)

fun cnPhrase(text: String, code: String, isHkCore: Boolean = false, freq: Double = 0.9) =
    DecodeCandidate(text, code, SourceSchema.QUICK, CandidateType.PHRASE, freq, isHkCore)

fun enLiteralCand(text: String) =
    DecodeCandidate(text, "", SourceSchema.ENGLISH, CandidateType.EN_LITERAL, 0.0, false)

// ── ClassifyResult builders ───────────────────────────────────────────────

/** Buffer where CN matched exactly (e.g. "rr"→唔) and EN is not a known word. */
fun clearChinese(buffer: String, cnText: String, code: String = buffer, isHkCore: Boolean = false) =
    ClassifyResult(
        buffer = buffer,
        cnExactParsed = true, cnHasPhraseMatch = false, cnPrefixParsed = false,
        cnCandidates = listOf(cnChar(cnText, code, isHkCore)),
        enLiteral = buffer, enAutocomplete = null, enIsWord = false, enStrongPrefix = false
    )

/** Buffer where EN is a known word and CN has no match. */
fun clearEnglish(buffer: String, autocomplete: String? = null, enStrong: Boolean = false) =
    ClassifyResult(
        buffer = buffer,
        cnExactParsed = false, cnHasPhraseMatch = false, cnPrefixParsed = false,
        cnCandidates = emptyList(),
        enLiteral = buffer, enAutocomplete = autocomplete,
        enIsWord = true, enStrongPrefix = enStrong
    )

/** Buffer with both CN exact match and EN word recognition (collision). */
fun collision(buffer: String, cnText: String, isHkCore: Boolean, enIsWord: Boolean = true) =
    ClassifyResult(
        buffer = buffer,
        cnExactParsed = true, cnHasPhraseMatch = false, cnPrefixParsed = false,
        cnCandidates = listOf(cnChar(cnText, buffer, isHkCore)),
        enLiteral = buffer, enAutocomplete = null,
        enIsWord = enIsWord, enStrongPrefix = false
    )

/** No CN match, no EN word — pure unknown buffer. */
fun unknown(buffer: String) =
    ClassifyResult(
        buffer = buffer,
        cnExactParsed = false, cnHasPhraseMatch = false, cnPrefixParsed = false,
        cnCandidates = emptyList(),
        enLiteral = buffer, enAutocomplete = null, enIsWord = false, enStrongPrefix = false
    )

/**
 * Simulates what an English-Assist decoder returns:
 * Chinese candidates are present but cnExactParsed and cnHasPhraseMatch remain false
 * (assist candidates are "shown but not space-committable").
 */
fun assistCandidates(buffer: String, candidates: List<DecodeCandidate>) =
    ClassifyResult(
        buffer = buffer,
        cnExactParsed = false, cnHasPhraseMatch = false, cnPrefixParsed = false,
        cnCandidates = candidates,
        enLiteral = buffer, enAutocomplete = null, enIsWord = false, enStrongPrefix = false
    )

// ── Controller factory ────────────────────────────────────────────────────

// `classify` is retained for call-site compatibility but no longer affects the
// controller: Space/punctuation now always commit the literal buffer, so commits
// don't consult the classifier (candidate selection happens in the bar instead).
fun makeCtrl(
    memory: UserMemory = UserMemory(),
    ctx: ImeContext = ImeContext(),
    @Suppress("UNUSED_PARAMETER") classify: (String) -> ClassifyResult = { unknown(it) }
) = CommitController(memory, ctx)

// ── Corpus CSV readers (file-system, no Android Context needed) ───────────

/**
 * Reads Quick-code CSV rows from an absolute file path.
 * Skips comment lines (#) and the header row.
 * Returns list of (quickCode, charText, isHkCore).
 */
fun readQuickCodes(csvPath: String): List<Triple<String, String, Boolean>> {
    val f = java.io.File(csvPath)
    if (!f.exists()) return emptyList()
    val result = mutableListOf<Triple<String, String, Boolean>>()
    var headerSkipped = false
    f.forEachLine { line ->
        val t = line.trim()
        if (t.startsWith("#") || t.isBlank()) return@forEachLine
        if (!headerSkipped) { headerSkipped = true; return@forEachLine }
        val cols = t.split(",")
        if (cols.size >= 5) {
            val char = cols[0].trim()
            val qc   = cols[1].trim()
            val hk   = cols[4].trim() == "1"
            if (char.isNotEmpty() && qc.isNotEmpty()) result.add(Triple(qc, char, hk))
        }
    }
    return result
}

/** Reads hk_core_phrases.csv so test decoding includes built-in Quick phrase prefixes. */
fun readQuickPhrases(csvPath: String): List<PhraseEntry> {
    val f = java.io.File(csvPath)
    if (!f.exists()) return emptyList()
    val result = mutableListOf<PhraseEntry>()
    var headerSkipped = false
    f.forEachLine { line ->
        val t = line.trim()
        if (t.startsWith("#") || t.isBlank()) return@forEachLine
        if (!headerSkipped) { headerSkipped = true; return@forEachLine }
        val cols = t.split(",")
        if (cols.size >= 4) {
            val phrase = cols[0].trim()
            val quickCode = cols[1].trim()
            val freq = cols[2].trim().toDoubleOrNull() ?: 0.0
            val hk = cols[3].trim() == "1"
            if (phrase.isNotEmpty() && quickCode.isNotEmpty())
                result.add(PhraseEntry(phrase, quickCode, freq, hk))
        }
    }
    return result
}

/** Reads english_assist.csv. Returns list of (english, chinese, freq). */
fun readEnglishAssist(csvPath: String): List<Triple<String, String, Double>> {
    val f = java.io.File(csvPath)
    if (!f.exists()) return emptyList()
    val result = mutableListOf<Triple<String, String, Double>>()
    var headerSkipped = false
    f.forEachLine { line ->
        val t = line.trim()
        if (t.startsWith("#") || t.isBlank()) return@forEachLine
        if (!headerSkipped) { headerSkipped = true; return@forEachLine }
        val cols = t.split(",")
        if (cols.size >= 3) {
            val english = cols[0].trim().lowercase()
            val chinese = cols[1].trim()
            val freq    = cols[2].trim().toDoubleOrNull() ?: 0.0
            if (english.isNotEmpty() && chinese.isNotEmpty())
                result.add(Triple(english, chinese, freq))
        }
    }
    return result
}

/** Reads jyutping.csv. Returns list of (jyutping, chinese, freq). */
fun readJyutping(csvPath: String): List<Triple<String, String, Double>> {
    val f = java.io.File(csvPath)
    if (!f.exists()) return emptyList()
    val result = mutableListOf<Triple<String, String, Double>>()
    var headerSkipped = false
    f.forEachLine { line ->
        val t = line.trim()
        if (t.startsWith("#") || t.isBlank()) return@forEachLine
        if (!headerSkipped) { headerSkipped = true; return@forEachLine }
        val cols = t.split(",")
        if (cols.size >= 3) {
            val jp      = cols[0].trim().lowercase()
            val chinese = cols[1].trim()
            val freq    = cols[2].trim().toDoubleOrNull() ?: 0.0
            if (jp.isNotEmpty() && chinese.isNotEmpty())
                result.add(Triple(jp, chinese, freq))
        }
    }
    return result
}

/**
 * Full-corpus test decoder — reads Quick codes, English assist, and Jyutping
 * from the filesystem. No Android Context required.
 *
 * Decode priority mirrors CorpusBackedDecoder:
 *   1. Quick exact match
 *   2. Exact English assist, followed by any matching Quick prefix
 *   3. Quick prefix match when there is no exact English assist
 *   4. English/Jyutping assist prefixes
 *
 * Assist results always have isExactCode=false, so Space never auto-commits them.
 */
fun buildFullCorpusDecoder(corpusDir: String): DecoderContract {
    // Quick codes
    val quickCodes = readQuickCodes("$corpusDir/hk_core_chars.csv")
    val quickPhrases = readQuickPhrases("$corpusDir/hk_core_phrases.csv")
    val quickIndex: Map<String, List<DecodeCandidate>> =
        (quickCodes.map { (qc, char, hk) ->
            DecodeCandidate(char, qc, SourceSchema.QUICK, CandidateType.CHAR, 0.9, hk)
        } + quickPhrases.map { phrase ->
            DecodeCandidate(phrase.phrase, phrase.quickCode, SourceSchema.QUICK,
                CandidateType.PHRASE, phrase.freq, phrase.isHkCore)
        }).groupBy { it.code }
        .mapValues { (_, entries) ->
            entries.sortedWith(
                compareByDescending<DecodeCandidate> { if (it.isHkCore) 1 else 0 }
                    .thenByDescending { if (it.type == CandidateType.PHRASE) 1 else 0 }
                    .thenByDescending { it.frequency }
            )
        }
    val quickPrefixSet: Set<String> = buildSet {
        for (k in quickIndex.keys) for (i in 1..k.length) add(k.substring(0, i))
    }

    // English assist (base gloss + reviewed Hong Kong overrides, mirroring
    // CorpusLoader.loadEnglishAssist so HK renderings rank first by frequency).
    val enAssistRaw = readEnglishAssist("$corpusDir/english_assist.csv") +
        readEnglishAssist("$corpusDir/english_assist_overrides.csv")
    val englishAssistIndex: Map<String, List<DecodeCandidate>> = enAssistRaw
        .groupBy { it.first }
        .mapValues { (eng, entries) ->
            entries.sortedByDescending { it.third }
                .distinctBy { it.second }
                .map { (_, chinese, freq) ->
                    DecodeCandidate(chinese, eng, SourceSchema.ENGLISH_ASSIST,
                        CandidateType.ENGLISH_ASSIST, freq, false)
                }
        }
    val englishAssistPrefixSet: Set<String> = buildSet {
        for (k in englishAssistIndex.keys) for (i in 1..k.length) add(k.substring(0, i))
    }

    // Jyutping
    val jpRaw = readJyutping("$corpusDir/jyutping.csv")
    val jyutpingIndex: Map<String, List<DecodeCandidate>> = jpRaw
        .groupBy { it.first }
        .mapValues { (jp, entries) ->
            entries.sortedByDescending { it.third }
                .distinctBy { it.second }
                .map { (_, chinese, freq) ->
                    DecodeCandidate(chinese, jp, SourceSchema.JYUTPING,
                        CandidateType.JYUTPING, freq, false)
                }
        }
    val jyutpingPrefixSet: Set<String> = buildSet {
        for (k in jyutpingIndex.keys) for (i in 1..k.length) add(k.substring(0, i))
    }

    return object : DecoderContract {
        override val schemeName = "FullCorpusTestDecoder"
        override fun isSchemeAvailable(scheme: Scheme) = true

        override fun decode(buffer: String, scheme: Scheme): DecodeResult {
            val lower = buffer.lowercase()

            // Quick exact
            quickIndex[lower]?.let {
                return DecodeResult(buffer, scheme, buffer.length, true, false, it)
            }

            // English meaning (4) before Jyutping fallback (5); within each source
            // exact before prefix (mirrors CorpusBackedDecoder.buildAssistCandidates).
            val englishExact = mutableListOf<DecodeCandidate>()
            val englishPrefix = mutableListOf<DecodeCandidate>()
            val jyutpingExact = mutableListOf<DecodeCandidate>()
            val jyutpingPrefix = mutableListOf<DecodeCandidate>()

            englishAssistIndex[lower]?.let { englishExact.addAll(it) }
            // Prefix completions run even with an exact match (mirrors
            // CorpusBackedDecoder): "disc" → disc→碟片 plus discuss→討論.
            if (lower.length >= 3 && englishAssistPrefixSet.contains(lower)) {
                englishAssistIndex.entries.asSequence()
                    .filter { it.key.startsWith(lower) && it.key != lower }
                    .flatMap { it.value.asSequence() }
                    .forEach { englishPrefix.add(it) }
            }

            jyutpingIndex[lower]?.let { jyutpingExact.addAll(it) }
            if (!jyutpingIndex.containsKey(lower) && jyutpingPrefixSet.contains(lower)) {
                jyutpingIndex.entries.filter { it.key.startsWith(lower) }
                    .flatMap { it.value }.forEach { jyutpingPrefix.add(it) }
            }

            val hasExact = englishExact.isNotEmpty() || jyutpingExact.isNotEmpty()
            val deduped = (englishExact.sortedByDescending { it.frequency } +
                englishPrefix.sortedByDescending { it.frequency } +
                jyutpingExact.sortedByDescending { it.frequency } +
                jyutpingPrefix.sortedByDescending { it.frequency })
                .distinctBy { it.text }
                .take(15)

            val quickPrefix = if (quickPrefixSet.contains(lower)) {
                quickIndex.entries.filter { it.key.startsWith(lower) }
                    .flatMap { it.value }.take(12)
            } else emptyList()

            // An exact English meaning leads, without discarding incomplete Quick
            // choices or lower-priority assist candidates.
            if (englishExact.isNotEmpty()) {
                val exactAssist = deduped.filter {
                    it.sourceSchema == SourceSchema.ENGLISH_ASSIST && it.code == lower
                }
                val remainingAssist = deduped.filterNot {
                    it.sourceSchema == SourceSchema.ENGLISH_ASSIST && it.code == lower
                }
                val cands = (exactAssist + quickPrefix + remainingAssist)
                    .distinctBy { it.text }
                return DecodeResult(buffer, scheme, buffer.length, false, false, cands)
            }

            // Quick prefix
            if (quickPrefix.isNotEmpty())
                return DecodeResult(buffer, scheme, buffer.length, false, true, quickPrefix)

            if (deduped.isNotEmpty())
                return DecodeResult(buffer, scheme, buffer.length, false, !hasExact, deduped)

            return DecodeResult(buffer, scheme, buffer.length, false, false, emptyList())
        }
    }
}
