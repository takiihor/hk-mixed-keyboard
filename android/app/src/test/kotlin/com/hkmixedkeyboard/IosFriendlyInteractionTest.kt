package com.hkmixedkeyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.hkmixedkeyboard.ime.EnterKeyAction
import com.hkmixedkeyboard.ime.WordDeletePolicy
import com.hkmixedkeyboard.ui.KeyTouchPolicy
import com.hkmixedkeyboard.ui.KeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Interactions reworked so an iPhone user's reflexes land somewhere sensible.
 */
class IosFriendlyInteractionTest {

    // ── Return key dresses itself as the field's action ───────────────────

    private fun action(imeOptions: Int, inputType: Int = InputType.TYPE_CLASS_TEXT) =
        EnterKeyAction.forEditor(imeOptions, inputType)

    @Test
    fun `a field with an action gets an accented, labelled return key`() {
        assertEquals("搜尋", action(EditorInfo.IME_ACTION_SEARCH).label)
        assertEquals("傳送", action(EditorInfo.IME_ACTION_SEND).label)
        assertEquals("前往", action(EditorInfo.IME_ACTION_GO).label)
        assertTrue(action(EditorInfo.IME_ACTION_SEARCH).accented)
    }

    @Test
    fun `a plain field keeps the newline arrow`() {
        val plain = action(EditorInfo.IME_ACTION_UNSPECIFIED)

        assertEquals("↵", plain.label)
        assertFalse(plain.accented)
    }

    @Test
    fun `a multi-line field is never dressed as a submit button`() {
        // Return inserts a newline there whatever the action bits claim, so
        // colouring it blue would promise something it does not do.
        val multiline = action(
            EditorInfo.IME_ACTION_SEND,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )

        assertEquals("↵", multiline.label)
        assertFalse(multiline.accented)
    }

    @Test
    fun `an editor that opts out of the enter action keeps the arrow`() {
        val optedOut = action(EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION)

        assertFalse(optedOut.accented)
    }

    // ── Whole-word backspace ──────────────────────────────────────────────

    @Test
    fun `a held backspace removes one Latin word`() {
        assertEquals(4, WordDeletePolicy.deleteCount("send"))
        assertEquals(5, WordDeletePolicy.deleteCount("please check"))
    }

    @Test
    fun `Latin and Han do not merge into one word`() {
        // "send個file" is three words to a reader, not one blob.
        assertEquals(4, WordDeletePolicy.deleteCount("send個file"))
        assertEquals(1, WordDeletePolicy.deleteCount("send個"))
    }

    @Test
    fun `a run of Han characters goes together`() {
        assertEquals(4, WordDeletePolicy.deleteCount("香港政府"))
        assertEquals(2, WordDeletePolicy.deleteCount("send香港"))
    }

    @Test
    fun `trailing spaces leave with the word they follow`() {
        assertEquals(7, WordDeletePolicy.deleteCount("check  "))
        assertEquals(4, WordDeletePolicy.deleteCount("香港  "))
    }

    @Test
    fun `punctuation is removed one glyph at a time`() {
        // Otherwise a run of "!!!???" would vanish in a single escalated step.
        assertEquals(1, WordDeletePolicy.deleteCount("wow!!!"))
    }

    @Test
    fun `nothing before the cursor deletes nothing`() {
        assertEquals(0, WordDeletePolicy.deleteCount(""))
        assertEquals(3, WordDeletePolicy.deleteCount("   "))
    }

    // ── The scheme key owns 簡體輸出 now ──────────────────────────────────

    @Test
    fun `the scheme key resolves through a tap-or-hold gesture`() {
        assertTrue(KeyTouchPolicy.usesHoldGesture(KeyboardLayout.KEY_MODE))
        assertFalse(KeyTouchPolicy.emitsOnPress(KeyboardLayout.KEY_MODE))
        // It must not also fire on release, or a hold would switch scheme as well
        // as toggling 簡體.
        assertFalse(KeyTouchPolicy.emitsOnRelease(KeyboardLayout.KEY_MODE, true))
    }

    @Test
    fun `space still owns its own gesture`() {
        assertTrue(KeyTouchPolicy.usesHoldGesture(KeyboardLayout.KEY_SPACE))
    }
}
