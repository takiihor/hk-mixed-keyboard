package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.KeyboardAccessibilityLabels
import com.hkmixedkeyboard.ui.KeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KeyboardAccessibilityLabelsTest {
    @Test
    fun `Cangjie letter exposes its Chinese root and key face`() {
        assertEquals(
            "日字根，a鍵",
            KeyboardAccessibilityLabels.descriptionFor("A", showCangjieRoots = true)
        )
    }

    @Test
    fun `plain letter exposes its shifted key face`() {
        assertEquals(
            "A鍵",
            KeyboardAccessibilityLabels.descriptionFor(
                "A",
                showCangjieRoots = false,
                shiftActive = true
            )
        )
    }

    @Test
    fun `special keys have Chinese descriptions`() {
        assertEquals("空格鍵，現時粵拼", KeyboardAccessibilityLabels.descriptionFor(
            KeyboardLayout.KEY_SPACE, spaceLabel = "粵拼"
        ))
        assertEquals("刪除鍵", KeyboardAccessibilityLabels.descriptionFor(KeyboardLayout.KEY_BACKSPACE))
        assertEquals("換行鍵", KeyboardAccessibilityLabels.descriptionFor(KeyboardLayout.KEY_ENTER))
        assertEquals("切換到大寫", KeyboardAccessibilityLabels.descriptionFor(KeyboardLayout.KEY_SHIFT))
        assertEquals("大寫鎖定", KeyboardAccessibilityLabels.descriptionFor(
            KeyboardLayout.KEY_SHIFT, shiftActive = true, shiftLocked = true
        ))
        assertEquals("符號鍵", KeyboardAccessibilityLabels.descriptionFor(KeyboardLayout.KEY_SYMBOL))
        assertEquals("表情符號鍵", KeyboardAccessibilityLabels.descriptionFor(KeyboardLayout.KEY_EMOJI))
        assertEquals("逗號鍵", KeyboardAccessibilityLabels.descriptionFor(KeyboardLayout.KEY_COMMA))
        assertEquals("句號鍵", KeyboardAccessibilityLabels.descriptionFor(KeyboardLayout.KEY_PERIOD))
        assertEquals("問號或驚嘆號鍵", KeyboardAccessibilityLabels.descriptionFor(KeyboardLayout.KEY_QUESTION))
    }

    @Test
    fun `scheme switch names the active Chinese input mode`() {
        val description = KeyboardAccessibilityLabels.descriptionFor(
            KeyboardLayout.KEY_MODE,
            modeLabel = "粵"
        )

        assertEquals("切換輸入模式，現時粵拼", description)
        assertFalse(description.contains("英文"))
    }
}
