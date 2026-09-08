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
    /** Extra strip height reserved for the single-line reading hint. */
    const val READING_HINT_HEIGHT_DP = 16f
    const val TEXT_SIZE_SP = 21f
    const val PREVIEW_TEXT_SIZE_SP = 13f
    const val VERTICAL_PADDING_DP = 4
    const val SYSTEM_MESSAGE_MAX_LINES = 1

    /**
     * The strip keeps one fixed height for every display state, so the IME window
     * never jumps while typing. It grows only when the learner turns a reading
     * hint on — a settings change, never a keystroke.
     */
    fun visibleHeightDp(showsReadingHint: Boolean): Float =
        if (showsReadingHint) VISIBLE_HEIGHT_DP + READING_HINT_HEIGHT_DP else VISIBLE_HEIGHT_DP

    fun heightPx(
        density: Float,
        state: CandidateBarDisplayState,
        showsReadingHint: Boolean
    ): Int = when (state) {
        CandidateBarDisplayState.EMPTY,
        CandidateBarDisplayState.CANDIDATES_OR_COMPOSING,
        CandidateBarDisplayState.SYSTEM_MESSAGE -> (visibleHeightDp(showsReadingHint) * density).toInt()
    }

    fun inputViewMinimumHeightPx(
        density: Float,
        state: CandidateBarDisplayState,
        showsReadingHint: Boolean
    ): Int = KeyboardLayout.keyboardHeightPx(density) + heightPx(density, state, showsReadingHint)
}
