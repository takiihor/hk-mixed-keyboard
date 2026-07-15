package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.KeyTouchPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyTouchPolicyTest {

    @Test
    fun `ordinary keys emit on press and never again on release`() {
        assertTrue(KeyTouchPolicy.emitsOnPress("A"))
        assertFalse(KeyTouchPolicy.emitsOnRelease("A", releasedInside = true))
    }

    @Test
    fun `backspace owns its press and repeat behavior`() {
        assertFalse(KeyTouchPolicy.emitsOnPress("⌫"))
        assertFalse(KeyTouchPolicy.emitsOnRelease("⌫", releasedInside = true))
    }

    @Test
    fun `question key waits for tap or long press decision`() {
        assertFalse(KeyTouchPolicy.emitsOnPress("？！"))
        assertFalse(KeyTouchPolicy.emitsOnRelease("？！", releasedInside = true))
    }

    @Test
    fun `period key waits for short or long press decision`() {
        assertFalse(KeyTouchPolicy.emitsOnPress("。"))
        assertTrue(KeyTouchPolicy.usesHoldGesture("。"))
    }

    @Test
    fun `mode key waits for tap or long-press direct picker`() {
        assertTrue(KeyTouchPolicy.usesHoldGesture("⌨"))
        assertFalse(KeyTouchPolicy.emitsOnPress("⌨"))
        assertFalse(KeyTouchPolicy.emitsOnRelease("⌨", releasedInside = true))
        assertFalse(KeyTouchPolicy.emitsOnRelease("⌨", releasedInside = false))
    }

    @Test
    fun `symbol key keeps its tap and long press gesture`() {
        assertTrue(KeyTouchPolicy.usesHoldGesture("符"))
    }
}
