package com.hkmixedkeyboard.ui

object KeyTouchPolicy {
    // Make most keys emit on DOWN for snappier feel; keep long-press-capable keys special
    fun emitsOnPress(label: String): Boolean =
        label != KeyboardLayout.KEY_BACKSPACE &&
            label != KeyboardLayout.KEY_QUESTION

    fun emitsOnRelease(label: String): Boolean = false
}
