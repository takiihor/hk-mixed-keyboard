package com.hkmixedkeyboard.ui

enum class ShiftState {
    OFF,
    ONCE,
    LOCKED
}

class ShiftStateController {
    var state: ShiftState = ShiftState.OFF
        private set

    private var lastShiftPressMs: Long? = null

    fun press(atMs: Long): ShiftState {
        state = when (state) {
            ShiftState.OFF -> ShiftState.ONCE
            ShiftState.ONCE -> {
                val elapsedMs = lastShiftPressMs?.let { atMs - it }
                if (elapsedMs != null && elapsedMs in 0..DOUBLE_TAP_WINDOW_MS) {
                    ShiftState.LOCKED
                } else {
                    ShiftState.OFF
                }
            }
            ShiftState.LOCKED -> ShiftState.OFF
        }
        lastShiftPressMs = if (state == ShiftState.ONCE) atMs else null
        return state
    }

    fun consumeLetter(): ShiftState {
        if (state == ShiftState.ONCE) {
            state = ShiftState.OFF
            lastShiftPressMs = null
        }
        return state
    }

    fun reset() {
        state = ShiftState.OFF
        lastShiftPressMs = null
    }

    private companion object {
        const val DOUBLE_TAP_WINDOW_MS = 300L
    }
}
