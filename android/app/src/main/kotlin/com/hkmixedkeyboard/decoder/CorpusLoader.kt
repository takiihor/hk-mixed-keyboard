package com.hkmixedkeyboard.decoder

import android.content.Context
import com.hkmixedkeyboard.BuildConfig
import com.hkmixedkeyboard.engine.EnglishCompletionIndex
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

data class CharEntry(
    val char: String,
    val quickCode: String,
    val cangjieCode: String,
    val freq: Double,
    val isHkCore: Boolean
)

data class PhraseEntry(
    val phrase: String,
    val quickCode: String,
    val freq: Double,
    val isHkCore: Boolean
)

data class MixedPhraseEntry(val triggerEn: String, val phrase: String, val freq: Double)

data class WhitelistEntry(val word: String, val canonical: String)

data class EnglishAssistEntry(val english: String, val chinese: String, val freq: Double)

data class JyutpingEntry(val jyutping: String, val chinese: String, val freq: Double)

class CorpusLoader(private val ctx: Context) {

    // The large tables go through the binary row cache (see CorpusCache):
    // first run parses CSV and writes the cache; later cold starts read it back
    // without re-splitting strings. The two tiny tables stay on the CSV path.
    val chars: List<CharEntry> by lazy {
        cached("chars",
            read = { CharEntry(it.readUTF(), it.readUTF(), it.readUTF(), it.readDouble(), it.readBoolean()) },
            write = { o, e -> o.writeUTF(e.char); o.writeUTF(e.quickCode); o.writeUTF(e.cangjieCode); o.writeDouble(e.freq); o.writeBoolean(e.isHkCore) },
            parse = ::loadChars)
    }
    val phrases: List<PhraseEntry> by lazy {
        cached("phrases",
            read = { PhraseEntry(it.readUTF(), it.readUTF(), it.readDouble(), it.readBoolean()) },
            write = { o, e -> o.writeUTF(e.phrase); o.writeUTF(e.quickCode); o.writeDouble(e.freq); o.writeBoolean(e.isHkCore) },
            parse = ::loadPhrases)
    }
    val mixedPhrases: List<MixedPhraseEntry> by lazy { loadMixedPhrases() }
    val whitelist: List<WhitelistEntry> by lazy { loadWhitelist() }
    val englishAssist: List<EnglishAssistEntry> by lazy {
        cached("english_assist",
            read = { EnglishAssistEntry(it.readUTF(), it.readUTF(), it.readDouble()) },
            write = { o, e -> o.writeUTF(e.english); o.writeUTF(e.chinese); o.writeDouble(e.freq) },
            parse = ::loadEnglishAssist)
    }
    val englishCompletionIndex: EnglishCompletionIndex by lazy {
        EnglishCompletionIndex(englishAssist)
    }
    val jyutping: List<JyutpingEntry> by lazy {
        cached("jyutping",
            read = { JyutpingEntry(it.readUTF(), it.readUTF(), it.readDouble()) },
            write = { o, e -> o.writeUTF(e.jyutping); o.writeUTF(e.chinese); o.writeDouble(e.freq) },
            parse = ::loadJyutping)
    }
    val pinyinLexicon: PinyinLexicon by lazy {
        val rows = cached("pinyin",
            read = { PinyinEntry(it.readUTF(), it.readUTF(), it.readDouble()) },
            write = { o, e -> o.writeUTF(e.pinyin); o.writeUTF(e.chinese); o.writeDouble(e.freq) },
            parse = ::loadPinyin)
        PinyinLexicon(rows)
    }
    val pinyinDecoder: PinyinDecoder by lazy { PinyinDecoder(pinyinLexicon) }

    // Direct-CJK lookup (pasting/typing Chinese directly). Avoids a linear scan
    // over 21k chars / 40k phrases on every direct character. putIfAbsent keeps the
    // first row for a duplicate key, matching the previous firstOrNull semantics.
    val charByText: Map<String, CharEntry> by lazy {
        HashMap<String, CharEntry>(chars.size).also { m -> for (c in chars) m.putIfAbsent(c.char, c) }
    }
    val phraseByText: Map<String, PhraseEntry> by lazy {
        HashMap<String, PhraseEntry>(phrases.size).also { m -> for (p in phrases) m.putIfAbsent(p.phrase, p) }
    }

    // ── Quick code indices ─────────────────────────────────────────────────

    val quickIndex: Map<String, List<DecodeCandidate>> by lazy { buildQuickIndex() }
    val quickPrefixCandidateIndex: QuickPrefixCandidateIndex by lazy {
        QuickPrefixCandidateIndex(quickIndex)
    }

    // ── English meaning assist indices ─────────────────────────────────────

