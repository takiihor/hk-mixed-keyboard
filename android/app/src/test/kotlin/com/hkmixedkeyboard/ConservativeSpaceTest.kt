package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.*
import com.hkmixedkeyboard.decoder.CandidateType
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests proving Space does NOT auto-commit:
 *   - English autocomplete candidates
 *   - Assist (English-meaning or romanization) Chinese candidates
 *   - Prefix-only Chinese candidates
 *
 * All tests in this file are EXPECTED to PASS — they verify correct conservative behavior
 * that must be maintained regardless of whether the assist feature is implemented.
 */
class ConservativeSpaceTest {

    // ── 1. Space on English input commits literal, not assist candidate ────

    @Test
    fun `Space on communication commits literal, not assist Chinese`() {
        val mock溝通 = cnChar("溝通", "communication")
        val ctrl = makeCtrl(classify = { buf ->
            // Simulate assist: candidates visible, but cnExactParsed=false, cnHasPhraseMatch=false
            if (buf == "communication") assistCandidates(buf, listOf(mock溝通))
            else unknown(buf)
        })
        val state = ImeStateData(buffer = "communication", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)

        assertEquals("communication", out.committedText)
        assertEquals(CandidateType.EN_LITERAL, out.memoryWrite.candidate?.type ?: CandidateType.EN_LITERAL)
    }

    @Test
    fun `Space on chong commits literal chong, not 衝`() {
        val mock衝 = cnChar("衝", "chong")
        val ctrl = makeCtrl(classify = { buf ->
            if (buf == "chong") assistCandidates(buf, listOf(mock衝))
            else unknown(buf)
        })
        val state = ImeStateData(buffer = "chong", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)

        assertEquals("chong", out.committedText)
        assertNotEquals("衝", out.committedText?.trimEnd())
    }

    // ── 2. Space on English autocomplete (enStrongPrefix) commits literal ─

    @Test
    fun `Space on hap commits hap literal not happy autocomplete`() {
        val ctrl = makeCtrl(classify = { buf ->
            clearEnglish(buf, autocomplete = "happy", enStrong = true)
        })
        val state = ImeStateData(buffer = "hap", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)

        assertEquals("hap", out.committedText)
        assertNotEquals("happy", out.committedText?.trimEnd())
    }

    @Test
    fun `Space on mee commits mee literal not meeting autocomplete`() {
        val ctrl = makeCtrl(classify = { buf ->
            clearEnglish(buf, autocomplete = "meeting", enStrong = true)
        })
        val state = ImeStateData(buffer = "mee", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)

        assertEquals("mee", out.committedText)
        assertNotEquals("meeting", out.committedText?.trimEnd())
    }

    // ── 3. Space on prefix-only Chinese buffer commits literal ─────────────

    @Test
    fun `Space on prefix-only Chinese buffer commits English literal`() {
        // Suppose "r" is a prefix of many 2-letter Quick codes
        val ctrl = makeCtrl(classify = { buf ->
            if (buf == "r") {
                com.hkmixedkeyboard.engine.ClassifyResult(
                    buffer = buf,
                    cnExactParsed = false, cnHasPhraseMatch = false, cnPrefixParsed = true,
                    cnCandidates = listOf(cnChar("唔", "rr")),
                    enLiteral = buf, enAutocomplete = null, enIsWord = false, enStrongPrefix = false
                )
            } else unknown(buf)
        })
        val state = ImeStateData(buffer = "r", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)

        // Prefix-only → cnCommittable=false → enLiteral
        assertEquals("r", out.committedText)
    }

    // ── 4. Space on empty buffer passes through space ───────────────────────

    @Test
    fun `Space with empty buffer passes through space character`() {
        val ctrl = makeCtrl()
        val out = ctrl.onSpace(ImeStateData())
        assertEquals(" ", out.committedText)
        assertTrue(out.newState.buffer.isEmpty())
    }

    // ── 5. Quick Space flushes the raw buffer without trailing whitespace ──

    @Test
    fun `Quick Space flushes a buffer as a literal without trailing whitespace`() {
        val ctx = ImeContext()
        val ctrl = makeCtrl(ctx = ctx, classify = { buf ->
            clearChinese(buf, "唔", isHkCore = true)
        })
        val state = ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)

