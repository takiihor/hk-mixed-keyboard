package com.hkmixedkeyboard.ui

/**
 * Space-bar gestures.
 *
 * Hold-then-drag is the iOS trackpad gesture, and it is the most reflexive thing
 * an iPhone user does on a keyboard: press space, hold, slide to place the caret.
 * That hold used to toggle 簡體輸出 here, so the reflex silently changed which
 * script the keyboard emitted — the worst available accident on a keyboard whose
 * point is preserving 繁體. Holding now enters cursor mode instead; 簡體 moved to
 * a long press on the scheme key.
 *
 * Swiping without holding still moves the caret, because Android users already
 * rely on it and it costs nothing to keep.
 */
class SpaceGestureController(
    private val holdController: HoldActionController,
    private val cursorStepPx: Float,
    private val onTap: () -> Unit,
    private val onSwipe: (Int) -> Unit,
    /** Cursor mode opened or closed, so the renderer can show the state. */
    private val onCursorModeChanged: (Boolean) -> Unit = {}
) {
    private var startX = 0f
    private var lastStep = 0
    private var swiped = false
    private var cursorMode = false
    private var cancelled = false

    fun press(startX: Float) {
        this.startX = startX
        lastStep = 0
        swiped = false
        cursorMode = false
        cancelled = false
        holdController.pressLong(
            delayMs = LONG_PRESS_DELAY_MS,
            tap = onTap,
            longPress = {
                // The finger has not moved yet, so measure the drag from where it
                // rests now rather than from the original touch-down.
                cursorMode = true
                lastStep = 0
                onCursorModeChanged(true)
            }
        )
    }

    fun move(x: Float, holdEligible: Boolean) {
        if (cancelled) return
        // Once cursor mode is open the finger owns the caret, so leaving the key
        // vertically must not cancel it — that is exactly how iOS behaves.
        if (!holdEligible && !cursorMode) {
            holdController.cancel()
            cancelled = true
            return
        }
        if (cursorMode) {
            startX = stepAndReturnOrigin(x, startX)
            return
        }
        val currentStep = ((x - startX) / cursorStepPx).toInt()
        val delta = currentStep - lastStep
        if (delta == 0) return

        if (!swiped) {
            swiped = true
            holdController.cancel()
        }
        lastStep = currentStep
        emit(delta)
    }

    fun release(releasedInside: Boolean) {
        if (releasedInside && !swiped && !cursorMode && !cancelled) holdController.release()
        else holdController.cancel()
        finish()
    }

    fun cancel() {
        holdController.cancel()
        finish()
    }

    /**
     * Emits one caret step per [cursorStepPx] travelled and re-anchors, so a slow
     * drag keeps producing steps instead of stalling once the finger passes the
     * key's edge.
     */
    private fun stepAndReturnOrigin(x: Float, origin: Float): Float {
        val steps = ((x - origin) / cursorStepPx).toInt()
        if (steps == 0) return origin
        emit(steps)
        return origin + steps * cursorStepPx
    }

    private fun emit(delta: Int) {
        val direction = if (delta > 0) 1 else -1
        repeat(kotlin.math.abs(delta)) { onSwipe(direction) }
    }

    private fun finish() {
        if (cursorMode) onCursorModeChanged(false)
        lastStep = 0
        swiped = false
        cursorMode = false
        cancelled = false
    }

    companion object {
        // iOS opens its trackpad at roughly half a second. 600ms read as broken
        // to a tester who did not know the gesture was there at all.
        const val LONG_PRESS_DELAY_MS = 500L
    }
}
