package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.ShiftState
import com.hkmixedkeyboard.ui.ShiftStateController
import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftStateControllerTest {

    @Test
    fun `shift starts off and first press enables one letter`() {
        val controller = ShiftStateController()

        assertEquals(ShiftState.OFF, controller.state)
        assertEquals(ShiftState.ONCE, controller.press(atMs = 1_000))
    }

    @Test
    fun `second press at double tap boundary locks shift`() {
        val controller = ShiftStateController()

        controller.press(atMs = 1_000)

        assertEquals(ShiftState.LOCKED, controller.press(atMs = 1_300))
    }

    @Test
    fun `second press outside double tap window turns shift off`() {
        val controller = ShiftStateController()

        controller.press(atMs = 1_000)

        assertEquals(ShiftState.OFF, controller.press(atMs = 1_301))
    }

    @Test
    fun `pressing locked shift turns it off`() {
        val controller = ShiftStateController()
        controller.press(atMs = 1_000)
        controller.press(atMs = 1_200)

        assertEquals(ShiftState.OFF, controller.press(atMs = 1_300))
    }

    @Test
    fun `letter consumes one-time shift`() {
        val controller = ShiftStateController()
        controller.press(atMs = 1_000)

        assertEquals(ShiftState.OFF, controller.consumeLetter())
    }

    @Test
    fun `letter preserves off shift`() {
        val controller = ShiftStateController()

        assertEquals(ShiftState.OFF, controller.consumeLetter())
    }

    @Test
    fun `letter preserves locked shift`() {
        val controller = ShiftStateController()
        controller.press(atMs = 1_000)
        controller.press(atMs = 1_200)

        assertEquals(ShiftState.LOCKED, controller.consumeLetter())
    }

    @Test
    fun `non-letter key leaves one-time shift enabled`() {
        val controller = ShiftStateController()
        controller.press(atMs = 1_000)

        assertEquals(ShiftState.ONCE, controller.state)
    }

    @Test
    fun `reset turns shift off and clears double tap tracking`() {
        val controller = ShiftStateController()
        controller.press(atMs = 1_000)

        controller.reset()

        assertEquals(ShiftState.OFF, controller.state)
        assertEquals(ShiftState.ONCE, controller.press(atMs = 1_100))
    }

    @Test
    fun `new shift sequence does not lock from an old press timestamp`() {
        val controller = ShiftStateController()
        controller.press(atMs = 1_000)
        controller.consumeLetter()

        assertEquals(ShiftState.ONCE, controller.press(atMs = 1_100))
        assertEquals(ShiftState.LOCKED, controller.press(atMs = 1_200))
    }
}
