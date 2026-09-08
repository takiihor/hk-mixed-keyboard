package com.hkmixedkeyboard

import android.text.Selection
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hkmixedkeyboard.ime.ComposingCursorTracker
import com.hkmixedkeyboard.ime.SelectionChangePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the InputConnection semantics that make an unhandled caret move visible to
 * the user: a composing region stays anchored where it was created, so a later
 * setComposingText() edits THAT text and drags the caret back to it.
 *
 * These run against [BaseInputConnection], the AOSP reference implementation of
 * those semantics, so they check the platform contract this fix depends on rather
 * than our own re-statement of it.
 */
@RunWith(AndroidJUnit4::class)
class ComposingRegionCaretTest {

    // fullEditor = true matters: in dummy mode BaseInputConnection clears its buffer
    // after every commitText() and re-sends the text as key events, so no committed
    // text would survive to compose against.
    private fun connection(): BaseInputConnection {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return BaseInputConnection(View(context), true)
    }

    @Test
    fun composingAfterAnUnhandledCaretMoveDragsTheCaretBack() {
        val ic = connection()
        ic.commitText("one two", 1)
        ic.setComposingText("hello", 1)
        val editable = ic.editable!!
        assertEquals("one twohello", editable.toString())

        // The user taps between "one" and " two".
        Selection.setSelection(editable, 3)
        ic.setComposingText("hellox", 1)

        // The region is still anchored at [7,12), so the keystroke lands there and
        // the caret follows it. This is the behaviour the service must prevent.
        assertEquals("one twohellox", editable.toString())
        assertEquals(13, Selection.getSelectionStart(editable))
    }

    @Test
    fun finishingTheCompositionFirstLeavesTheCaretWhereTheUserPutIt() {
        val ic = connection()
        ic.commitText("one two", 1)
        ic.setComposingText("hello", 1)
        val editable = ic.editable!!

        // What HkImeService.onUpdateSelection now does when it sees an external move.
        ic.finishComposingText()
        Selection.setSelection(editable, 3)
        ic.setComposingText("x", 1)

        assertEquals("onex twohello", editable.toString())
        assertEquals(4, Selection.getSelectionStart(editable))
    }

    @Test
    fun theCaretMirrorMatchesWhereTheEditorActuallyPutsIt() {
        val ic = connection()
        val tracker = ComposingCursorTracker()
        val editable = ic.editable!!

        ic.commitText("one two", 1)
        tracker.syncTo(Selection.getSelectionStart(editable), Selection.getSelectionEnd(editable))

        listOf("h", "he", "hel").forEach { buffer ->
            ic.setComposingText(buffer, 1)
            tracker.onCompose(buffer.length)
            assertEquals(
                "mirror must track the editor through composing updates",
                Selection.getSelectionStart(editable),
                tracker.cursor
            )
        }

        ic.commitText("兄", 1)
        tracker.onCommit(1)
        assertEquals(Selection.getSelectionStart(editable), tracker.cursor)

        // An accurate mirror is what lets the policy stay quiet on our own edits …
        val settled = Selection.getSelectionStart(editable)
        assertFalse(
            SelectionChangePolicy.isExternalMove(
                composingBuffer = "hel",
                matchesOurEdit = tracker.confirm(settled),
                haveMirror = tracker.hasMirror,
                newSelStart = settled,
                newSelEnd = settled,
                candidatesStart = -1,
                candidatesEnd = -1
            )
        )
        // … and still catch a real move in an editor reporting no composing region.
        assertTrue(
            SelectionChangePolicy.isExternalMove(
                composingBuffer = "hel",
                matchesOurEdit = tracker.confirm(2),
                haveMirror = tracker.hasMirror,
                newSelStart = 2,
                newSelEnd = 2,
                candidatesStart = -1,
                candidatesEnd = -1
            )
        )
    }
}
