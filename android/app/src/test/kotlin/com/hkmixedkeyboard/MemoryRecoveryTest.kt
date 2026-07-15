package com.hkmixedkeyboard

import com.hkmixedkeyboard.memory.MemoryIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRecoveryTest {
    @Test
    fun `personal weights are capped`() {
        val index = MemoryIndex()
        repeat(100) { index.record("nei", cnChar("你", "nei")) }

        assertTrue(index.suggestions("nei", 8).single().count <= MemoryIndex.MAX_PERSONAL_COUNT)
    }

    @Test
    fun `correcting an old choice lets ranking recover`() {
        val index = MemoryIndex()
        repeat(20) { index.record("nei", cnChar("呢", "nei")) }
        repeat(12) { index.record("nei", cnChar("你", "nei")) }

        assertEquals("你", index.suggestions("nei", 8).first().candidate.text)
    }
}
