package com.hkmixedkeyboard.ui

import android.view.HapticFeedbackConstants
import android.view.View

object HapticFeedbackPolicy {
    @android.annotation.SuppressLint("InlinedApi")
    fun typingConstant(): Int = HapticFeedbackConstants.KEYBOARD_PRESS

    fun selectionConstant(): Int = HapticFeedbackConstants.VIRTUAL_KEY

    @Suppress("DEPRECATION")
    fun performTyping(view: View, enabled: Boolean) {
        perform(view, enabled, typingConstant())
    }

    @Suppress("DEPRECATION")
    fun performSelection(view: View, enabled: Boolean) {
        perform(view, enabled, selectionConstant())
    }

    @Suppress("DEPRECATION")
    private fun perform(view: View, enabled: Boolean, constant: Int) {
        if (!enabled) return
        view.performHapticFeedback(constant)
    }
}
