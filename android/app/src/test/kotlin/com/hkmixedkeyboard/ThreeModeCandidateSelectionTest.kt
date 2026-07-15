package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.ime.PinyinImePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test


class ThreeModeCandidateSelectionTest {
    @Test
    fun `exact Quick candidate is eligible for Space selection`() {
        val candidate = candidate("唔", "rr", SourceSchema.QUICK, CandidateType.CHAR)

        assertEquals(
            candidate,
            PinyinImePolicy.spaceCandidate(Scheme.QUICK, "rr", listOf(candidate))
        )
    }

    @Test
    fun `exact Jyutping candidate is eligible for Space selection`() {
        val candidate = candidate(
            "你好", "neihou", SourceSchema.JYUTPING, CandidateType.JYUTPING
        )

        assertEquals(
            candidate,
            PinyinImePolicy.spaceCandidate(Scheme.JYUTPING, "neihou", listOf(candidate))
        )
    }

    @Test
    fun `common Quick English collisions remain literal on Space`() {
        listOf("ok" to "仗", "hi" to "我", "go" to "個").forEach { (code, text) ->
            val candidate = candidate(text, code, SourceSchema.QUICK, CandidateType.CHAR)

            assertNull(
                PinyinImePolicy.spaceCandidate(Scheme.QUICK, code, listOf(candidate))
            )
        }
    }

    private fun candidate(
        text: String,
        code: String,
        source: SourceSchema,
        type: CandidateType
    ) = DecodeCandidate(text, code, source, type, 1.0, isHkCore = true)
}
