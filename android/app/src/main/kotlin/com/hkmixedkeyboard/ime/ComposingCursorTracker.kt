package com.hkmixedkeyboard.ime

/**
 * Mirrors where the caret ends up after each edit this IME performs, so
 * [SelectionChangePolicy] can tell our own updates apart from the user moving the
 * caret or selecting a range.
 *
 * Every write we make (`commitText`/`setComposingText` with newCursorPosition = 1)
 * leaves a collapsed caret immediately after the text written, and both calls
 * REPLACE the active composing region rather than inserting at the caret — which is
 * why the region's length has to be tracked alongside the caret.
 *
 * The editor confirms our edits asynchronously, so a report can describe a position
 * we have already moved past: type three letters quickly and the callback for the
 * first can arrive after the mirror reached the third. Every position we produce is
 * therefore held in [pending] until the editor confirms it, and [confirm] accepts
 * any of them. Comparing against the latest position alone would read those belated
 * reports as the user moving the caret and drop the composition mid-word.
 *
 * [UNKNOWN] means we have no trustworthy mirror: the editor never reported an
 * initial selection, or we sent a raw key event whose effect we cannot predict.
 */
class ComposingCursorTracker {

    var cursor: Int = UNKNOWN
        private set

    /** Length of the composing region we last handed to the editor. */
    var composingLength: Int = 0
        private set

    /** True once we have a caret position worth comparing against. */
    val hasMirror: Boolean get() = cursor != UNKNOWN

    // Positions we have produced but the editor has not confirmed yet, oldest first.
    private val pending = ArrayDeque<Int>()

    /** Re-sync to a caret position the editor just reported. */
    fun syncTo(selStart: Int, selEnd: Int) {
        cursor = if (selStart >= 0 && selStart == selEnd) selStart else UNKNOWN
        composingLength = 0
        pending.clear()
    }

    /** Give up the mirror until the editor reports a position again. */
    fun invalidate() {
        cursor = UNKNOWN
        composingLength = 0
        pending.clear()
    }

    /** `finishComposingText()`: the region becomes ordinary text, the caret stays. */
    fun onFinishComposing() {
        composingLength = 0
    }

    /** `deleteSurroundingText(count, 0)`, with no composing region active. */
    fun onDeleteBefore(count: Int) {
        if (cursor != UNKNOWN) cursor = (cursor - count).coerceAtLeast(0)
        record()
    }

    /** `commitText(text, 1)`: replaces any composing region, caret lands after it. */
    fun onCommit(length: Int) {
        if (cursor != UNKNOWN) cursor = (cursor - composingLength + length).coerceAtLeast(0)
        composingLength = 0
        record()
    }

    /** `setComposingText(text, 1)`: replaces any composing region, caret after it. */
    fun onCompose(length: Int) {
        if (cursor != UNKNOWN) cursor = (cursor - composingLength + length).coerceAtLeast(0)
        composingLength = length
        record()
    }

    /**
     * Is [position] somewhere we put the caret ourselves? Consumes that position and
     * any older ones still outstanding, so a report only ever confirms once.
     */
    fun confirm(position: Int): Boolean {
        if (cursor == UNKNOWN) return false
        val index = pending.indexOf(position)
        if (index >= 0) {
            repeat(index + 1) { pending.removeFirst() }
            return true
        }
        // Settled state: nothing outstanding and the editor agrees with the mirror.
        return position == cursor
    }

    private fun record() {
        if (cursor == UNKNOWN) return
        pending.addLast(cursor)
        while (pending.size > MAX_PENDING) pending.removeFirst()
    }

    companion object {
        const val UNKNOWN = -1

        // Bounds the queue if an editor never reports back. Far more than the few
        // edits that can be in flight while the user is typing.
        private const val MAX_PENDING = 16
    }
}
