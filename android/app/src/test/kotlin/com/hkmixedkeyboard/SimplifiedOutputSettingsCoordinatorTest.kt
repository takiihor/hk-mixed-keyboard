package com.hkmixedkeyboard

import com.hkmixedkeyboard.ime.SimplifiedOutputSettingsCoordinator
import org.junit.Assert.assertEquals
import org.junit.Test

class SimplifiedOutputSettingsCoordinatorTest {

    @Test
    fun `false emission invalidates an older true load before it completes`() {
        var finishLoad: ((Boolean) -> Unit)? = null
        val applied = mutableListOf<Boolean>()
        val coordinator = SimplifiedOutputSettingsCoordinator(
            loadConverter = { finishLoad = it },
            apply = { applied += it }
        )

        coordinator.onSetting(enabled = true)
        coordinator.onSetting(enabled = false)
        finishLoad!!.invoke(true)

        assertEquals(listOf(false), applied)
    }

    @Test
    fun `current true emission applies only after its load completes`() {
        var finishLoad: ((Boolean) -> Unit)? = null
        val applied = mutableListOf<Boolean>()
        val coordinator = SimplifiedOutputSettingsCoordinator(
            loadConverter = { finishLoad = it },
            apply = { applied += it }
        )

        coordinator.onSetting(enabled = true)
        assertEquals(emptyList<Boolean>(), applied)

        finishLoad!!.invoke(true)
        assertEquals(listOf(true), applied)
    }
}
