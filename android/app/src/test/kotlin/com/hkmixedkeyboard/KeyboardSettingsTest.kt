package com.hkmixedkeyboard

import com.hkmixedkeyboard.settings.KeyboardPrefs
import org.junit.Assert.assertFalse
import org.junit.Test

class KeyboardSettingsTest {

    @Test
    fun `Jyutping candidate readings are hidden by default`() {
        assertFalse(KeyboardPrefs().showJyutpingCandidateReadings)
    }
}
