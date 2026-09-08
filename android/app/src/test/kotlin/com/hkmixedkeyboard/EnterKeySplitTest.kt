package com.hkmixedkeyboard

import com.hkmixedkeyboard.ime.EnterKeySplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnterKeySplitTest {

    @Test
    fun `a bare newline becomes a key event with nothing to commit`() {
        val r = EnterKeySplit.split("\n", swallowEnter = false)
        assertEquals("", r.text)
        assertTrue(r.sendEnter)
    }

    @Test
    fun `flushed text is committed before the Enter key event`() {
        // A terminal's pass-through Enter finalizing "ls": commit "ls" as text, then
        // send Enter, or the shell receives a literal newline and runs nothing.
        val r = EnterKeySplit.split("ls\n", swallowEnter = false)
        assertEquals("ls", r.text)
        assertTrue(r.sendEnter)
    }

    @Test
    fun `a swallowed Enter commits its text and sends no key event`() {
        val r = EnterKeySplit.split("你好", swallowEnter = true)
        assertEquals("你好", r.text)
        assertFalse(r.sendEnter)
    }

    @Test
    fun `ordinary committed text is left alone`() {
        val r = EnterKeySplit.split("你好", swallowEnter = false)
        assertEquals("你好", r.text)
        assertFalse(r.sendEnter)
    }

    @Test
    fun `a trailing newline is not sent when the Enter was swallowed`() {
        val r = EnterKeySplit.split("ls\n", swallowEnter = true)
        assertEquals("ls\n", r.text)
        assertFalse(r.sendEnter)
    }
}
