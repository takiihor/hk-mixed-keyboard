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
    fun `disc prefix should show 討論 candidate`() {
        val decoder = buildFullCorpusDecoder(CORPUS_DIR)
        val classifier = Classifier(decoder)
        val result = classifier.classify("disc", Scheme.QUICK)

        assertTrue(
            "prefix 'disc' should show 討論 candidate, " +
            "but got: ${result.cnCandidates.map { it.text }}",
            result.cnCandidates.any { it.text == "討論" }
        )
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
