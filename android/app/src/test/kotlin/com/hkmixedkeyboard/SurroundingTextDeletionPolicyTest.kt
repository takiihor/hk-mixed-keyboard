package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.DeletionRequest
import com.hkmixedkeyboard.commit.DeletionUnit
import com.hkmixedkeyboard.ime.SurroundingTextDeletionPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class SurroundingTextDeletionPolicyTest {
    @Test
    fun `falls back to one UTF-16 unit for a BMP code point`() {
        assertEquals(1, fallbackFor("A"))
    }

    @Test
    fun `falls back to two UTF-16 units for an emoji code point`() {
        assertEquals(2, fallbackFor("😀"))
    }

    @Test
    fun `falls back to two UTF-16 units for a supplementary HKSCS code point`() {
        assertEquals(2, fallbackFor("𠬠"))
    }

    @Test
    fun `falls back to one UTF-16 unit without a complete preceding code point`() {
        assertEquals(1, fallbackFor(null))
        assertEquals(1, fallbackFor("\uD83D"))
    }

    @Test
    fun `uses the code point immediately before the cursor`() {
        assertEquals(1, fallbackFor("😀A"))
    }

    @Test
    fun `preserves explicit UTF-16 deletion requests`() {
        assertEquals(
            2,
            SurroundingTextDeletionPolicy.utf16UnitsForFallback(
                DeletionRequest(DeletionUnit.UTF16_UNITS, 2),
                "😀"
            )
        )
    }

    private fun fallbackFor(beforeCursor: CharSequence?): Int =
        SurroundingTextDeletionPolicy.utf16UnitsForFallback(
            DeletionRequest(DeletionUnit.CODE_POINTS, 1),
            beforeCursor
        )
}
