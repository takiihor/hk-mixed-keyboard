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
                supportsPrimitiveTick = true,
                supportsPredefinedTick = true
            )
        )
    }

    @Test
    fun `supported primitive selects primitive tick`() {
        assertEquals(
            TypingHapticStrategy.PRIMITIVE_TICK,
            HapticCapabilityPolicy.select(
                enabled = true,
                hasVibrator = true,
                supportsPrimitiveTick = true,
                supportsPredefinedTick = true
            )
        )
    }

    @Test
    fun `predefined tick is selected without primitive support`() {
        assertEquals(
            TypingHapticStrategy.PREDEFINED_TICK,
            HapticCapabilityPolicy.select(
                enabled = true,
                hasVibrator = true,
                supportsPrimitiveTick = false,
                supportsPredefinedTick = true
            )
        )
    }

    @Test
    fun `view fallback is selected without a direct tick path`() {
        assertEquals(
            TypingHapticStrategy.VIEW_FALLBACK,
            HapticCapabilityPolicy.select(
                enabled = true,
                hasVibrator = true,
                supportsPrimitiveTick = false,
                supportsPredefinedTick = false
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
                supportsPrimitiveTick = false,
                supportsPredefinedTick = false
            )
        )
    }
}
