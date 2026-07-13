package com.hkmixedkeyboard

import com.hkmixedkeyboard.settings.KeyboardPrefs
import org.junit.Assert.assertFalse
import org.junit.Test

class KeyboardPrefsTest {

    @Test
    fun `simplified output defaults off`() {
        assertFalse(KeyboardPrefs().simplifiedOutput)
    }
}
