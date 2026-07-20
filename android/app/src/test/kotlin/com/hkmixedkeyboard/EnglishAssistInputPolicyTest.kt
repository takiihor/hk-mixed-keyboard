package com.hkmixedkeyboard

import com.hkmixedkeyboard.ime.EnglishAssistInputPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishAssistInputPolicyTest {

    @Test
    fun `apostrophe stays composing only for a real English assist prefix`() {
        assertTrue(
            EnglishAssistInputPolicy.shouldComposeSymbol("can", "'") { it == "can'" }
        )
        assertFalse(
            EnglishAssistInputPolicy.shouldComposeSymbol("can", "'") { false }
        )
    }

    @Test
    fun `hyphen supports a real compound assist prefix without capturing normal punctuation`() {
        assertTrue(
            EnglishAssistInputPolicy.shouldComposeSymbol("anti", "-") { it == "anti-" }
        )
        assertFalse(
            EnglishAssistInputPolicy.shouldComposeSymbol("anti", ".") { true }
        )
        assertFalse(
            EnglishAssistInputPolicy.shouldComposeSymbol("", "-") { true }
        )
        assertFalse(
            EnglishAssistInputPolicy.shouldComposeSymbol("你", "-") { true }
        )
    }
}
