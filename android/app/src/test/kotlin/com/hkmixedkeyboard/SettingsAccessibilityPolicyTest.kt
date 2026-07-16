package com.hkmixedkeyboard

import com.hkmixedkeyboard.settings.SettingsAccessibilityPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsAccessibilityPolicyTest {
    @Test
    fun `theme preview descriptions use the Settings secondary foreground`() {
        assertEquals(0xFF475569.toInt(), SettingsAccessibilityPolicy.previewDescriptionTextColor)
    }

    @Test
    fun `switch content description names the associated setting`() {
        assertEquals(
            "Key vibration",
            SettingsAccessibilityPolicy.switchContentDescription("Key vibration")
        )
    }
}
