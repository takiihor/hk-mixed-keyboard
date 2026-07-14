package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.CandidateBarDisplayState
import com.hkmixedkeyboard.ui.CandidateBarDisplayStatePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateBarDisplayStatePolicyTest {

    @Test
    fun `clear empties candidates but preserves a system message`() {
        assertEquals(
            CandidateBarDisplayState.EMPTY,
            CandidateBarDisplayStatePolicy.afterClear(CandidateBarDisplayState.CANDIDATES_OR_COMPOSING)
        )
        assertEquals(
            CandidateBarDisplayState.SYSTEM_MESSAGE,
            CandidateBarDisplayStatePolicy.afterClear(CandidateBarDisplayState.SYSTEM_MESSAGE)
        )
    }
}
