package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.*
import com.hkmixedkeyboard.decoder.*
import com.hkmixedkeyboard.engine.Classifier
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression tests for existing Quick (速成) code behavior.
 * These verify that Stage 2 changes (corpus loader, Room, DataStore) did not break
 * the core Quick-code → Chinese decode and commit logic.
 *
 * Uses InMemoryQuickDecoder built from the actual corpus CSV files on disk.
 * All tests are EXPECTED to PASS.
 */
class QuickRegressionTest {

    private val CHARS_CSV = "src/main/assets/corpus/hk_core_chars.csv"
    private val PHRASES_CSV = "src/main/assets/corpus/hk_core_phrases.csv"

    private val quickCodes = readQuickCodes(CHARS_CSV)

    // ── Build classifier from corpus ───────────────────────────────────────

    private val decoder: DecoderContract by lazy {
        // Build index from chars + phrases
        val charCandidates = quickCodes.map { (qc, char, hk) ->
            qc to DecodeCandidate(char, qc, SourceSchema.QUICK, CandidateType.CHAR, 0.9, hk)
        }
        val phraseCandidates = readPhrases(PHRASES_CSV).map { (phrase, qc, hk) ->
            qc to DecodeCandidate(phrase, qc, SourceSchema.QUICK, CandidateType.PHRASE, 0.95, hk)
        }
        val index = (charCandidates + phraseCandidates)
            .filter { it.first.isNotEmpty() }
            .groupBy { it.first }
            .mapValues { (_, pairs) ->
                pairs.map { it.second }
                    .sortedWith(
                        compareByDescending<DecodeCandidate> { if (it.isHkCore) 1 else 0 }
                            .thenByDescending { if (it.type == CandidateType.PHRASE) 1 else 0 }
                            .thenByDescending { it.frequency }
                    )
            }
        val prefixSet: Set<String> = buildSet {
            for (k in index.keys) for (i in 1..k.length) add(k.substring(0, i))
        }
        object : DecoderContract {
            override val schemeName = "CorpusRegressionDecoder"
            override fun isSchemeAvailable(scheme: Scheme) = true
            override fun decode(buffer: String, scheme: Scheme): DecodeResult {
                val lower = buffer.lowercase()
                index[lower]?.let { return DecodeResult(buffer, scheme, buffer.length, true, false, it) }
                if (prefixSet.contains(lower) && !index.containsKey(lower)) {
                    val cands = index.entries.filter { it.key.startsWith(lower) }
                        .flatMap { it.value }.take(12)
                    return DecodeResult(buffer, scheme, buffer.length, false, true, cands)
                }
                return DecodeResult(buffer, scheme, buffer.length, false, false, emptyList())
            }
        }
    }

    private val classifier get() = Classifier(decoder)

    // ── Single-char Quick code decode ──────────────────────────────────────

    @Test
    fun `rr decodes to 唔 (HK core)`() {
        val result = classifier.classify("rr", Scheme.QUICK)
        assertTrue("'rr' must produce candidates", result.cnCandidates.isNotEmpty())
        val top = result.cnCandidates.first()
        assertEquals("Top candidate for 'rr' must be 唔", "唔", top.text)
        assertTrue("唔 must be HK core", top.isHkCore)
    }

    @Test
    fun `ru decodes to 嘅 (HK core)`() {
        val result = classifier.classify("ru", Scheme.QUICK)
        assertTrue("'ru' must produce candidates", result.cnCandidates.isNotEmpty())
        assertEquals("Top candidate for 'ru' must be 嘅", "嘅", result.cnCandidates.first().text)
        assertTrue("嘅 must be HK core", result.cnCandidates.first().isHkCore)
    }

    @Test
    fun `ri decodes to 啲 (HK core)`() {
        val result = classifier.classify("ri", Scheme.QUICK)
        assertTrue(result.cnCandidates.isNotEmpty())
        assertEquals("啲", result.cnCandidates.first().text)
        assertTrue(result.cnCandidates.first().isHkCore)
    }

    @Test
    fun `kb decodes to 冇 (HK core)`() {
        val result = classifier.classify("kb", Scheme.QUICK)
        assertTrue(result.cnCandidates.isNotEmpty())
        assertEquals("冇", result.cnCandidates.first().text)
        assertTrue(result.cnCandidates.first().isHkCore)
    }

    @Test
    fun `hi yields 我 (real Cangjie hqi to Quick hi)`() {
        // 我 = 竹手戈 (hqi) → Quick first+last = hi. With the full dictionary the
        // code is shared by many chars, so verify membership rather than top rank.
        val result = classifier.classify("hi", Scheme.QUICK)
        assertTrue(result.cnCandidates.isNotEmpty())
        assertTrue("我 must be among 'hi' candidates",
            result.cnCandidates.any { it.text == "我" })
    }

    @Test
    fun `os decodes to 佢`() {
        val result = classifier.classify("os", Scheme.QUICK)
        assertTrue(result.cnCandidates.isNotEmpty())
        assertEquals("佢", result.cnCandidates.first().text)
    }

    // ── Phrase Quick code decode ───────────────────────────────────────────

    @Test
    fun `rryo decodes to 唔該 phrase (HK core)`() {
        val result = classifier.classify("rryo", Scheme.QUICK)
        assertTrue("'rryo' must produce candidates", result.cnCandidates.isNotEmpty())
        val phraseCand = result.cnCandidates.firstOrNull { it.type == CandidateType.PHRASE }
        assertNotNull("'rryo' must produce a PHRASE candidate", phraseCand)
        assertEquals("唔該", phraseCand!!.text)
        assertTrue("唔該 must be HK core", phraseCand.isHkCore)
    }

