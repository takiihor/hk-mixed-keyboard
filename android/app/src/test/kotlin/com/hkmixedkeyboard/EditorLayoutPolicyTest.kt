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
    fun `numeric password takes precedence over signed decimal flags`() {
        val numericPasswordWithFlags =
            InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_VARIATION_PASSWORD or
                InputType.TYPE_NUMBER_FLAG_SIGNED or
                InputType.TYPE_NUMBER_FLAG_DECIMAL

        assertEquals(
            KeyboardSurface.NUMERIC_PASSWORD,
            EditorLayoutPolicy.surfaceFor(numericPasswordWithFlags)
        )
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
    fun `maps genuine text password editors to their own surface`() {
        val passwordVariations = listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )

        passwordVariations.forEach { variation ->
            assertEquals(
                KeyboardSurface.TEXT_PASSWORD,
                EditorLayoutPolicy.surfaceFor(InputType.TYPE_CLASS_TEXT or variation)
            )
        }
    }

    @Test
    fun `uses text surface for ordinary and unknown text editors`() {
        assertEquals(KeyboardSurface.TEXT, EditorLayoutPolicy.surfaceFor(InputType.TYPE_CLASS_TEXT))
        assertEquals(
            KeyboardSurface.TEXT,
            EditorLayoutPolicy.surfaceFor(InputType.TYPE_CLASS_TEXT or 0x00000ff0)
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
