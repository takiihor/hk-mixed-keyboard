package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.settings.InputSchemeTransitionCoordinator
import org.junit.Assert.assertEquals
import org.junit.Test

class InputSchemeTransitionCoordinatorTest {

    @Test
    fun `first persisted emission applies even when it matches initial scheme`() {
        val coordinator = InputSchemeTransitionCoordinator(Scheme.QUICK)

        assertEquals(
            InputSchemeTransitionCoordinator.Action.Apply(Scheme.QUICK),
            coordinator.onPersisted(Scheme.QUICK)
        )
    }

    @Test
    fun `rapid toggles advance from latest requested scheme`() {
        val coordinator = InputSchemeTransitionCoordinator(Scheme.QUICK)

        assertEquals(
            InputSchemeTransitionCoordinator.Action.ApplyAndPersist(Scheme.JYUTPING),
            coordinator.onToggle()
        )
        assertEquals(
            InputSchemeTransitionCoordinator.Action.ApplyAndPersist(Scheme.PINYIN),
            coordinator.onToggle()
        )
    }

    @Test
    fun `direct selection applies and persists the chosen mode`() {
        val coordinator = InputSchemeTransitionCoordinator(Scheme.QUICK)

        assertEquals(
            InputSchemeTransitionCoordinator.Action.ApplyAndPersist(Scheme.PINYIN),
            coordinator.onSelect(Scheme.PINYIN)
        )
        assertEquals(Scheme.PINYIN, coordinator.currentScheme)
    }

    @Test
    fun `selecting the current or pending mode is ignored`() {
        val coordinator = InputSchemeTransitionCoordinator(Scheme.QUICK)

        assertEquals(
            InputSchemeTransitionCoordinator.Action.Ignore,
            coordinator.onSelect(Scheme.QUICK)
        )
        coordinator.onSelect(Scheme.JYUTPING)
        assertEquals(
            InputSchemeTransitionCoordinator.Action.Ignore,
            coordinator.onSelect(Scheme.JYUTPING)
        )
    }

    @Test
    fun `stale persisted emission cannot regress a newer requested scheme`() {
        val coordinator = InputSchemeTransitionCoordinator(Scheme.QUICK)
        coordinator.onToggle()
        coordinator.onToggle()

        assertEquals(
            InputSchemeTransitionCoordinator.Action.Ignore,
            coordinator.onPersisted(Scheme.JYUTPING)
        )
        assertEquals(Scheme.PINYIN, coordinator.currentScheme)
    }

    @Test
    fun `latest persisted emission acknowledges pending request without reapplying`() {
        val coordinator = InputSchemeTransitionCoordinator(Scheme.QUICK)
        coordinator.onToggle()

        assertEquals(
            InputSchemeTransitionCoordinator.Action.Ignore,
            coordinator.onPersisted(Scheme.JYUTPING)
        )
        assertEquals(Scheme.JYUTPING, coordinator.currentScheme)
    }

    @Test
    fun `external persisted change applies when no request is pending`() {
        val coordinator = InputSchemeTransitionCoordinator(Scheme.QUICK)

        assertEquals(
            InputSchemeTransitionCoordinator.Action.Apply(Scheme.PINYIN),
            coordinator.onPersisted(Scheme.PINYIN)
        )
        assertEquals(Scheme.PINYIN, coordinator.currentScheme)
    }

    @Test
    fun `failed latest request clears pending and reconciles to persisted scheme`() {
        val coordinator = InputSchemeTransitionCoordinator(Scheme.QUICK)
        coordinator.onPersisted(Scheme.QUICK)
        coordinator.onToggle()

        assertEquals(
            InputSchemeTransitionCoordinator.Action.Apply(Scheme.QUICK),
            coordinator.onPersistenceFailed(Scheme.JYUTPING)
        )
        assertEquals(Scheme.QUICK, coordinator.currentScheme)
    }

    @Test
    fun `failure of superseded request leaves latest request pending`() {
        val coordinator = InputSchemeTransitionCoordinator(Scheme.QUICK)
        coordinator.onPersisted(Scheme.QUICK)
        coordinator.onToggle()
        coordinator.onToggle()

        assertEquals(
            InputSchemeTransitionCoordinator.Action.Ignore,
            coordinator.onPersistenceFailed(Scheme.JYUTPING)
        )
        assertEquals(
            InputSchemeTransitionCoordinator.Action.Ignore,
            coordinator.onPersisted(Scheme.PINYIN)
        )
        assertEquals(Scheme.PINYIN, coordinator.currentScheme)
    }
}
