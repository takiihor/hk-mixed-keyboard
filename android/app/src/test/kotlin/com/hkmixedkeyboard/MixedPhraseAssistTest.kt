package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.engine.CandidateDisplayPolicy
import com.hkmixedkeyboard.engine.Classifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 中英 code-switching completions (send → send返). The corpus and the
 * MIXED_PHRASE candidate type shipped long ago, but nothing ever queried
 * mixedIndex, so these candidates could not be produced at all.
 */
class MixedPhraseAssistTest {

    private val corpusDir = "src/main/assets/corpus"

    private fun decode(buffer: String) =
        Classifier(buildFullCorpusDecoder(corpusDir)).classify(buffer, Scheme.QUICK)

    private fun mixedFor(buffer: String) =
        decode(buffer).cnCandidates.filter { it.type == CandidateType.MIXED_PHRASE }

    @Test
    fun `an English trigger produces its code-switching phrases`() {
        val texts = mixedFor("send").map { it.text }

        assertTrue("expected send返 among $texts", texts.contains("send返"))
        assertTrue("expected send個file among $texts", texts.contains("send個file"))
    }

    @Test
    fun `triggers that are also Quick codes still fire`() {
        // ok / no / go are exact Quick codes, so the decoder returns before ever
        // reaching the assist path. They were the three triggers the feature could
        // never have worked for.
        listOf("ok", "no", "go").forEach { trigger ->
            assertTrue(
                "$trigger produced no mixed phrases",
                mixedFor(trigger).isNotEmpty()
            )
        }
    }

    @Test
    fun `a Quick trigger keeps its Chinese characters as well`() {
        val candidates = decode("ok").cnCandidates

        assertTrue(
            "Quick candidates for 'ok' were displaced",
            candidates.any { it.sourceSchema == SourceSchema.QUICK }
        )
        assertTrue(candidates.any { it.type == CandidateType.MIXED_PHRASE })
    }

    @Test
    fun `mixed phrases stay tap-only so Space still commits the literal`() {
        val result = decode("send")

        assertFalse("mixed phrases must not make the buffer Space-committable",
            result.cnExactParsed)
        assertFalse(result.cnHasPhraseMatch)
    }

    @Test
    fun `a word with no code-switching entry produces none`() {
        assertTrue(mixedFor("discuss").isEmpty())
    }

    // ── Display ordering (exercises the real CandidateDisplayPolicy) ──────

    private fun mixedCand(text: String, trigger: String) =
        com.hkmixedkeyboard.decoder.DecodeCandidate(
            text, trigger, SourceSchema.MIXED_PHRASE, CandidateType.MIXED_PHRASE, 0.65, false
        )

    private fun assistCand(text: String, english: String) =
        com.hkmixedkeyboard.decoder.DecodeCandidate(
            text, english, SourceSchema.ENGLISH_ASSIST, CandidateType.ENGLISH_ASSIST, 0.6, false
        )

    @Test
    fun `code-switching phrases are not buried behind the literal`() {
        val display = CandidateDisplayPolicy().order(
            buffer = "send",
            learned = emptyList(),
            english = listOf(enLiteralCand("sender")),
            decoded = listOf(assistCand("傳送", "send"), mixedCand("send返", "send")),
            literal = enLiteralCand("send"),
            chineseFirst = false
        ).map { it.text }

        assertTrue("no mixed phrase reached the bar: $display", display.contains("send返"))
        assertTrue(
            "send返 should precede the literal: $display",
            display.indexOf("send返") < display.indexOf("send")
        )
    }

    @Test
    fun `the exact English meaning still leads the code-switching phrases`() {
        val display = CandidateDisplayPolicy().order(
            buffer = "send",
            learned = emptyList(),
            english = emptyList(),
            decoded = listOf(assistCand("傳送", "send"), mixedCand("send返", "send")),
            literal = enLiteralCand("send"),
            chineseFirst = false
        ).map { it.text }

        assertEquals(listOf("傳送", "send返"), display.take(2))
    }

    @Test
    fun `a long gloss list cannot push the code-switching phrases out of sight`() {
        // CC-CEDICT gives "send" five exact meanings. On a scrolling strip they
        // would bury send返 past the visible candidates.
        val glosses = listOf("送給", "派出", "派送", "寄送", "運出")
            .map { assistCand(it, "send") }
        val display = CandidateDisplayPolicy().order(
            buffer = "send",
            learned = emptyList(),
            english = emptyList(),
            decoded = glosses + mixedCand("send返", "send") + mixedCand("send個file", "send"),
            literal = enLiteralCand("send"),
            chineseFirst = false
        ).map { it.text }

        assertEquals(
            listOf("送給", "派出", "send返", "send個file", "派送", "寄送", "運出"),
            display.take(7)
        )
    }

    @Test
    fun `a code-switching phrase for a different word gets no priority slot`() {
        // The candidate is for "call", the buffer is "send": it must not jump the
        // literal on the strength of being a MIXED_PHRASE.
        val display = CandidateDisplayPolicy().order(
            buffer = "send",
            learned = emptyList(),
            english = emptyList(),
            decoded = listOf(mixedCand("call你", "call")),
            literal = enLiteralCand("send"),
            chineseFirst = false
        ).map { it.text }

        assertTrue(
            "call你 should follow the literal: $display",
            display.indexOf("call你") > display.indexOf("send")
        )
    }
}
