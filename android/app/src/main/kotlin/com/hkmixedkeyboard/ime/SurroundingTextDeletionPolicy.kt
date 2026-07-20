package com.hkmixedkeyboard.ime

import com.hkmixedkeyboard.commit.DeletionRequest
import com.hkmixedkeyboard.commit.DeletionUnit

/** Converts a code-point request to the UTF-16 count required by legacy editors. */
object SurroundingTextDeletionPolicy {
    fun utf16UnitsForFallback(
        request: DeletionRequest,
        textBeforeCursor: CharSequence?
    ): Int = when (request.unit) {
        DeletionUnit.UTF16_UNITS -> request.count
        DeletionUnit.CODE_POINTS -> {
            val text = textBeforeCursor
            if (request.count != 1 || text.isNullOrEmpty()) 1
            else if (
                Character.isLowSurrogate(text.last()) &&
                text.length >= 2 &&
                Character.isHighSurrogate(text[text.length - 2])
            ) 2
            else 1
        }
    }
}
