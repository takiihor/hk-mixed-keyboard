package com.hkmixedkeyboard

import com.hkmixedkeyboard.settings.ChangeTokenTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeTokenTrackerTest {

    @Test
    fun `first token observation is baseline only`() {
        val tracker = ChangeTokenTracker()

        assertFalse(tracker.hasChanged(7L))
    }

    @Test
    fun `unchanged token does not trigger change`() {
        val tracker = ChangeTokenTracker()

        tracker.hasChanged(7L)

        assertFalse(tracker.hasChanged(7L))
    }

    @Test
    fun `new token triggers change once`() {
        val tracker = ChangeTokenTracker()

        tracker.hasChanged(7L)

        assertTrue(tracker.hasChanged(8L))
        assertFalse(tracker.hasChanged(8L))
    }
}
