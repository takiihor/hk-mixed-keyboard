package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.KeyboardMode
import com.hkmixedkeyboard.ui.KeyboardThemeColors
import com.hkmixedkeyboard.ui.SymbolKeyboardState
import com.hkmixedkeyboard.ui.SymbolPage
import com.hkmixedkeyboard.ui.toColors
import com.hkmixedkeyboard.settings.KeyboardTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardThemeColorsTest {

    @Test
    fun `dark theme resolves to the current approved keyboard palette`() {
        val dark = KeyboardTheme.DARK.toColors()

        assertEquals(0xFF202124.toInt(), dark.keyboardBackground)
        assertEquals(0xFF3C4043.toInt(), dark.keyBackground)
        assertEquals(0xFF26282A.toInt(), dark.specialKeyBackground)
        assertEquals(0xFFE8EAED.toInt(), dark.label)
        assertEquals(0xFF15191C.toInt(), dark.symbolKeyboardBackground)
    }

    @Test
    fun `iPhone style light theme separates character and function keys`() {
        val light = KeyboardTheme.IOS_LIGHT.toColors()

        assertEquals(0xFFD1D5DB.toInt(), light.keyboardBackground)
        assertEquals(0xFFFFFFFF.toInt(), light.keyBackground)
        assertEquals(0xFFAEB4BD.toInt(), light.specialKeyBackground)
        assertNotEquals(light.keyBackground, light.specialKeyBackground)
        assertEquals(0xFF111214.toInt(), light.label)
        assertEquals(0xFFC4C8CE.toInt(), light.pressedKeyBackground)
        assertEquals(0xFF969DA7.toInt(), light.pressedSpecialKeyBackground)
    }

    @Test
    fun `iPhone style light theme keeps priority candidates blue`() {
        val light = KeyboardTheme.IOS_LIGHT.toColors()

        assertEquals(0xFF0645AD.toInt(), light.candidatePriorityText)
        assertNotEquals(light.candidateText, light.candidatePriorityText)
    }

    @Test
    fun `essential text meets WCAG AA contrast in every theme`() {
        KeyboardTheme.entries.map { it.toColors() }.forEach { colors ->
            listOf(
                colors.label to colors.keyBackground,
                colors.hint to colors.keyBackground,
                colors.enterLabel to colors.enterKeyBackground,
                colors.candidateText to colors.candidateBackground,
                colors.candidatePriorityText to colors.candidateBackground,
                colors.symbolLabel to colors.symbolKeyBackground,
                colors.safeModeText to colors.safeModeBackground
            ).forEach { (foreground, background) ->
                assertTrue("contrast must be >= 4.5", contrast(foreground, background) >= 4.5)
            }
        }
    }

    @Test
    fun `iPhone style light theme supplies surrounding Emoji UI tokens`() {
        val light = KeyboardTheme.IOS_LIGHT.toColors()

        assertEquals(0xFFD1D5DB.toInt(), light.emojiPanelBackground)
        assertEquals(0xFFAEB4BD.toInt(), light.emojiCategoryBarBackground)
        assertEquals(0xFFFFFFFF.toInt(), light.emojiSearchBackground)
        assertEquals(0xFF191B1E.toInt(), light.emojiFunctionIcon)
        assertEquals(0xFFC4C8CE.toInt(), light.emojiGridPressedBackground)
        assertEquals(0xFF202326.toInt(), light.emojiSelectionIndicator)
    }

    @Test
    fun `symbol palette is held by one immutable keyboard theme token object`() {
        val dark = KeyboardThemeColors(
            keyboardBackground = 0xFF15191C.toInt(),
            keyBackground = 1,
            specialKeyBackground = 2,
            spaceKeyBackground = 3,
            pressedKeyBackground = 4,
            enterKeyBackground = 5,
            label = 6,
            hint = 7,
            enterLabel = 8,
            popupBackground = 9,
            popupLabel = 10,
            symbolKeyboardBackground = 11,
            symbolKeyBackground = 12,
            symbolFunctionKeyBackground = 13,
            symbolPressedKeyBackground = 14,
            symbolLabel = 15,
            symbolIndicatorActive = 16,
            symbolIndicatorInactive = 17,
            symbolPopupBackground = 18,
            symbolPopupLabel = 19
        )

        assertEquals(12, dark.symbolKeyBackground)
        assertEquals(15, dark.symbolLabel)
        assertNotEquals(dark, dark.copy(symbolKeyBackground = 20))
    }

    @Test
    fun `rebinding theme presentation does not change symbol page state`() {
        val state = SymbolKeyboardState(KeyboardMode.SYMBOLS, SymbolPage.EXTENDED)
        val dark = fixture(symbolKeyBackground = 0xFF3C4043.toInt())
        val light = dark.copy(
            keyboardBackground = 0xFFF2F4F5.toInt(),
            symbolKeyBackground = 0xFFE1E5E8.toInt(),
            symbolLabel = 0xFF202124.toInt()
        )

        assertNotEquals(dark, light)
        assertEquals(SymbolKeyboardState(KeyboardMode.SYMBOLS, SymbolPage.EXTENDED), state)
    }

    private fun fixture(symbolKeyBackground: Int) = KeyboardThemeColors(
        keyboardBackground = 0xFF15191C.toInt(),
        keyBackground = 1,
        specialKeyBackground = 2,
        spaceKeyBackground = 3,
        pressedKeyBackground = 4,
        enterKeyBackground = 5,
        label = 6,
        hint = 7,
        enterLabel = 8,
        popupBackground = 9,
        popupLabel = 10,
        symbolKeyboardBackground = 11,
        symbolKeyBackground = symbolKeyBackground,
        symbolFunctionKeyBackground = 13,
        symbolPressedKeyBackground = 14,
        symbolLabel = 15,
        symbolIndicatorActive = 16,
        symbolIndicatorInactive = 17,
        symbolPopupBackground = 18,
        symbolPopupLabel = 19
    )

    private fun contrast(foreground: Int, background: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val value = ((color ushr shift) and 0xFF) / 255.0
                return if (value <= 0.04045) value / 12.92
                else Math.pow((value + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        val first = luminance(foreground)
        val second = luminance(background)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }
}