    val englishAssistIndex: Map<String, List<DecodeCandidate>> by lazy { buildEnglishAssistIndex() }
    val englishAssistPrefixIndex: SortedPrefixIndex by lazy {
        SortedPrefixIndex(englishAssistIndex.keys)
    }

    // ── Jyutping romanization indices ──────────────────────────────────────

    val jyutpingIndex: Map<String, List<DecodeCandidate>> by lazy { buildJyutpingIndex() }
    val jyutpingPrefixIndex: SortedPrefixIndex by lazy {
        SortedPrefixIndex(jyutpingIndex.keys)
    }

    // Valid atomic syllables for segmentation: index keys that have at least one
    // single-character reading (e.g. "nei"→你). Concatenated phrase keys
    // (e.g. "hoenggong"→香港) only map to multi-char text, so they are excluded.
    val jyutpingSyllableSet: Set<String> by lazy {
        jyutpingIndex.entries
            .filter { (_, cands) -> cands.any { it.text.length == 1 } }
            .mapTo(HashSet()) { it.key }
    }

    val jyutpingSegmenter: JyutpingSegmenter by lazy { JyutpingSegmenter(jyutpingSyllableSet) }

    // ── Mixed phrase index ─────────────────────────────────────────────────

    val mixedIndex: Map<String, List<DecodeCandidate>> by lazy { buildMixedIndex() }

    // ── Next-character prediction (逐字組詞) ─────────────────────────────────
    // After the user commits one or more Chinese characters, suggest the next
    // character to build a word one tap at a time. Keyed by the committed prefix
    // (e.g. "我" → 們, 哋, 地, …); value = following characters ranked by the
    // frequency of the phrase they came from.

    val nextCharIndex: Map<String, List<DecodeCandidate>> by lazy {
        // prefix -> (nextChar -> best phrase frequency)
        val acc = HashMap<String, HashMap<String, Double>>()
        for (p in phrases) {
            val s = p.phrase
            val n = s.length
            if (n < 2) continue
            // Predict the next char after prefixes of length 1..3 (words ≤ 4 chars).
            val maxPrefix = minOf(3, n - 1)
            for (i in 1..maxPrefix) {
                val prefix = s.substring(0, i)
                val next = s.substring(i, i + 1)
                val m = acc.getOrPut(prefix) { HashMap() }
                if (p.freq > (m[next] ?: 0.0)) m[next] = p.freq
            }
        }
        acc.mapValues { (_, nexts) ->
            nexts.entries.sortedByDescending { it.value }
                .take(20)
                .map { (ch, f) ->
                    DecodeCandidate(ch, "", SourceSchema.QUICK, CandidateType.CHAR, f, false)
                }
        }
    }

    // ── Whitelist helpers ──────────────────────────────────────────────────

    val canonicalMap: Map<String, String> by lazy {
        whitelist.filter { it.canonical.isNotEmpty() }
            .associate { it.word.lowercase() to it.canonical }
    }

    val whitelistSet: Set<String> by lazy { whitelist.map { it.word.lowercase() }.toSet() }

    // ── Loaders ────────────────────────────────────────────────────────────

    private fun loadChars(): List<CharEntry> = parseCsv("corpus/hk_core_chars.csv") { cols ->
        if (cols.size < 5) null
        else CharEntry(cols[0], cols[1], cols[2], cols[3].toDoubleOrNull() ?: 0.0,
            cols[4].trim() == "1")
    }

    private fun loadPhrases(): List<PhraseEntry> = parseCsv("corpus/hk_core_phrases.csv") { cols ->
        if (cols.size < 4) null
        else PhraseEntry(cols[0], cols[1], cols[2].toDoubleOrNull() ?: 0.0,
            cols[3].trim() == "1")
    }

    private fun loadMixedPhrases(): List<MixedPhraseEntry> =
        parseCsv("corpus/mixed_phrases.csv") { cols ->
            if (cols.size < 3) null
            else MixedPhraseEntry(cols[0], cols[1], cols[2].toDoubleOrNull() ?: 0.0)
        }

    private fun loadWhitelist(): List<WhitelistEntry> = parseCsv("corpus/whitelist_en.csv") { cols ->
        if (cols.isEmpty()) null
        else WhitelistEntry(cols[0], if (cols.size > 1) cols[1] else "")
    }

    private fun loadEnglishAssist(): List<EnglishAssistEntry> =
        parseCsv("corpus/english_assist.csv") { cols ->
            if (cols.size < 3) null
            else EnglishAssistEntry(cols[0].lowercase(), cols[1],
                cols[2].toDoubleOrNull() ?: 0.0)
        }

    private fun loadJyutping(): List<JyutpingEntry> =
        parseCsv("corpus/jyutping.csv") { cols ->
            if (cols.size < 3) null
            else JyutpingEntry(cols[0].lowercase(), cols[1],
                cols[2].toDoubleOrNull() ?: 0.0)
        }

