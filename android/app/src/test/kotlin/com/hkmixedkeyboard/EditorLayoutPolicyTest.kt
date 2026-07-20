package com.hkmixedkeyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.hkmixedkeyboard.ime.EditorImeActions
import com.hkmixedkeyboard.ime.EditorLayoutPolicy
import com.hkmixedkeyboard.ui.KeyboardSurface
import com.hkmixedkeyboard.ui.SymbolEnterAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorLayoutPolicyTest {
    @Test
    fun `maps numeric editors to their compact surfaces`() {
        assertEquals(
            KeyboardSurface.NUMBER,
            EditorLayoutPolicy.surfaceFor(InputType.TYPE_CLASS_NUMBER)
        )
        assertEquals(
            KeyboardSurface.SIGNED_DECIMAL_NUMBER,
            EditorLayoutPolicy.surfaceFor(
                InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_SIGNED or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL
            )
        )
        assertEquals(
            KeyboardSurface.PHONE,
            EditorLayoutPolicy.surfaceFor(InputType.TYPE_CLASS_PHONE)
        )
    }

    @Test
    fun `maps numeric password editors to their own surface`() {
        val numericPassword =
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD

        assertEquals("NUMERIC_PASSWORD", EditorLayoutPolicy.surfaceFor(numericPassword).name)
        assertTrue(EditorLayoutPolicy.usesDirectEntry(numericPassword))
    }

    @Test
    fun `maps email editors to email and URI editors to standard text`() {
        assertEquals(
            KeyboardSurface.EMAIL,
            EditorLayoutPolicy.surfaceFor(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            )
        )
        assertEquals(
            KeyboardSurface.TEXT,
            EditorLayoutPolicy.surfaceFor(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            )
        )
    }

    @Test
    fun `uses text surface for ordinary password and unknown editors`() {
        assertEquals(KeyboardSurface.TEXT, EditorLayoutPolicy.surfaceFor(InputType.TYPE_CLASS_TEXT))
        assertEquals(
            KeyboardSurface.TEXT,
            EditorLayoutPolicy.surfaceFor(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            )
        )
        assertEquals(KeyboardSurface.TEXT, EditorLayoutPolicy.surfaceFor(0x7f000000))
    }

    @Test
    fun `offers a platform next-IME action only when requested`() {
        assertEquals(
            EditorImeActions(SymbolEnterAction.SEND, offerNextInputMethod = true),
            EditorLayoutPolicy.actionsFor(EditorInfo.IME_ACTION_SEND, shouldOfferNextInputMethod = true)
        )
        assertEquals(
            EditorImeActions(SymbolEnterAction.SEND, offerNextInputMethod = false),
            EditorLayoutPolicy.actionsFor(EditorInfo.IME_ACTION_SEND, shouldOfferNextInputMethod = false)
        )
    }
}
