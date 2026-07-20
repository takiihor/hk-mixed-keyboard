package com.hkmixedkeyboard.ui

/** Resolves the small set of main-keyboard actions that differ between tap and hold. */
object MainKeyboardLongPressPolicy {
    fun shortPressTextFor(label: String): String = label

    fun longPressTextFor(label: String): String? = when (label) {
        KeyboardLayout.KEY_PERIOD -> "."
        else -> null
    }
}
