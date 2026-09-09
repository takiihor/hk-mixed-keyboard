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
    private var repeats = 0

    /**
     * Repeats [action] while held, accelerating and then escalating.
     *
     * A flat character-per-60ms repeat means clearing a wrong sentence is a long
     * grind; iOS speeds up and then deletes whole words, which is what makes a
     * held backspace feel like an undo rather than a wait. [onEscalate] is invoked
     * once the hold passes [ESCALATE_AFTER_REPEATS], and takes over from [action].
     */
    fun pressRepeating(action: () -> Unit, onEscalate: (() -> Unit)? = null) {
        cancel()
        repeating = true
        repeats = 0
        action()
        scheduleRepeat(REPEAT_INITIAL_DELAY_MS, action, onEscalate)
    }

    fun pressLong(
        delayMs: Long = LONG_PRESS_DELAY_MS,
        tap: () -> Unit,
        longPress: () -> Unit
    ) {
        cancel()
        tapAction = tap
        longPressFired = false
        pending = scheduler.schedule(delayMs) {
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

    private fun scheduleRepeat(
        delayMs: Long,
        action: () -> Unit,
        onEscalate: (() -> Unit)?
    ) {
        pending = scheduler.schedule(delayMs) {
            if (!repeating) return@schedule
            repeats++
            val escalated = onEscalate != null && repeats >= ESCALATE_AFTER_REPEATS
            if (escalated) onEscalate!!() else action()
            scheduleRepeat(intervalFor(repeats, escalated), action, onEscalate)
        }
    }

    /**
     * Character deletion ramps from 60ms to 25ms over the first stretch of a hold;
     * once it escalates to whole words it slows back down, because a word is a
     * much bigger step and running it at 25ms overshoots before a finger can lift.
     */
    private fun intervalFor(repeats: Int, escalated: Boolean): Long = when {
        escalated -> WORD_REPEAT_INTERVAL_MS
        repeats >= ACCELERATE_AFTER_REPEATS -> FAST_REPEAT_INTERVAL_MS
        else -> REPEAT_INTERVAL_MS
    }

    companion object {
        const val REPEAT_INITIAL_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 60L
        const val FAST_REPEAT_INTERVAL_MS = 25L
        const val WORD_REPEAT_INTERVAL_MS = 120L
        /** Repeats before character deletion speeds up (~0.6s of holding). */
        const val ACCELERATE_AFTER_REPEATS = 10
        /** Repeats before whole-word deletion takes over (~1.1s of holding). */
        const val ESCALATE_AFTER_REPEATS = 30
        const val LONG_PRESS_DELAY_MS = 450L
    }
}
