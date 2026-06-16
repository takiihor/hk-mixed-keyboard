package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.SortedPrefixIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class SortedPrefixIndexTest {

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
