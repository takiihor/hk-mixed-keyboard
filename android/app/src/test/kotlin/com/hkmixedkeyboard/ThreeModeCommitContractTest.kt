package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.ImeContext
import com.hkmixedkeyboard.commit.ImeState
import com.hkmixedkeyboard.commit.ImeStateData
import com.hkmixedkeyboard.commit.PrecedingContext
import com.hkmixedkeyboard.commit.DeletionRequest
import com.hkmixedkeyboard.commit.DeletionUnit
import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.memory.UserMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test


class ThreeModeCommitContractTest {
    @Test
    fun `Space commits an exact Quick candidate supplied by the active composition`() {
        assertSpaceCommit(
            scheme = Scheme.QUICK,
            buffer = "rr",
            candidate = candidate("唔", "rr", SourceSchema.QUICK, CandidateType.CHAR)
        )
    }

    @Test
    fun `Space commits an exact Jyutping candidate supplied by the active composition`() {
        assertSpaceCommit(
            scheme = Scheme.JYUTPING,
            buffer = "neihou",
            candidate = candidate(
                "你好", "neihou", SourceSchema.JYUTPING, CandidateType.JYUTPING
            )
        )
    }

    @Test
    fun `Space records enough information for Backspace to restore composition`() {
        val ctrl = makeCtrl(
            memory = UserMemory(),
            ctx = ImeContext(scheme = Scheme.PINYIN)
        )
        val committed = ctrl.onSpace(
            ImeStateData(buffer = "nihao", imeState = ImeState.COMPOSING),
            candidate("你好", "nihao", SourceSchema.PINYIN, CandidateType.PHRASE)
        )

        val restored = ctrl.onBackspace(committed.newState)

        assertEquals(DeletionRequest(DeletionUnit.UTF16_UNITS, 2), restored.deletion)
        assertEquals("nihao", restored.newState.buffer)
        assertEquals(ImeState.COMPOSING, restored.newState.imeState)
    }

    @Test
    fun `ordinary Backspace requests one code point deletion`() {
        val ctrl = makeCtrl(
            memory = UserMemory(),
            ctx = ImeContext(scheme = Scheme.QUICK)
        )

        val deleted = ctrl.onBackspace(ImeStateData())

        assertEquals(DeletionRequest(DeletionUnit.CODE_POINTS, 1), deleted.deletion)
    }

    @Test
    fun `punctuation commits a fresh Jyutping candidate before Chinese punctuation`() {
        val ctrl = makeCtrl(
            memory = UserMemory(),
            ctx = ImeContext(scheme = Scheme.JYUTPING)
        )
        val candidate = candidate(
            "你好", "neihou", SourceSchema.JYUTPING, CandidateType.JYUTPING
        )

        val out = ctrl.onPunctuation(
            "。",
            ImeStateData(buffer = "neihou", imeState = ImeState.COMPOSING),
            PrecedingContext.NEUTRAL,
            candidate
        )

        assertEquals("你好。", out.committedText)
        assertEquals(candidate, out.memoryWrite.candidate)
        assertEquals("", out.newState.buffer)
    }

    private fun assertSpaceCommit(
        scheme: Scheme,
        buffer: String,
        candidate: DecodeCandidate
    ) {
        val ctrl = makeCtrl(memory = UserMemory(), ctx = ImeContext(scheme = scheme))

        val out = ctrl.onSpace(
            ImeStateData(buffer = buffer, imeState = ImeState.COMPOSING),
            candidate
        )

        assertEquals(candidate.text, out.committedText)
        assertEquals("", out.newState.buffer)
        assertEquals(candidate.text, out.newState.prevCommitted)
        assertNotNull(out.newState.lastAutoCommit)
        assertEquals(buffer, out.newState.lastAutoCommit?.originalBuffer)
    }

    private fun candidate(
        text: String,
        code: String,
        source: SourceSchema,
        type: CandidateType
    ) = DecodeCandidate(
        text = text,
        code = code,
        sourceSchema = source,
        type = type,
        frequency = 1.0,
        isHkCore = true
    )
}
