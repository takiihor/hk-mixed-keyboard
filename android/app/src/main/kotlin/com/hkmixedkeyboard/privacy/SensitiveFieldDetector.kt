package com.hkmixedkeyboard.privacy

import android.text.InputType
import android.view.inputmethod.EditorInfo

object SensitiveFieldDetector {

    fun isSensitive(info: EditorInfo): Boolean {
        val privateHints = info.privateImeOptions.orEmpty().lowercase()
        if (SENSITIVE_PRIVATE_HINTS.any(privateHints::contains)) return true
        val type = info.inputType
        val base = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION

        if (base == InputType.TYPE_CLASS_TEXT) {
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) return true

            // NOTE: TYPE_TEXT_FLAG_NO_SUGGESTIONS is intentionally NOT treated as
            // sensitive. Many note/editor apps set this flag on ordinary body
            // fields just to disable autocorrect; treating it as a password field
            // forces safe mode, which hides all candidates and makes Cangjie/Quick
            // composition impossible to commit (looks like "keyboard produces no
            // output"). Only genuine password input types enter safe mode.
        }

        if (base == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return true

        return false
    }

    private val SENSITIVE_PRIVATE_HINTS = listOf(
        "creditcard", "credit_card", "payment", "bank", "cvv", "cvc", "otp", "one_time_code"
    )
}
