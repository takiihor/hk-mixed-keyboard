package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.AssistFallbackPolicy
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.engine.CandidateDisplayPolicy
import com.hkmixedkeyboard.memory.MemorySuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BilingualOrderingTest {

    private val policy = CandidateDisplayPolicy()

    private fun learned(text: String, exactCount: Int) = MemorySuggestion(
        candidate = cnChar(text, "ok"),
        count = exactCount,
        isExactBuffer = true,
        exactCount = exactCount
    )

    private fun order(
        buffer: String,
        learned: List<MemorySuggestion> = emptyList(),
        cnRatio: Double = 0.5
    ) = policy.order(
        buffer = buffer,
        learned = learned,
        english = emptyList(),
        decoded = listOf(cnChar("好", buffer)),
        literal = enLiteralCand(buffer),
        chineseFirst = false,
        cnRatio = cnRatio
    ).map { it.text }

    // ── Falling back to the length rule ───────────────────────────────────

    @Test
    fun `without history a short buffer still leads with Chinese`() {
        assertEquals("好", order("ok").first())
    }

    @Test
    fun `without history a long buffer still leads with English`() {
        assertEquals("sender", order("sender").first())
    }

    @Test
    fun `a lone commit is not enough history to override the length rule`() {
        assertEquals("好", order("ok", listOf(learned("好", 1)), cnRatio = 0.0).first())
    }

    // ── The user's own habit for this buffer ──────────────────────────────

    @Test
    fun `a consistently English short buffer stops leading with Chinese`() {
        val out = order("ok", listOf(learned("好", 4)), cnRatio = 0.0)

        assertEquals("ok", out.first())
    }

    @Test
    fun `a consistently Chinese long buffer stops leading with English`() {
        val out = order("sender", listOf(learned("好", 5)), cnRatio = 1.0)

        assertEquals("好", out.first())
    }

    @Test
    fun `a mixed habit leaves the length rule in charge`() {
        assertEquals("好", order("ok", listOf(learned("好", 9)), cnRatio = 0.5).first())
        assertEquals("sender", order("sender", listOf(learned("好", 9)), cnRatio = 0.5).first())
    }

    // ── Assist reaching the romanization schemes ──────────────────────────
    //
    // Tested through AssistFallbackPolicy rather than buildFullCorpusDecoder:
    // that helper reimplements the decoder and models no scheme routing at all,
    // so a scheme-specific assertion would pass there for the wrong reason.

    private fun assist(text: String, english: String, freq: Double) =
        DecodeCandidate(text, english, SourceSchema.ENGLISH_ASSIST,
            CandidateType.ENGLISH_ASSIST, freq, false)

    private fun mixed(text: String, trigger: String) =
        DecodeCandidate(text, trigger, SourceSchema.MIXED_PHRASE,
            CandidateType.MIXED_PHRASE, 0.65, false)

    @Test
    fun `meanings lead the code-switching phrases in the fallback`() {
        val out = AssistFallbackPolicy.merge(
            english = listOf(assist("傳送", "send", 0.5), assist("寄送", "send", 0.9)),
            mixed = listOf(mixed("send返", "send"))
        ).map { it.text }

        assertEquals(listOf("寄送", "傳送", "send返"), out)
    }

    @Test
    fun `the fallback is capped and de-duplicated`() {
        val many = (1..12).map { assist("字$it", "w", it / 100.0) }
        val out = AssistFallbackPolicy.merge(many + many, emptyList())

        assertEquals(AssistFallbackPolicy.LIMIT, out.size)
        assertEquals(out.size, out.distinctBy { it.text }.size)
    }

    @Test
    fun `nothing to offer stays empty so the caller can keep its own result`() {
        assertTrue(AssistFallbackPolicy.merge(emptyList(), emptyList()).isEmpty())
    }
}
