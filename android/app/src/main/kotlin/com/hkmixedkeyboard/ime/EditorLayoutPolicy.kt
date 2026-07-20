package com.hkmixedkeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.hkmixedkeyboard.ui.KeyboardSurface
import com.hkmixedkeyboard.ui.SymbolEnterAction

data class EditorImeActions(
    val enterAction: SymbolEnterAction,
    val offerNextInputMethod: Boolean
)

/** Selects a compact keyboard surface from the host editor without changing composition policy. */
object EditorLayoutPolicy {
    fun surfaceFor(inputType: Int): KeyboardSurface {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when {
            inputClass == InputType.TYPE_CLASS_NUMBER &&
                inputType and (InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0 ->
                KeyboardSurface.SIGNED_DECIMAL_NUMBER
            inputClass == InputType.TYPE_CLASS_NUMBER -> KeyboardSurface.NUMBER
            inputClass == InputType.TYPE_CLASS_PHONE -> KeyboardSurface.PHONE
            inputClass == InputType.TYPE_CLASS_TEXT &&
                variation in setOf(
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                ) -> KeyboardSurface.EMAIL
            else -> KeyboardSurface.TEXT
        }
    }

    fun actionsFor(imeOptions: Int, shouldOfferNextInputMethod: Boolean): EditorImeActions =
        EditorImeActions(
            enterAction = SymbolEnterAction.fromImeOptions(imeOptions),
            offerNextInputMethod = shouldOfferNextInputMethod
        )

    fun usesDirectEntry(inputType: Int): Boolean =
        surfaceFor(inputType) in setOf(
            KeyboardSurface.NUMBER,
            KeyboardSurface.SIGNED_DECIMAL_NUMBER,
            KeyboardSurface.PHONE,
            KeyboardSurface.EMAIL
        )
}
