package com.hkmixedkeyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.hkmixedkeyboard.ime.EnterKeyAction
import com.hkmixedkeyboard.ime.WordDeletePolicy
import com.hkmixedkeyboard.engine.IdleSuggestionPolicy
import com.hkmixedkeyboard.memory.MemorySuggestion
import com.hkmixedkeyboard.ui.KeyTouchPolicy
import com.hkmixedkeyboard.ui.MainKeyboardLongPressPolicy
import com.hkmixedkeyboard.ui.KeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ── Optional number row ───────────────────────────────────────────────

    @Test
    fun `dropping the number row removes a row and its height`() {
        val withRow = KeyboardLayout.rowsFor(showNumberRow = true)
        val without = KeyboardLayout.rowsFor(showNumberRow = false)

        assertEquals(withRow.size - 1, without.size)
        assertTrue("digits should be gone", without.none { r -> r.keys.any { it.label == "1" } })
        assertTrue(
            KeyboardLayout.keyboardHeightPx(2f, showNumberRow = false) <
                KeyboardLayout.keyboardHeightPx(2f, showNumberRow = true)
        )
    }

    @Test
    fun `the letter and bottom rows are untouched by the number row setting`() {
        val without = KeyboardLayout.rowsFor(showNumberRow = false)

        assertEquals(listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            without.first().keys.map { it.label })
        assertEquals(listOf("符", "😊", "⌨", " ", "。", "↵"),
            without.last().keys.map { it.label })
    }

    @Test
    fun `cells are laid out for whichever row set is showing`() {
        val cells = KeyboardLayout.buildCells(1000f, 1000f, showNumberRow = false)

        assertTrue(cells.none { it.key.label == "1" })
        assertEquals(0f, cells.first().bounds.top, 0.001f)
    }

    // ── Long-press alternates on the main layer ───────────────────────────

    @Test
    fun `digits reach their symbol on a hold`() {
        assertEquals("!", MainKeyboardLongPressPolicy.longPressTextFor("1"))
        assertEquals("@", MainKeyboardLongPressPolicy.longPressTextFor("2"))
        assertEquals(")", MainKeyboardLongPressPolicy.longPressTextFor("0"))
    }

    @Test
    fun `the full stop keeps its Latin alternate and letters have none`() {
        assertEquals(".", MainKeyboardLongPressPolicy.longPressTextFor(KeyboardLayout.KEY_PERIOD))
        assertNull(MainKeyboardLongPressPolicy.longPressTextFor("Q"))
    }

    @Test
    fun `a key with an alternate waits for the tap-or-hold outcome`() {
        // Emitting on press would commit the digit before the hold could resolve.
        assertFalse(KeyTouchPolicy.emitsOnPress("1"))
        assertTrue(KeyTouchPolicy.usesHoldGesture("1"))
        assertTrue(KeyTouchPolicy.emitsOnPress("Q"))
    }

    // ── Idle strip ────────────────────────────────────────────────────────

    @Test
    fun `an empty history still fills the idle strip`() {
        val idle = IdleSuggestionPolicy.suggestions(emptyList())

        assertEquals(IdleSuggestionPolicy.LIMIT, idle.size)
        assertEquals("唔該", idle.first().text)
    }

    @Test
    fun `the user's own words lead the starters`() {
        val learned = listOf(
            MemorySuggestion(cnPhrase("香港", "haeu"), count = 9, isExactBuffer = true),
            MemorySuggestion(cnChar("我", "hqi"), count = 4, isExactBuffer = true)
        )
        val idle = IdleSuggestionPolicy.suggestions(learned)

        assertEquals(listOf("香港", "我"), idle.take(2).map { it.text })
        assertEquals(IdleSuggestionPolicy.LIMIT, idle.size)
    }

    @Test
    fun `English literals never reach the idle strip`() {
        val learned = listOf(MemorySuggestion(enLiteralCand("send"), count = 20, isExactBuffer = true))
        val idle = IdleSuggestionPolicy.suggestions(learned)

        assertTrue(idle.none { it.text == "send" })
    }

    @Test
    fun `every hold-gesture key can still resolve a plain tap`() {
        // The release path used to name its keys explicitly, so a key added to
        // usesHoldGesture but missed there fired its long press on a tap. Any key
        // that holds must therefore either be space, or have a tap outcome.
        val holdKeys = (KeyboardLayout.rowsFor(showNumberRow = true)
            .flatMap { it.keys }
            .map { it.label } + listOf(KeyboardLayout.KEY_QUESTION))
            .filter { KeyTouchPolicy.usesHoldGesture(it) }

        assertTrue("expected several hold keys, got $holdKeys", holdKeys.size >= 4)
        holdKeys.forEach { label ->
            // Space steers the caret; the rest name a tap outcome either in
            // beginGesture (backspace, ？！, 符, ⌨) or through the policy map.
            val resolvable = label in setOf(
                KeyboardLayout.KEY_SPACE,
                KeyboardLayout.KEY_BACKSPACE,
                KeyboardLayout.KEY_QUESTION,
                KeyboardLayout.KEY_SYMBOL,
                KeyboardLayout.KEY_MODE
            ) || MainKeyboardLongPressPolicy.longPressTextFor(label) != null
            assertTrue("$label holds but has no tap outcome", resolvable)
        }
    }
}
