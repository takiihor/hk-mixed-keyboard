package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.ui.KeyboardMode
import com.hkmixedkeyboard.ui.SymbolKeyboardState
import com.hkmixedkeyboard.ui.SymbolPage
import org.junit.Assert.assertEquals
import org.junit.Test

class SymbolKeyboardStateTest {

    @Test
    fun `entering symbols always opens common page`() {
        val state = SymbolKeyboardState(
            keyboardMode = KeyboardMode.ALPHABET,
            symbolPage = SymbolPage.EXTENDED
        )

        assertEquals(
            SymbolKeyboardState(KeyboardMode.SYMBOLS, SymbolPage.COMMON),
            state.enterSymbols()
        )
    }

    @Test
    fun `symbol page toggles without changing keyboard mode`() {
        val state = SymbolKeyboardState(KeyboardMode.SYMBOLS, SymbolPage.COMMON)

        assertEquals(
            SymbolKeyboardState(KeyboardMode.SYMBOLS, SymbolPage.EXTENDED),
            state.toggleSymbolPage()
        )
    }

    @Test
    fun `returning to alphabet keeps symbol page independent from alphabet state`() {
        val alphabetScheme = Scheme.JYUTPING
        val alphabetShiftIsLocked = true
        val state = SymbolKeyboardState(KeyboardMode.SYMBOLS, SymbolPage.EXTENDED)

        assertEquals(
            SymbolKeyboardState(KeyboardMode.ALPHABET, SymbolPage.EXTENDED),
            state.returnToAlphabet()
        )
        assertEquals(Scheme.JYUTPING, alphabetScheme)
        assertEquals(true, alphabetShiftIsLocked)
    }
}
