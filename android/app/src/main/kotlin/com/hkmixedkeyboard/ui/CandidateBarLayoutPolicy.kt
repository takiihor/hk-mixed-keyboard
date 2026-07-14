package com.hkmixedkeyboard.ui

/** Shared geometry for the candidate/preview strip and the IME container. */
enum class CandidateBarDisplayState {
    EMPTY,
    CANDIDATES_OR_COMPOSING,
    SYSTEM_MESSAGE
}

/** State transitions shared by CandidateBarView content updates. */
object CandidateBarDisplayStatePolicy {
    fun afterClear(current: CandidateBarDisplayState): CandidateBarDisplayState =
        if (current == CandidateBarDisplayState.SYSTEM_MESSAGE) {
            CandidateBarDisplayState.SYSTEM_MESSAGE
        } else {
            CandidateBarDisplayState.EMPTY
        }
}

object CandidateBarLayoutPolicy {
    const val VISIBLE_HEIGHT_DP = 42f
    const val TEXT_SIZE_SP = 20f
    const val VERTICAL_PADDING_DP = 4
    const val SYSTEM_MESSAGE_MAX_LINES = 1

    fun heightPx(density: Float, state: CandidateBarDisplayState): Int = when (state) {
        CandidateBarDisplayState.EMPTY,
        CandidateBarDisplayState.CANDIDATES_OR_COMPOSING,
        CandidateBarDisplayState.SYSTEM_MESSAGE -> (VISIBLE_HEIGHT_DP * density).toInt()
    }

    fun inputViewMinimumHeightPx(density: Float, state: CandidateBarDisplayState): Int =
        KeyboardLayout.keyboardHeightPx(density) + heightPx(density, state)
}
