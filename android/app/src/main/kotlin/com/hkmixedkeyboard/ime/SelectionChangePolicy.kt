package com.hkmixedkeyboard.ime

/**
 * Separates a caret move the user made from the one our own edit just caused.
 *
 * This matters because a composing region stays anchored where it was created. Once
 * the user taps elsewhere the region is still registered at the old offsets, so the
 * next `setComposingText()` replaces THAT text and drags the caret back to the end
 * of it — the word appears to "pull" the cursor back. Spotting the move lets the
 * service finalize the composition where it sits and start a fresh one at the caret.
 */
object SelectionChangePolicy {

    /**
     * @param matchesOurEdit the reported caret is a position we produced
     *   (see [ComposingCursorTracker.confirm]); meaningless unless [haveMirror]
     * @param haveMirror we are tracking the caret at all
     * @param candidatesStart composing-region bounds as reported by the editor, or -1
     */
    fun isExternalMove(
        composingBuffer: String,
        matchesOurEdit: Boolean,
        haveMirror: Boolean,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ): Boolean {
        // Nothing composing: wherever the caret went, the next keystroke starts a
        // fresh composition there anyway.
        if (composingBuffer.isEmpty()) return false

        // Our edits always leave a collapsed caret, so a range is the user selecting.
        if (newSelStart != newSelEnd) return true

        // Primary signal: our own mirror, which does not depend on the editor
        // reporting composing spans.
        if (haveMirror) return !matchesOurEdit

        // Fallback for editors that do report the region: our updates leave the
        // caret exactly at its end.
        if (candidatesStart >= 0 && candidatesEnd >= 0) return newSelStart != candidatesEnd

        // Neither signal is available. Guessing "external" here would drop the
        // composition on every keystroke in such an editor — far worse than the
        // caret bug this policy exists to fix — so treat it as our own edit.
        return false
    }
}
