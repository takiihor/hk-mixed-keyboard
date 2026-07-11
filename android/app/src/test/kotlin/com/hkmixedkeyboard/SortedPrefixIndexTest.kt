package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.SortedPrefixIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SortedPrefixIndexTest {

    @Test
    fun `hasPrefix matches the old prefix-set contains semantics`() {
        val index = SortedPrefixIndex(listOf("command", "comment", "community", "happy"))

        // Every proper prefix of a key is covered, including the full key itself.
        assertTrue(index.hasPrefix("c"))
        assertTrue(index.hasPrefix("comm"))
        assertTrue(index.hasPrefix("command"))
        assertTrue(index.hasPrefix("h"))

        // Nothing starts with these.
        assertFalse(index.hasPrefix("z"))
        assertFalse(index.hasPrefix("comz"))
        assertFalse(index.hasPrefix("commando")) // longer than any key
        assertFalse(index.hasPrefix(""))
    }

    @Test
    fun `matching returns only keys in the requested prefix range`() {
        val index = SortedPrefixIndex(
            listOf("command", "comment", "communication", "community", "confirm", "happy")
        )

        assertEquals(
            listOf("command", "comment", "communication", "community"),
            index.matching("comm")
        )
    }

    @Test
    fun `matching respects result limit`() {
        val index = SortedPrefixIndex(listOf("communicate", "communication", "community"))

        assertEquals(listOf("communicate", "communication"), index.matching("comm", limit = 2))
    }
}
