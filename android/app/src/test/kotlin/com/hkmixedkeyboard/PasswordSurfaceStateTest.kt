package com.hkmixedkeyboard

import com.hkmixedkeyboard.ime.PasswordSurfaceState
import com.hkmixedkeyboard.ui.KeyboardSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordSurfaceStateTest {
    @Test
    fun `text password starts on its editor surface without an alphabet action`() {
        val state = PasswordSurfaceState()

        state.startEditor(KeyboardSurface.TEXT_PASSWORD)

        assertEquals(KeyboardSurface.TEXT_PASSWORD, state.visibleSurface)
        assertFalse(state.showAlphabetAction)
    }

    @Test
    fun `manual PIN mode exposes the numeric password surface and alphabet action`() {
        val state = PasswordSurfaceState()
        state.startEditor(KeyboardSurface.TEXT_PASSWORD)

        state.enterManualPin()

        assertEquals(KeyboardSurface.NUMERIC_PASSWORD, state.visibleSurface)
        assertTrue(state.showAlphabetAction)
    }

    @Test
    fun `leaving manual PIN mode restores the text password surface`() {
        val state = PasswordSurfaceState()
        state.startEditor(KeyboardSurface.TEXT_PASSWORD)
        state.enterManualPin()

        state.leaveManualPin()

        assertEquals(KeyboardSurface.TEXT_PASSWORD, state.visibleSurface)
        assertFalse(state.showAlphabetAction)
    }

    @Test
    fun `manual PIN mode is ignored outside text password editors`() {
        listOf(
            KeyboardSurface.NUMERIC_PASSWORD,
            KeyboardSurface.TEXT
        ).forEach { surface ->
            val state = PasswordSurfaceState()
            state.startEditor(surface)

            state.enterManualPin()

            assertEquals(surface, state.visibleSurface)
            assertFalse(state.showAlphabetAction)
        }
    }

    @Test
    fun `starting a text password editor resets prior manual PIN mode`() {
        val state = PasswordSurfaceState()
        state.startEditor(KeyboardSurface.TEXT_PASSWORD)
        state.enterManualPin()

        state.startEditor(KeyboardSurface.TEXT_PASSWORD)

        assertEquals(KeyboardSurface.TEXT_PASSWORD, state.visibleSurface)
        assertFalse(state.showAlphabetAction)
    }

    @Test
    fun `finishing an editor resets to ordinary text`() {
        val state = PasswordSurfaceState()
        state.startEditor(KeyboardSurface.TEXT_PASSWORD)
        state.enterManualPin()

        state.finishEditor()

        assertEquals(KeyboardSurface.TEXT, state.visibleSurface)
        assertFalse(state.showAlphabetAction)
    }
}
