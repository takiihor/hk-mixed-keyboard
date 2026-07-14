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
        val expected = (42f * density).toInt()

        CandidateBarDisplayState.entries.forEach { state ->
            assertEquals(expected, CandidateBarLayoutPolicy.heightPx(density, state))
            assertEquals(
                KeyboardLayout.keyboardHeightPx(density) + expected,
                CandidateBarLayoutPolicy.inputViewMinimumHeightPx(density, state)
            )
        }
    }

    @Test
    fun `candidate bar dimensions are theme independent`() {
        val density = 2f
        val dark = KeyboardTheme.DARK.toColors()
        val light = KeyboardTheme.IOS_LIGHT.toColors()

        assertEquals(
            CandidateBarLayoutPolicy.heightPx(density, CandidateBarDisplayState.CANDIDATES_OR_COMPOSING),
            CandidateBarLayoutPolicy.heightPx(density, CandidateBarDisplayState.CANDIDATES_OR_COMPOSING)
        )
        assertEquals(0xFF202124.toInt(), dark.candidateBackground)
        assertEquals(0xFFD1D5DB.toInt(), light.candidateBackground)
    }

    @Test
    fun `candidate typography is comfortably sized and single line`() {
        assertEquals(21f, CandidateBarLayoutPolicy.TEXT_SIZE_SP)
        assertEquals(42f, CandidateBarLayoutPolicy.VISIBLE_HEIGHT_DP)
        assertEquals(1, CandidateBarLayoutPolicy.SYSTEM_MESSAGE_MAX_LINES)
    }

    @Test
    fun `loading to candidates and candidates to empty never change height`() {
        val density = 2.625f
        val heights = listOf(
            CandidateBarDisplayState.SYSTEM_MESSAGE,
            CandidateBarDisplayState.CANDIDATES_OR_COMPOSING,
            CandidateBarDisplayState.EMPTY
        ).map { CandidateBarLayoutPolicy.heightPx(density, it) }

        assertEquals(1, heights.distinct().size)
    }
}
