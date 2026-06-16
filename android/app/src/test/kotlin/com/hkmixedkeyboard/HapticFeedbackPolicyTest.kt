package com.hkmixedkeyboard

import android.view.HapticFeedbackConstants
import com.hkmixedkeyboard.ui.HapticFeedbackPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class HapticFeedbackPolicyTest {

    @Test
    fun `typing view fallback uses keyboard press feedback`() {
        assertEquals(
            HapticFeedbackConstants.KEYBOARD_PRESS,
            HapticFeedbackPolicy.typingConstant()
        )
    }

    @Test
    fun `candidate and panel selections use virtual key feedback`() {
        assertEquals(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackPolicy.selectionConstant()
        )
    }
}
