package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.settings.InputSchemeSelectionCoordinator
import org.junit.Assert.assertEquals
import org.junit.Test

class InputSchemeSelectionCoordinatorTest {

    @Test
    fun `hydration applies persisted selection without persisting it again`() {
        val coordinator = InputSchemeSelectionCoordinator()

        assertEquals(
            InputSchemeSelectionCoordinator.Action.ApplyToUi(Scheme.JYUTPING),
            coordinator.onHydrated(Scheme.JYUTPING)
        )
    }

    @Test
    fun `user selection is persisted`() {
        val coordinator = InputSchemeSelectionCoordinator()

        assertEquals(
            InputSchemeSelectionCoordinator.Action.Persist(Scheme.PINYIN),
            coordinator.onUserSelection(Scheme.PINYIN)
        )
    }

    @Test
    fun `late hydration cannot overwrite a fast user selection`() {
        val coordinator = InputSchemeSelectionCoordinator()
        coordinator.onUserSelection(Scheme.PINYIN)

        assertEquals(
            InputSchemeSelectionCoordinator.Action.Ignore,
            coordinator.onHydrated(Scheme.QUICK)
        )
    }
}
