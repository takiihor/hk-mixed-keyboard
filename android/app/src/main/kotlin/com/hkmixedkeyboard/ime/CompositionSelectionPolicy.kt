package com.hkmixedkeyboard.ime


/** Keeps a composing session from writing into an editor position the user left. */
object CompositionSelectionPolicy {
    fun shouldCancel(
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ): Boolean {
        if (candidatesStart < 0 || candidatesEnd < candidatesStart) return false
        return newSelStart != candidatesEnd || newSelEnd != candidatesEnd
    }
}
