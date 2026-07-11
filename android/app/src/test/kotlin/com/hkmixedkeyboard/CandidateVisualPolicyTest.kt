package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.ui.CandidateVisualPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateVisualPolicyTest {

    @Test
    fun `learned candidate is priority even when it is not hk core`() {
        val learned = cnChar("估", "or", isHkCore = false)
            .copy(sourceSchema = SourceSchema.USER_MEMORY)

        assertTrue(CandidateVisualPolicy.isPriority(learned))
    }

    @Test
    fun `hk core candidate remains priority`() {
        assertTrue(CandidateVisualPolicy.isPriority(cnChar("唔", "rr", isHkCore = true)))
    }

    @Test
    fun `ordinary quick candidate is not priority`() {
        assertFalse(CandidateVisualPolicy.isPriority(cnChar("估", "or", isHkCore = false)))
    }
}
