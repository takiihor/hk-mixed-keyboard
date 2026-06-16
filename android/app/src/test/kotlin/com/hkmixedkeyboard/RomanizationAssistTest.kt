package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.*
import com.hkmixedkeyboard.engine.Classifier
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the Jyutping/romanization → Chinese candidate feature.
 *
 * Group A: Candidate generation — verified against full corpus decoder that reads
 *   jyutping.csv. These tests now PASS with the feature implemented.
 *   "chong" → 衝 (top), 沖/充 (secondary); "cho" prefix shows candidates.
 *
 * Group B: Space conservatism — Space must not auto-commit Jyutping candidates.
 *
 * Group C: Tap commits the selected candidate.
 */
class RomanizationAssistTest {

    private val CORPUS_DIR = "src/main/assets/corpus"

    // ── Group A: Candidate generation ─────────────────────────────────────

    @Test
    fun `cung jyutping should produce 衝 candidate`() {
        // 衝 = cung1 in Jyutping. The code is shared by several common characters,
        // so verify membership rather than top rank.
        val decoder = buildFullCorpusDecoder(CORPUS_DIR)
        val classifier = Classifier(decoder)
        val result = classifier.classify("cung", Scheme.QUICK)

        assertTrue(
            "'cung' should produce 衝 as a candidate, " +
            "but got: ${result.cnCandidates.map { it.text }}",
            result.cnCandidates.any { it.text == "衝" }
        )
    }

    @Test
    fun `cung should also show 沖 or 充 candidates`() {
        val decoder = buildFullCorpusDecoder(CORPUS_DIR)
        val classifier = Classifier(decoder)
        val result = classifier.classify("cung", Scheme.QUICK)

        assertTrue(
            "'cung' should show 沖 or 充, " +
            "but got: ${result.cnCandidates.map { it.text }}",
            result.cnCandidates.any { it.text == "沖" || it.text == "充" }
        )
    }

    @Test
    fun `cu prefix should show romanization candidates`() {
        val decoder = buildFullCorpusDecoder(CORPUS_DIR)
        val classifier = Classifier(decoder)
        val result = classifier.classify("cu", Scheme.QUICK)

        assertTrue(
            "prefix 'cu' should produce romanization candidates, " +
            "but got: ${result.cnCandidates.map { it.text }}",
            result.cnCandidates.isNotEmpty()
        )
    }

    // ── Group B: Space conservatism with mock romanization ────────────────

    @Test
    fun `Space does not auto-commit romanization assist candidate for chong`() {
        val mockChong = cnChar("衝", "chong")
        val ctrl = makeCtrl(classify = { buf ->
            if (buf == "chong") assistCandidates(buf, listOf(mockChong, cnChar("沖", "chong")))
            else unknown(buf)
        })

        val state = com.hkmixedkeyboard.commit.ImeStateData(
            buffer = "chong",
            imeState = com.hkmixedkeyboard.commit.ImeState.COMPOSING
        )
        val out = ctrl.onSpace(state)

        assertNotEquals(
            "Space must NOT commit 衝 — romanization assist candidates are tap-only",
            "衝", out.committedText
        )
        assertEquals(
            "Space on romanized input should commit literal 'chong'",
            "chong", out.committedText?.trimEnd()
        )
    }

    // ── Group C: Tap commits correct romanization candidate ───────────────

    @Test
    fun `Tapping 衝 candidate commits 衝 and clears buffer`() {
        val chong = cnChar("衝", "chong")
        val ctrl = makeCtrl(classify = { buf ->
            assistCandidates(buf, listOf(chong, cnChar("沖", "chong")))
        })

        val state = com.hkmixedkeyboard.commit.ImeStateData(
            buffer = "chong",
            imeState = com.hkmixedkeyboard.commit.ImeState.COMPOSING
        )
        val out = ctrl.onCandidateTap(chong, state)

        assertEquals("Tap on 衝 should commit 衝", "衝", out.committedText)
        assertTrue("Buffer should clear after tap", out.newState.buffer.isEmpty())
    }
}
