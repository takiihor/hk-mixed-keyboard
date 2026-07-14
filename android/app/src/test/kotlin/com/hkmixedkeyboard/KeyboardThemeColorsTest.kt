package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.KeyboardMode
import com.hkmixedkeyboard.ui.KeyboardThemeColors
import com.hkmixedkeyboard.ui.SymbolKeyboardState
import com.hkmixedkeyboard.ui.SymbolPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class KeyboardThemeColorsTest {

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
}
