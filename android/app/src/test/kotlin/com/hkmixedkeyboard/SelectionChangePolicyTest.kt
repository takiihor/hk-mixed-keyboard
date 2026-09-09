package com.hkmixedkeyboard

import com.hkmixedkeyboard.ime.SelectionChangePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionChangePolicyTest {

    private fun isExternal(
        buffer: String = "hello",
        matchesOurEdit: Boolean = true,
        haveMirror: Boolean = true,
        selStart: Int = 5,
        selEnd: Int = 5,
        candStart: Int = 0,
        candEnd: Int = 5
    ) = SelectionChangePolicy.isExternalMove(
        buffer, matchesOurEdit, haveMirror, selStart, selEnd, candStart, candEnd
    )

    @Test
    fun `our own composing update is not an external move`() {
        assertFalse(isExternal(matchesOurEdit = true))
    }

    @Test
    fun `tapping elsewhere while composing is an external move`() {
        assertTrue(isExternal(matchesOurEdit = false, selStart = 2, selEnd = 2))
    }

    @Test
    fun `a caret move with nothing composing is ignored`() {
        assertFalse(isExternal(buffer = "", matchesOurEdit = false, selStart = 2, selEnd = 2))
    }

    @Test
    fun `selecting a range while composing is an external move`() {
        assertTrue(isExternal(matchesOurEdit = true, selStart = 1, selEnd = 4))
        // Even when the range ends where our own edit left the caret.
        assertTrue(isExternal(matchesOurEdit = true, selStart = 2, selEnd = 5))
    }

    @Test
    fun `falls back to the reported composing region when there is no mirror`() {
        assertFalse(
            isExternal(haveMirror = false, matchesOurEdit = false, selStart = 5, selEnd = 5, candEnd = 5)
        )
        assertTrue(
            isExternal(haveMirror = false, matchesOurEdit = false, selStart = 2, selEnd = 2, candEnd = 5)
        )
    }

    @Test
    fun `with neither signal available nothing is treated as a move`() {
        // Resetting on every keystroke would be far worse than the bug being fixed.
        assertFalse(
            isExternal(
                haveMirror = false, matchesOurEdit = false,
                selStart = 2, selEnd = 2, candStart = -1, candEnd = -1
            )
        )
    }

    @Test
    fun `the mirror wins over a stale reported composing region`() {
        assertTrue(isExternal(matchesOurEdit = false, selStart = 5, selEnd = 5, candEnd = 5))
    }
}
