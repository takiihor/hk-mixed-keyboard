package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.KeyTouchPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyTouchPolicyTest {

    @Test
    fun `ordinary keys emit on press and never again on release`() {
        assertTrue(KeyTouchPolicy.emitsOnPress("A"))
        assertFalse(KeyTouchPolicy.emitsOnRelease("A"))
    }

    @Test
    fun `backspace owns its press and repeat behavior`() {
        assertFalse(KeyTouchPolicy.emitsOnPress("⌫"))
        assertFalse(KeyTouchPolicy.emitsOnRelease("⌫"))
    }

    @Test
    fun `question key waits for tap or long press decision`() {
        assertFalse(KeyTouchPolicy.emitsOnPress("？！"))
        assertFalse(KeyTouchPolicy.emitsOnRelease("？！"))
    }
}