    @Test
    fun `rrvd decodes to 唔好 phrase (HK core)`() {
        val result = classifier.classify("rrvd", Scheme.QUICK)
        val phraseCand = result.cnCandidates.firstOrNull { it.type == CandidateType.PHRASE }
        assertNotNull("'rrvd' must produce 唔好 phrase", phraseCand)
        assertEquals("唔好", phraseCand!!.text)
    }

    @Test
    fun `kbarac decodes to 冇問題 phrase`() {
        val result = classifier.classify("kbarac", Scheme.QUICK)
        val phraseCand = result.cnCandidates.firstOrNull { it.type == CandidateType.PHRASE }
        assertNotNull("'kbarac' must produce 冇問題 phrase", phraseCand)
        assertEquals("冇問題", phraseCand!!.text)
    }

    @Test
    fun `hiru decodes to 我嘅 phrase`() {
        val result = classifier.classify("hiru", Scheme.QUICK)
        val phraseCand = result.cnCandidates.firstOrNull { it.type == CandidateType.PHRASE }
        assertNotNull("'hiru' must produce 我嘅 phrase", phraseCand)
        assertEquals("我嘅", phraseCand!!.text)
    }

    @Test
    fun `orjd includes 估字 phrase`() {
        val result = classifier.classify("orjd", Scheme.QUICK)
        assertTrue("'orjd' must produce phrase candidates", result.cnCandidates.isNotEmpty())
        assertTrue("Missing HK phrase 估字 for Quick code 'orjd'",
            result.cnCandidates.any { it.text == "估字" && it.type == CandidateType.PHRASE })
    }

    // ── Phrases rank above single chars ────────────────────────────────────

    @Test
    fun `Phrase ranks above single char for same Quick code prefix`() {
        val result = classifier.classify("rryo", Scheme.QUICK)
        // 唔該 (phrase) should appear before any single char with code rr+yo
        val first = result.cnCandidates.firstOrNull()
        assertNotNull(first)
        assertEquals("Phrase 唔該 should rank first for 'rryo'", CandidateType.PHRASE, first!!.type)
    }

    // ── Space commit: short input stays English ────────────────────────────
    // Two Latin letters + Space commit the English literal, not a Chinese char,
    // even when the 2-letter code is a HK-core Quick code. 唔/嘅 remain tap-only.

    @Test
    fun `Space on 2-letter rr commits EN literal rr (short input stays English)`() {
        val ctrl = makeCtrl(classify = { buf ->
            classifier.classify(buf, Scheme.QUICK)
        })
        val state = ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)
        assertEquals("Space on 2-letter 'rr' → 'rr' (EN); 唔 is tap-only", "rr", out.committedText?.trimEnd())
    }

    @Test
    fun `Space on 2-letter ru commits EN literal ru (short input stays English)`() {
        val ctrl = makeCtrl(classify = { buf ->
            classifier.classify(buf, Scheme.QUICK)
        })
        val state = ImeStateData(buffer = "ru", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)
        assertEquals("Space on 2-letter 'ru' → 'ru' (EN); 嘅 is tap-only", "ru", out.committedText?.trimEnd())
    }

    // ── ok / go whitelist: Space commits EN literal ─────────────────────────

    @Test
    fun `Space on ok commits ok (EN literal, not 仗)`() {
        val ctrl = makeCtrl(classify = { buf ->
            classifier.classify(buf, Scheme.QUICK)
        })
        val state = ImeStateData(buffer = "ok", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)
        assertEquals("Space on 'ok' → 'ok' (EN literal)", "ok", out.committedText?.trimEnd())
    }

    @Test
    fun `Space on go commits go (EN literal, not Chinese)`() {
        val ctrl = makeCtrl(classify = { buf ->
            classifier.classify(buf, Scheme.QUICK)
        })
        val state = ImeStateData(buffer = "go", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)
        assertEquals("Space on 'go' → 'go' (EN literal)", "go", out.committedText?.trimEnd())
    }

    // ── Prefix detection ───────────────────────────────────────────────────

    @Test
    fun `Single radical r decodes to 口 (exact single-letter Quick code)`() {
        // Every Cangjie radical letter is itself a single-code character (口 = r),
        // so a one-letter buffer is an exact match, not a prefix.
        val result = classifier.classify("r", Scheme.QUICK)
        assertTrue("'r' must produce candidates", result.cnCandidates.isNotEmpty())
        assertTrue("口 (single-letter code r) must be among 'r' candidates",
            result.cnCandidates.any { it.text == "口" })
    }

    @Test
    fun `Unknown string has no CN match and no prefix`() {
        val result = classifier.classify("xyz", Scheme.QUICK)
        assertFalse(result.cnExactParsed)
        assertFalse(result.cnHasPhraseMatch)
        assertFalse(result.cnPrefixParsed)
        assertTrue(result.cnCandidates.isEmpty())
    }
}

// ── Phrase CSV helper ──────────────────────────────────────────────────────

private data class PhraseRow(val phrase: String, val quickCode: String, val isHkCore: Boolean)

private fun readPhrases(csvPath: String): List<PhraseRow> {
    val f = java.io.File(csvPath)
    if (!f.exists()) return emptyList()
    val result = mutableListOf<PhraseRow>()
    var headerSkipped = false
    f.forEachLine { line ->
        val t = line.trim()
        if (t.startsWith("#") || t.isBlank()) return@forEachLine
        if (!headerSkipped) { headerSkipped = true; return@forEachLine }
        val cols = t.split(",")
        if (cols.size >= 4) {
            val phrase = cols[0].trim()
            val qc = cols[1].trim()
            val hk = cols[3].trim() == "1"
            if (phrase.isNotEmpty() && qc.isNotEmpty())
                result.add(PhraseRow(phrase, qc, hk))
        }
    }
    return result
}
