package com.hkmixedkeyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.hkmixedkeyboard.privacy.SensitiveFieldDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveFieldDetectorTest {

    @Test
    fun `password variations enter safe mode`() {
        assertTrue(SensitiveFieldDetector.isSensitive(editorInfo(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)))
        assertTrue(SensitiveFieldDetector.isSensitive(editorInfo(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)))
        assertTrue(SensitiveFieldDetector.isSensitive(editorInfo(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)))
        assertTrue(SensitiveFieldDetector.isSensitive(editorInfo(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)))
    }

    @Test
    fun `no suggestions flag remains an ordinary composition field`() {
        assertFalse(
            SensitiveFieldDetector.isSensitive(
                editorInfo(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            )
        )
    }

    @Test
    fun `payment and one-time-code private hints enter safe mode`() {
        assertTrue(SensitiveFieldDetector.isSensitive(editorInfo(InputType.TYPE_CLASS_NUMBER).apply {
            privateImeOptions = "payment.creditCard"
        }))
        assertTrue(SensitiveFieldDetector.isSensitive(editorInfo(InputType.TYPE_CLASS_NUMBER).apply {
            privateImeOptions = "one_time_code"
        }))
    }

    private fun editorInfo(inputType: Int) = EditorInfo().apply {
        this.inputType = inputType
    }
}
