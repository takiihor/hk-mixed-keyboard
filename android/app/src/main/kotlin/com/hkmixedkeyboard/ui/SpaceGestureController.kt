package com.hkmixedkeyboard.ui

class SpaceGestureController(
    private val holdController: HoldActionController,
    private val cursorStepPx: Float,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onSwipe: (Int) -> Unit
) {
    private var startX = 0f
    private var lastStep = 0
    private var swiped = false
    private var longPressFired = false
    private var cancelled = false

    fun press(startX: Float) {
        this.startX = startX
        lastStep = 0
        swiped = false
        longPressFired = false
        cancelled = false
        holdController.pressLong(
            delayMs = LONG_PRESS_DELAY_MS,
            tap = onTap,
            longPress = {
                longPressFired = true
                onLongPress()
            }
        )
    }

    fun move(x: Float, holdEligible: Boolean) {
        if (longPressFired || cancelled) return
        if (!holdEligible) {
            holdController.cancel()
            cancelled = true
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
        val direction = if (delta > 0) 1 else -1
        repeat(kotlin.math.abs(delta)) { onSwipe(direction) }
    }

    fun release(releasedInside: Boolean) {
        if (releasedInside && !swiped && !cancelled) holdController.release()
        else holdController.cancel()
        reset()
    }

    fun cancel() {
        holdController.cancel()
        reset()
    }

    private fun reset() {
        lastStep = 0
        swiped = false
        longPressFired = false
        cancelled = false
    }

    companion object {
        const val LONG_PRESS_DELAY_MS = 600L
    }
}