        assertEquals("rr", out.committedText)
        assertEquals("", out.newState.buffer)
    }

    @Test
    fun `Space flushes long exact Quick phrase as a literal without trailing whitespace`() {
        val ctrl = makeCtrl(
            ctx = ImeContext(),
            classify = { buf ->
                com.hkmixedkeyboard.engine.ClassifyResult(
                    buffer = buf,
                    cnExactParsed = false, cnHasPhraseMatch = true, cnPrefixParsed = false,
                    cnCandidates = listOf(cnPhrase("唔該", "rryo", isHkCore = true)),
                    enLiteral = buf, enAutocomplete = null, enIsWord = false, enStrongPrefix = false
                )
            }
        )

        val out = ctrl.onSpace(ImeStateData(buffer = "rryo", imeState = ImeState.COMPOSING))

        assertEquals("rryo", out.committedText)
        assertEquals("", out.newState.buffer)
    }

    @Test
    fun `Space flushes Jyutping buffer as literal plus space`() {
        val ctrl = makeCtrl(
            ctx = ImeContext(
                scheme = com.hkmixedkeyboard.decoder.Scheme.JYUTPING
            ),
            classify = { collision(it, "個", isHkCore = false, enIsWord = true) }
        )

        val out = ctrl.onSpace(ImeStateData(buffer = "go", imeState = ImeState.COMPOSING))

        assertEquals("go ", out.committedText)
        assertEquals("", out.newState.buffer)
    }

    @Test
    fun `Punctuation commits composed literal and half-width punctuation for English`() {
        val ctrl = makeCtrl(classify = { clearEnglish(it) })

        // Composing an English word then comma → the word commits as English, so the
        // comma follows suit and is half-width.
        val out = ctrl.onPunctuation(
            "，",
            ImeStateData(buffer = "send", imeState = ImeState.COMPOSING)
        )

        assertEquals("send,", out.committedText)
    }

    @Test
    fun `Punctuation flushes latin buffer without selecting preview candidate`() {
        val ctrl = makeCtrl(classify = { clearChinese(it, "唔", isHkCore = true) })

        val out = ctrl.onPunctuation(
            "。",
            ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING)
        )

        assertEquals("rr.", out.committedText)
    }

    @Test
    fun `Punctuation does not auto-commit Chinese preview candidate`() {
        val ctrl = makeCtrl(classify = { clearChinese(it, "唔", isHkCore = true) })

        val out = ctrl.onPunctuation(
            "。",
            ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING)
        )

        assertEquals("rr.", out.committedText)
    }

    @Test
    fun `Punctuation after Latin context uses half-width when buffer empty`() {
        val ctrl = makeCtrl()

        val out = ctrl.onPunctuation(
            "。",
            ImeStateData(buffer = "", imeState = ImeState.IDLE),
            PrecedingContext.LATIN
        )

        assertEquals(".", out.committedText)
    }

    @Test
    fun `Punctuation after CJK context uses full-width when buffer empty`() {
        val ctrl = makeCtrl()

        val out = ctrl.onPunctuation(
            "？",
            ImeStateData(buffer = "", imeState = ImeState.IDLE),
            PrecedingContext.CJK
        )

        assertEquals("？", out.committedText)
    }

    @Test
    fun `Punctuation in neutral context defaults to full-width`() {
        val ctrl = makeCtrl()

        val out = ctrl.onPunctuation(
            "！",
            ImeStateData(buffer = "", imeState = ImeState.IDLE),
            PrecedingContext.NEUTRAL
        )

        assertEquals("！", out.committedText)
    }

    // ── 6. Space does NOT commit Chinese for collision when no memory ───────

    @Test
    fun `Space on ok collision commits ok literal cold-start (not HK core)`() {
        val ctrl = makeCtrl(classify = { buf ->
            // ok has both 仗 (not HK core) and EN word
            collision(buf, "仗", isHkCore = false, enIsWord = true)
        })
        val state = ImeStateData(buffer = "ok", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)

        assertEquals("ok", out.committedText)
    }

    @Test
    fun `Space on 2-letter rr commits EN literal (short input stays English)`() {
        val ctrl = makeCtrl(classify = { buf ->
            collision(buf, "唔", isHkCore = true, enIsWord = false)
        })
        val state = ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)

        // Two Latin letters + Space stay English; 唔 remains available by tapping.
        assertEquals("rr", out.committedText)
    }

    @Test
    fun `Space on 2-letter hi commits the literal without trailing whitespace`() {
        // Even when both a HK-core Chinese char and an EN word collide on "hi",
        // Space emits the raw literal without selecting the preview candidate.
        val ctrl = makeCtrl(classify = { buf ->
            collision(buf, "我", isHkCore = true, enIsWord = true)
        })
        val state = ImeStateData(buffer = "hi", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)

        assertEquals("hi", out.committedText)
        assertEquals("", out.newState.buffer)
        assertEquals(ImeState.PREDICTING, out.newState.imeState)
    }
}
