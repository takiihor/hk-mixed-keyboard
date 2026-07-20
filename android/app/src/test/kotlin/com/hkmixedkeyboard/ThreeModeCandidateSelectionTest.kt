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
    fun `Quick never resolves a Space candidate`() {
        listOf(SourceSchema.QUICK, SourceSchema.CUSTOM_QUICK).forEach { source ->
            val candidate = candidate("唔", "rr", source, CandidateType.CHAR)

            assertNull(
                PinyinImePolicy.spaceCandidate(Scheme.QUICK, "rr", listOf(candidate))
            )
        }
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

    private fun candidate(
        text: String,
        code: String,
        source: SourceSchema,
        type: CandidateType
    ) = DecodeCandidate(text, code, source, type, 1.0, isHkCore = true)
}
