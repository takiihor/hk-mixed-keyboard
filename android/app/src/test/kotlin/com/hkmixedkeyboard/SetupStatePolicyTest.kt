package com.hkmixedkeyboard

import com.hkmixedkeyboard.settings.SetupStatePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStatePolicyTest {
    @Test
    fun `enabled and selected states are evaluated independently`() {
        val state = SetupStatePolicy.evaluate(
            packageName = "com.hkmixedkeyboard",
            enabledServicePackages = setOf("com.hkmixedkeyboard"),
            selectedImeId = "com.other/.Ime"
        )
        assertTrue(state.enabled)
        assertFalse(state.selected)
        assertFalse(state.complete)
    }

    @Test
    fun `setup is complete only when selected IME belongs to app`() {
        val state = SetupStatePolicy.evaluate(
            packageName = "com.hkmixedkeyboard",
            enabledServicePackages = setOf("com.hkmixedkeyboard"),
            selectedImeId = "com.hkmixedkeyboard/.ime.HkImeService"
        )
        assertTrue(state.complete)
    }
}
