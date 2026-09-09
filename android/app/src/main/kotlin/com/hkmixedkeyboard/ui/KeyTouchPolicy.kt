package com.hkmixedkeyboard.ui

object KeyTouchPolicy {
    // Make most keys emit on DOWN for snappier feel; release-only and gesture keys
    // are handled after their tap/hold or drag-out result is known.
    fun emitsOnPress(label: String): Boolean =
        label != KeyboardLayout.KEY_BACKSPACE &&
            label != KeyboardLayout.KEY_QUESTION &&
            label != KeyboardLayout.KEY_PERIOD &&
            label != KeyboardLayout.KEY_MODE

    // The scheme key resolves through its own tap/hold gesture now that a long
    // press toggles 簡體輸出, so it must not also fire on release.
    fun emitsOnRelease(label: String, releasedInside: Boolean): Boolean = false

    fun usesHoldGesture(label: String): Boolean =
            label == KeyboardLayout.KEY_BACKSPACE ||
            label == KeyboardLayout.KEY_SYMBOL ||
            label == KeyboardLayout.KEY_QUESTION ||
            label == KeyboardLayout.KEY_PERIOD ||
            label == KeyboardLayout.KEY_MODE ||
            label == KeyboardLayout.KEY_SPACE
}
