package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.*
import com.hkmixedkeyboard.engine.Classifier
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the English-meaning → Chinese candidate feature.
 *
 * Group A: Candidate generation — verified against full corpus decoder that reads
 *   english_assist.csv. These tests now PASS with the feature implemented.
 *
 * Group B: Space conservatism — CommitController must not auto-commit assist candidates
 *   on Space (they are tap-only).
 *
 * Group C: Tap commits — tapping a candidate commits it and clears the buffer.
 */
class EnglishAssistCandidateTest {

    private val CORPUS_DIR = "src/main/assets/corpus"

    // ── Group A: Candidate generation ─────────────────────────────────────

    @Test
    fun `communication should produce 溝通 candidate`() {
        val decoder = buildFullCorpusDecoder(CORPUS_DIR)
        val classifier = Classifier(decoder)
        val result = classifier.classify("communication", Scheme.QUICK)

        assertTrue(
            "'communication' should produce 溝通 candidate, " +
            "but got: ${result.cnCandidates.map { it.text }}",
            result.cnCandidates.any { it.text == "溝通" }
        )
    }

    @Test
    fun `communicate should produce 溝通 candidate`() {
        val decoder = buildFullCorpusDecoder(CORPUS_DIR)
        val classifier = Classifier(decoder)
        val result = classifier.classify("communicate", Scheme.QUICK)

        assertTrue(
            "'communicate' should produce 溝通 candidate, " +
            "but got: ${result.cnCandidates.map { it.text }}",
            result.cnCandidates.any { it.text == "溝通" }
        )
    }

    @Test
    fun `discussion should produce 討論 candidate`() {
        val decoder = buildFullCorpusDecoder(CORPUS_DIR)
        val classifier = Classifier(decoder)
        val result = classifier.classify("discussion", Scheme.QUICK)

        assertTrue(
            "'discussion' should produce 討論 candidate, " +
            "but got: ${result.cnCandidates.map { it.text }}",
            result.cnCandidates.any { it.text == "討論" }
        )
    }

    @Test
    fun `discuss should produce 討論 candidate`() {
        val decoder = buildFullCorpusDecoder(CORPUS_DIR)
        val classifier = Classifier(decoder)
        val result = classifier.classify("discuss", Scheme.QUICK)

        assertTrue(
            "'discuss' should produce 討論 candidate, " +
            "but got: ${result.cnCandidates.map { it.text }}",
            result.cnCandidates.any { it.text == "討論" }
        )
    }

    // ── Group A: Prefix lookup ────────────────────────────────────────────

    @Test
    fun `commu prefix should show 溝通 candidate`() {
        val decoder = buildFullCorpusDecoder(CORPUS_DIR)
        val classifier = Classifier(decoder)
        val result = classifier.classify("commu", Scheme.QUICK)

        assertTrue(
            "prefix 'commu' should show 溝通 candidate, " +
            "but got: ${result.cnCandidates.map { it.text }}",
            result.cnCandidates.any { it.text == "溝通" }
        )
    }

    @Test
    fun `discu prefix should show 討論 candidate`() {
        val decoder = buildFullCorpusDecoder(CORPUS_DIR)
        val classifier = Classifier(decoder)
        val result = classifier.classify("discu", Scheme.QUICK)

        assertTrue(
            "prefix 'discu' should show 討論 candidate, " +
            "but got: ${result.cnCandidates.map { it.text }}",
            result.cnCandidates.any { it.text == "討論" }
        )
    }

    // ── Group A: Exact-match weighting ────────────────────────────────────
    // The word's own meaning must outrank a higher-frequency prefix completion.
    // "act"→動作 (exact, freq 0.6951) vs "action"→作用 (prefix completion, 0.6987):
    // a pure frequency sort would bury the exact 動作 under 作用.

    @Test
    fun `exact meaning ranks above a higher-frequency prefix completion`() {
        val decoder = buildFullCorpusDecoder(CORPUS_DIR)
        val texts = decoder.decode("act", Scheme.QUICK).candidates.map { it.text }

        val exactIdx = texts.indexOf("動作")   // act → 動作 (exact)
        val prefixIdx = texts.indexOf("作用")  // action → 作用 (prefix completion)
        assertTrue("both meanings should be present, but got: $texts",
            exactIdx >= 0 && prefixIdx >= 0)
        assertTrue("exact 動作 should rank before higher-freq prefix 作用, but got: $texts",
            exactIdx < prefixIdx)
    }

    // ── English meaning ranks above the Jyutping fallback ─────────────────
    // "cat" is also a valid Jyutping syllable (柒/七), so a Quick-mode typist
    // would otherwise get Cantonese homophones instead of the English meaning.

