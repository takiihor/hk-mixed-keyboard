package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.HapticCapabilityPolicy
import com.hkmixedkeyboard.ui.TypingHapticStrategy
import org.junit.Assert.assertEquals
import org.junit.Test

class HapticCapabilityPolicyTest {

    @Test
    fun `disabled feedback selects no haptic strategy`() {
        assertEquals(
            TypingHapticStrategy.NONE,
            HapticCapabilityPolicy.select(
                enabled = false,
                hasVibrator = true,
                supportsPrimitiveClick = true,
                supportsPredefinedClick = true
            )
        )
    }

    @Test
    fun `supported primitive selects primitive click`() {
        assertEquals(
            TypingHapticStrategy.PRIMITIVE_CLICK,
            HapticCapabilityPolicy.select(
                enabled = true,
                hasVibrator = true,
                supportsPrimitiveClick = true,
                supportsPredefinedClick = true
            )
        )
    }

    @Test
    fun `predefined click is selected without primitive support`() {
        assertEquals(
            TypingHapticStrategy.PREDEFINED_CLICK,
            HapticCapabilityPolicy.select(
                enabled = true,
                hasVibrator = true,
                supportsPrimitiveClick = false,
                supportsPredefinedClick = true
            )
        )
    }

    @Test
    fun `view fallback is selected without a direct click path`() {
        assertEquals(
            TypingHapticStrategy.VIEW_FALLBACK,
            HapticCapabilityPolicy.select(
                enabled = true,
                hasVibrator = true,
                supportsPrimitiveClick = false,
                supportsPredefinedClick = false
            )
        )
    }

    @Test
    fun `view fallback is selected when device has no vibrator`() {
        assertEquals(
            TypingHapticStrategy.VIEW_FALLBACK,
            HapticCapabilityPolicy.select(
                enabled = true,
                hasVibrator = false,
                supportsPrimitiveClick = false,
                supportsPredefinedClick = false
            )
        )
    }
}
