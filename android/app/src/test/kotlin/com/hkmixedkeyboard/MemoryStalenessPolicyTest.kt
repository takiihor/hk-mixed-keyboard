package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.memory.MemoryStalenessPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryStalenessPolicyTest {
    private val policy = MemoryStalenessPolicy(
        quickContains = { it == "貓" },
        purgeVariant = { it == "爲" }
    )

    @Test
    fun `Pinyin Han candidates are retained without Quick corpus membership`() {
        assertFalse(policy.isStale(candidate("𨳍", SourceSchema.PINYIN)))
        assertFalse(policy.isStale(candidate("妳", SourceSchema.PINYIN)))
    }

    @Test
    fun `invalid non Han Pinyin memory is stale`() {
        assertTrue(policy.isStale(candidate("hello", SourceSchema.PINYIN)))
    }

    @Test
    fun `Quick Han candidate absent from Quick corpus remains stale`() {
        assertTrue(policy.isStale(candidate("妳", SourceSchema.QUICK)))
        assertFalse(policy.isStale(candidate("貓", SourceSchema.QUICK)))
    }

    @Test
    fun `one time variant purge still applies outside Pinyin`() {
        assertTrue(policy.isStale(candidate("爲", SourceSchema.QUICK)))
    }

    private fun candidate(text: String, source: SourceSchema) = DecodeCandidate(
        text, "code", source, CandidateType.CHAR, 1.0, false
    )
}