    @Test
    fun `cat surfaces 貓 above Jyutping homophones`() {
        val decoder = buildFullCorpusDecoder(CORPUS_DIR)
        val texts = decoder.decode("cat", Scheme.QUICK).candidates.map { it.text }

        assertTrue("cat should surface its English meaning 貓, but got: $texts",
            texts.contains("貓"))
        val maoIdx = texts.indexOf("貓")
        val homophoneIdx = texts.indexOfFirst { it == "柒" || it == "七" }
        if (homophoneIdx >= 0) {
            assertTrue("English meaning 貓 should rank above Jyutping homophone 柒/七, " +
                "but got: $texts", maoIdx < homophoneIdx)
        }
    }

    @Test
    fun `exact English meaning outranks an incomplete built-in Quick phrase code`() {
        val decoder = buildFullCorpusDecoder(CORPUS_DIR)
        val result = decoder.decode("cat", Scheme.QUICK)
        val texts = result.candidates.map { it.text }

        assertTrue("both exact English and incomplete Quick candidates should remain: $texts",
            texts.contains("貓") && texts.contains("曾對"))
        assertTrue("exact cat meaning must precede incomplete cati Quick phrase: $texts",
            texts.indexOf("貓") < texts.indexOf("曾對"))
        assertFalse("English-assist results must remain tap-only", result.isExactCode)
    }

    @Test
    fun `exact built-in Quick phrase still wins`() {
        val result = buildFullCorpusDecoder(CORPUS_DIR).decode("cati", Scheme.QUICK)

        assertTrue(result.isExactCode)
        assertEquals("曾對", result.candidates.firstOrNull()?.text)
    }

    @Test
    fun `Jyutping collision remains tap-only in Quick mode`() {
        val result = buildFullCorpusDecoder(CORPUS_DIR).decode("cai", Scheme.QUICK)

        assertFalse(result.isExactCode)
        assertTrue(result.candidates.isNotEmpty())
        assertTrue(result.candidates.any {
            it.sourceSchema == SourceSchema.JYUTPING && it.code == "cai"
        })
    }

    // ── Group B: Space conservatism with mock assist ──────────────────────

    @Test
    fun `Space does not auto-commit assist candidate for communication`() {
        val soughtChinese = cnChar("溝通", "communication")
        val ctrl = makeCtrl(classify = { buf ->
            if (buf == "communication") assistCandidates(buf, listOf(soughtChinese))
            else unknown(buf)
        })

        val state = com.hkmixedkeyboard.commit.ImeStateData(
            buffer = "communication",
            imeState = com.hkmixedkeyboard.commit.ImeState.COMPOSING
        )
        val out = ctrl.onSpace(state)

        assertNotEquals(
            "Space must NOT commit 溝通 — assist candidates are tap-only",
            "溝通", out.committedText
        )
        assertEquals(
            "Space on English assist should commit the literal 'communication'",
            "communication", out.committedText?.trimEnd()
        )
    }

    @Test
    fun `Space does not auto-commit assist candidate for discussion`() {
        val soughtChinese = cnChar("討論", "discussion")
        val ctrl = makeCtrl(classify = { buf ->
            if (buf == "discussion") assistCandidates(buf, listOf(soughtChinese))
            else unknown(buf)
        })

        val state = com.hkmixedkeyboard.commit.ImeStateData(
            buffer = "discussion",
            imeState = com.hkmixedkeyboard.commit.ImeState.COMPOSING
        )
        val out = ctrl.onSpace(state)

        assertNotEquals("溝通", out.committedText)
        assertNotEquals("討論", out.committedText)
        assertEquals("discussion", out.committedText?.trimEnd())
    }

    // ── Group C: Tap commits the assist candidate ─────────────────────────

    @Test
    fun `Tapping 溝通 candidate commits 溝通 and clears buffer`() {
        val soughtChinese = cnChar("溝通", "communication")
        val ctrl = makeCtrl(classify = { buf ->
            assistCandidates(buf, listOf(soughtChinese))
        })

        val state = com.hkmixedkeyboard.commit.ImeStateData(
            buffer = "communication",
            imeState = com.hkmixedkeyboard.commit.ImeState.COMPOSING
        )
        val out = ctrl.onCandidateTap(soughtChinese, state)

        assertEquals("Tap on 溝通 should commit 溝通", "溝通", out.committedText)
        assertTrue("Buffer should clear after tap", out.newState.buffer.isEmpty())
    }

    @Test
    fun `Tapping 討論 candidate commits 討論 and clears buffer`() {
        val soughtChinese = cnChar("討論", "discussion")
        val ctrl = makeCtrl(classify = { buf ->
            assistCandidates(buf, listOf(soughtChinese))
        })

        val state = com.hkmixedkeyboard.commit.ImeStateData(
            buffer = "discussion",
            imeState = com.hkmixedkeyboard.commit.ImeState.COMPOSING
        )
        val out = ctrl.onCandidateTap(soughtChinese, state)

        assertEquals("討論", out.committedText)
        assertTrue(out.newState.buffer.isEmpty())
    }
}
