package com.hkmixedkeyboard.ui

class HoldActionController(private val scheduler: Scheduler) {

    fun interface Cancellable {
        fun cancel()
    }

    fun interface Scheduler {
        fun schedule(delayMs: Long, action: () -> Unit): Cancellable
    }

    private var pending: Cancellable? = null
    private var tapAction: (() -> Unit)? = null
    private var longPressFired = false
    private var repeating = false

    fun pressRepeating(action: () -> Unit) {
        cancel()
        repeating = true
        action()
        scheduleRepeat(REPEAT_INITIAL_DELAY_MS, action)
    }

    fun pressLong(tap: () -> Unit, longPress: () -> Unit) {
        cancel()
        tapAction = tap
        longPressFired = false
        pending = scheduler.schedule(LONG_PRESS_DELAY_MS) {
            pending = null
            longPressFired = true
            longPress()
        }
    }

    fun release() {
        val tap = tapAction
        pending?.cancel()
        pending = null
        repeating = false
        tapAction = null
        if (tap != null && !longPressFired) tap()
        longPressFired = false
    }

    fun cancel() {
        pending?.cancel()
        pending = null
        tapAction = null
        longPressFired = false
        repeating = false
    }

    private fun scheduleRepeat(delayMs: Long, action: () -> Unit) {
        pending = scheduler.schedule(delayMs) {
            if (!repeating) return@schedule
            action()
            scheduleRepeat(REPEAT_INTERVAL_MS, action)
        }
    }

    companion object {
        const val REPEAT_INITIAL_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 60L
        const val LONG_PRESS_DELAY_MS = 450L
    }
}