    private fun loadPinyin(): List<PinyinEntry> =
        parseCsv("corpus/pinyin.csv") { cols ->
            if (cols.size < 3) null
            else PinyinEntry(cols[0].lowercase(), cols[1],
                cols[2].toDoubleOrNull() ?: 0.0)
        }

    // ── Index builders ─────────────────────────────────────────────────────

    private fun buildQuickIndex(): Map<String, List<DecodeCandidate>> {
        val map = mutableMapOf<String, MutableList<DecodeCandidate>>()
        for (c in chars) {
            if (c.quickCode.isBlank()) continue
            map.getOrPut(c.quickCode) { mutableListOf() }.add(
                DecodeCandidate(c.char, c.quickCode, SourceSchema.QUICK,
                    CandidateType.CHAR, c.freq, c.isHkCore)
            )
        }
        for (p in phrases) {
            if (p.quickCode.isBlank()) continue
            map.getOrPut(p.quickCode) { mutableListOf() }.add(
                DecodeCandidate(p.phrase, p.quickCode, SourceSchema.QUICK,
                    CandidateType.PHRASE, p.freq, p.isHkCore)
            )
        }
        return map.mapValues { (_, list) ->
            list.sortedWith(
                compareByDescending<DecodeCandidate> { if (it.isHkCore) 1 else 0 }
                    .thenByDescending { if (it.type == CandidateType.PHRASE) 1 else 0 }
                    .thenByDescending { it.frequency }
            )
        }
    }

    private fun buildEnglishAssistIndex(): Map<String, List<DecodeCandidate>> =
        englishAssist.groupBy { it.english }
            .mapValues { (eng, entries) ->
                entries.sortedByDescending { it.freq }
                    .distinctBy { it.chinese }
                    .map { DecodeCandidate(it.chinese, eng, SourceSchema.ENGLISH_ASSIST,
                        CandidateType.ENGLISH_ASSIST, it.freq, false) }
            }

    private fun buildJyutpingIndex(): Map<String, List<DecodeCandidate>> =
        jyutping.groupBy { it.jyutping }
            .mapValues { (jp, entries) ->
                entries.sortedByDescending { it.freq }
                    .distinctBy { it.chinese }
                    .map { DecodeCandidate(it.chinese, jp, SourceSchema.JYUTPING,
                        CandidateType.JYUTPING, it.freq, false) }
            }

    private fun buildMixedIndex(): Map<String, List<DecodeCandidate>> =
        mixedPhrases.groupBy { it.triggerEn.lowercase() }
            .mapValues { (_, entries) ->
                entries.sortedByDescending { it.freq }
                    .map { DecodeCandidate(it.phrase, it.triggerEn,
                        SourceSchema.MIXED_PHRASE, CandidateType.MIXED_PHRASE, it.freq, false) }
            }

    // ── CSV parser ─────────────────────────────────────────────────────────

    private fun <T> parseCsv(assetPath: String, parse: (List<String>) -> T?): List<T> {
        val result = mutableListOf<T>()
        var headerSkipped = false
        try {
            ctx.assets.open(assetPath).bufferedReader().forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("#") || trimmed.isBlank()) return@forEachLine
                if (!headerSkipped) { headerSkipped = true; return@forEachLine }
                val cols = com.hkmixedkeyboard.util.Csv.split(trimmed)
                parse(cols)?.let { result.add(it) }
            }
        } catch (e: Exception) {
            android.util.Log.e("CorpusLoader", "Failed to load $assetPath: ${e.message}")
        }
        return result
    }

    // ── Binary row cache ─────────────────────────────────────────────────────

    // Returns cached rows for [name] when available and current, otherwise parses
    // the CSV via [parse] and writes the cache for next time. An empty parse is not
    // cached, so a transient asset-read failure never poisons the cache.
    private fun <T> cached(
        name: String,
        read: (DataInputStream) -> T,
        write: (DataOutputStream, T) -> Unit,
        parse: () -> List<T>
    ): List<T> {
        val dir = File(ctx.cacheDir, "corpus")
        CorpusCache.load(dir, name, cacheVersion, read)?.let { return it }
        val rows = parse()
        if (rows.isNotEmpty()) CorpusCache.store(dir, name, cacheVersion, rows, write)
        return rows
    }

    // Cache key. The low bits track the app build (release builds bump BUILD_NUMBER,
    // so a shipped corpus change invalidates old caches); the high byte is a manual
    // format/content version — bump CORPUS_CONTENT_VERSION when editing the CSVs
    // without a release build so local dev doesn't read a stale cache.
    private val cacheVersion: Int =
        (CORPUS_CONTENT_VERSION shl 24) or (BuildConfig.BUILD_NUMBER and 0x00FFFFFF)

    private companion object {
        const val CORPUS_CONTENT_VERSION = 4
    }
}
