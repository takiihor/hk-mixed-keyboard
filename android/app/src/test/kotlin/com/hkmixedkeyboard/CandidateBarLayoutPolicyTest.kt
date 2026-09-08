package com.hkmixedkeyboard

import com.hkmixedkeyboard.settings.KeyboardTheme
import com.hkmixedkeyboard.ui.CandidateBarDisplayState
import com.hkmixedkeyboard.ui.CandidateBarLayoutPolicy
import com.hkmixedkeyboard.ui.KeyboardLayout
import com.hkmixedkeyboard.ui.toColors
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateBarLayoutPolicyTest {

    @Test
    fun `empty candidates and system messages all keep fixed height`() {
        val density = 2f

        listOf(false to 42f, true to 58f).forEach { (showsHint, heightDp) ->
            val expected = (heightDp * density).toInt()
            CandidateBarDisplayState.entries.forEach { state ->
                assertEquals(expected, CandidateBarLayoutPolicy.heightPx(density, state, showsHint))
                assertEquals(
                    KeyboardLayout.keyboardHeightPx(density) + expected,
                    CandidateBarLayoutPolicy.inputViewMinimumHeightPx(density, state, showsHint)
                )
            }
        }
    }

    @Test
    fun `candidate bar dimensions are theme independent`() {
        val density = 2f
        val dark = KeyboardTheme.DARK.toColors()
        val light = KeyboardTheme.IOS_LIGHT.toColors()

        assertEquals(
            CandidateBarLayoutPolicy.heightPx(density, CandidateBarDisplayState.CANDIDATES_OR_COMPOSING, false),
            CandidateBarLayoutPolicy.heightPx(density, CandidateBarDisplayState.CANDIDATES_OR_COMPOSING, false)
        )
        assertEquals(0xFF202124.toInt(), dark.candidateBackground)
        assertEquals(0xFFD1D5DB.toInt(), light.candidateBackground)
    }

    @Test
    fun `candidate strip reserves a compact reading hint line only when enabled`() {
        assertEquals(21f, CandidateBarLayoutPolicy.TEXT_SIZE_SP)
        assertEquals(13f, CandidateBarLayoutPolicy.PREVIEW_TEXT_SIZE_SP)
        assertEquals(42f, CandidateBarLayoutPolicy.VISIBLE_HEIGHT_DP)
        assertEquals(16f, CandidateBarLayoutPolicy.READING_HINT_HEIGHT_DP)
        assertEquals(42f, CandidateBarLayoutPolicy.visibleHeightDp(showsReadingHint = false))
        assertEquals(58f, CandidateBarLayoutPolicy.visibleHeightDp(showsReadingHint = true))
        assertEquals(1, CandidateBarLayoutPolicy.SYSTEM_MESSAGE_MAX_LINES)
    }

    @Test
    fun `loading to candidates and candidates to empty never change height`() {
        val density = 2.625f

        listOf(false, true).forEach { showsHint ->
            val heights = listOf(
                CandidateBarDisplayState.SYSTEM_MESSAGE,
                CandidateBarDisplayState.CANDIDATES_OR_COMPOSING,
                CandidateBarDisplayState.EMPTY
            ).map { CandidateBarLayoutPolicy.heightPx(density, it, showsHint) }

            assertEquals(1, heights.distinct().size)
        }
    }
}
