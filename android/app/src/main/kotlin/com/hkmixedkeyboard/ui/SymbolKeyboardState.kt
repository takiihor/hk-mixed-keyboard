package com.hkmixedkeyboard.ui

enum class KeyboardMode {
    ALPHABET,
    SYMBOLS
}

/** Symbol state intentionally does not own alphabet scheme or Shift/Caps state. */
data class SymbolKeyboardState(
    val keyboardMode: KeyboardMode = KeyboardMode.ALPHABET,
    val symbolPage: SymbolPage = SymbolPage.COMMON
) {
    fun enterSymbols(): SymbolKeyboardState = copy(
        keyboardMode = KeyboardMode.SYMBOLS,
        symbolPage = SymbolPage.COMMON
    )

    fun toggleSymbolPage(): SymbolKeyboardState = copy(symbolPage = symbolPage.toggled())

    fun returnToAlphabet(): SymbolKeyboardState = copy(keyboardMode = KeyboardMode.ALPHABET)
}
