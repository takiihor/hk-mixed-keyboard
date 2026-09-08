package com.hkmixedkeyboard

import com.hkmixedkeyboard.memory.MemoryIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suggestion lookup walks a contiguous range of the sorted bucket map instead of
 * filtering every buffer ever learned. At 10k learned buffers the old full scan
 * cost about 0.9 ms of decode latency per keystroke, and about 4 ms at 50k.
 */
class MemoryIndexScanTest {

    private fun indexWith(vararg buffers: String) = MemoryIndex().apply {
        buffers.forEach { record(it, cnChar("字", it)) }
    }

    @Test
    fun `only buffers sharing the prefix are returned`() {
        val index = indexWith("ab", "abc", "abd", "ac", "b", "zzz")

        val out = index.suggestions("ab", limit = 50)

        // One candidate text across ab/abc/abd, so the counts collapse into one
        // suggestion — what matters is that ac, b and zzz never contributed.
        assertEquals(1, out.size)
        assertEquals(3, out.single().count)
    }

    @Test
    fun `the range stops rather than running to the end of the map`() {
        val index = indexWith("aa", "ab", "b", "c", "d")

        // aa and ab both match "a"; b matches only itself; c and d are past the end.
        assertEquals(2, index.suggestions("a", limit = 50).single().count)
        assertEquals(1, index.suggestions("b", limit = 50).single().count)
        assertTrue(index.suggestions("q", limit = 50).isEmpty())
    }

    @Test
    fun `a prefix matches regardless of the case it was typed in`() {
        val index = indexWith("Abc")

        assertEquals(1, index.suggestions("ab", limit = 50).size)
        assertEquals(1, index.suggestions("AB", limit = 50).size)
    }

    @Test
    fun `case variants of one buffer accumulate instead of splitting`() {
        val index = MemoryIndex()
        index.record("ok", cnChar("好", "ok"))
        index.record("OK", cnChar("好", "OK"))
        index.record("Ok", cnChar("好", "Ok"))

        assertEquals(3, index.suggestions("ok", limit = 50).single().count)
        assertEquals(1, index.size())
    }

    @Test
    fun `Chinese runs range-scan the same way`() {
        val index = indexWith("我哋", "我哋今", "你哋")

        assertEquals(2, index.suggestions("我哋", limit = 50).single().count)
        assertEquals(1, index.suggestions("你", limit = 50).single().count)
    }
}
