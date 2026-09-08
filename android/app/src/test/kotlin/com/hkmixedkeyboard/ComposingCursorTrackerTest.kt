package com.hkmixedkeyboard

import com.hkmixedkeyboard.ime.ComposingCursorTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposingCursorTrackerTest {

    @Test
    fun `composing text advances the caret past the region`() {
        val t = ComposingCursorTracker()
        t.syncTo(10, 10)
        t.onCompose(1)   // "h"
        assertEquals(11, t.cursor)
        t.onCompose(2)   // "he" replaces "h"
        assertEquals(12, t.cursor)
        t.onCompose(5)   // "hello"
        assertEquals(15, t.cursor)
        assertEquals(5, t.composingLength)
    }

    @Test
    fun `commit replaces the composing region rather than appending to it`() {
        val t = ComposingCursorTracker()
        t.syncTo(10, 10)
        t.onCompose(3)              // caret 13, region [10,13)
        t.onCommit(1)               // one Chinese char replaces the 3-letter code
        assertEquals(11, t.cursor)
        assertEquals(0, t.composingLength)
    }

    @Test
    fun `finishing composing leaves the caret where it is`() {
        val t = ComposingCursorTracker()
        t.syncTo(4, 4)
        t.onCompose(3)
        assertEquals(7, t.cursor)
        t.onFinishComposing()
        assertEquals(7, t.cursor)
        assertEquals(0, t.composingLength)
        // A following commit must not subtract the finished region again.
        t.onCommit(2)
        assertEquals(9, t.cursor)
    }

    @Test
    fun `deleting before the caret moves it back`() {
        val t = ComposingCursorTracker()
        t.syncTo(8, 8)
        t.onDeleteBefore(3)
        assertEquals(5, t.cursor)
    }

    @Test
    fun `an unknown mirror stays unknown through every edit`() {
        val t = ComposingCursorTracker()
        t.invalidate()
        t.onCompose(4)
        t.onCommit(2)
        t.onDeleteBefore(1)
        assertEquals(ComposingCursorTracker.UNKNOWN, t.cursor)
    }

    @Test
    fun `syncing to a range gives up the mirror rather than guessing`() {
        val t = ComposingCursorTracker()
        t.syncTo(3, 9)
        assertEquals(ComposingCursorTracker.UNKNOWN, t.cursor)
    }

    @Test
    fun `an editor that reports no initial selection leaves the mirror unknown`() {
        val t = ComposingCursorTracker()
        t.syncTo(-1, -1)
        assertEquals(ComposingCursorTracker.UNKNOWN, t.cursor)
    }

    @Test
    fun `a belated report of an already-passed position is still ours`() {
        // Three fast keystrokes; the editor has confirmed none of them yet.
        val t = ComposingCursorTracker()
        t.syncTo(0, 0)
        t.onCompose(1)
        t.onCompose(2)
        t.onCompose(3)
        assertEquals(3, t.cursor)
        // The callback for the first keystroke arrives now. Reading it as the user
        // moving the caret would drop the composition mid-word.
        assertTrue(t.confirm(1))
        assertTrue(t.confirm(3))
    }

    @Test
    fun `a position we never produced is not ours`() {
        val t = ComposingCursorTracker()
        t.syncTo(0, 0)
        t.onCompose(5)
        assertFalse(t.confirm(2))
    }

    @Test
    fun `each pending position confirms only once`() {
        val t = ComposingCursorTracker()
        t.syncTo(0, 0)
        t.onCompose(1)
        t.onCompose(2)
        assertTrue(t.confirm(2))
        // 1 was consumed along with 2, and 2 is now the settled position.
        assertFalse(t.confirm(1))
        assertTrue(t.confirm(2))
    }

    @Test
    fun `the settled position still confirms after a re-sync`() {
        val t = ComposingCursorTracker()
        t.syncTo(7, 7)
        assertTrue("a redundant report at the synced caret is not a user move", t.confirm(7))
        assertFalse(t.confirm(4))
    }

    @Test
    fun `nothing confirms without a mirror`() {
        val t = ComposingCursorTracker()
        t.invalidate()
        assertFalse(t.confirm(0))
        assertFalse(t.hasMirror)
    }

    @Test
    fun `the caret never goes negative`() {
        val t = ComposingCursorTracker()
        t.syncTo(2, 2)
        t.onDeleteBefore(5)
        assertEquals(0, t.cursor)
    }
}
