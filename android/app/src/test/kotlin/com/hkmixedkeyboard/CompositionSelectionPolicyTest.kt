package com.hkmixedkeyboard

import com.hkmixedkeyboard.ime.CompositionSelectionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class CompositionSelectionPolicyTest {
    @Test
    fun `cursor at the composing end keeps the active composition`() {
        assertFalse(CompositionSelectionPolicy.shouldCancel(12, 12, 10, 12))
    }

    @Test
    fun `moving away from the composing end cancels the old composition`() {
        assertTrue(CompositionSelectionPolicy.shouldCancel(3, 3, 10, 12))
    }

    @Test
    fun `selecting part of composing text cancels the old composition`() {
        assertTrue(CompositionSelectionPolicy.shouldCancel(10, 11, 10, 12))
    }

    @Test
    fun `missing composing range does not trigger an extra edit`() {
        assertFalse(CompositionSelectionPolicy.shouldCancel(3, 3, -1, -1))
    }
}
