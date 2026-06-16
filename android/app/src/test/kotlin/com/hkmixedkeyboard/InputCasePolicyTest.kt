package com.hkmixedkeyboard

import com.hkmixedkeyboard.engine.InputCasePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class InputCasePolicyTest {
    @Test
    fun `lookup form is lowercase`() {
        assertEquals("rr", InputCasePolicy.lookupForm("Rr"))
    }

    @Test
    fun `lowercase typing produces lowercase English candidate`() {
        assertEquals("hello", InputCasePolicy.applyPattern("HELLO", "he"))
    }

    @Test
    fun `initial capital typing produces initial capital English candidate`() {
        assertEquals("Hello", InputCasePolicy.applyPattern("hello", "He"))
    }

    @Test
    fun `two uppercase letters produce uppercase English candidate`() {
        assertEquals("HELLO", InputCasePolicy.applyPattern("hello", "HE"))
    }

    @Test
    fun `Chinese candidate remains unchanged`() {
        assertEquals("你好", InputCasePolicy.applyPattern("你好", "HE"))
    }

    @Test
    fun `mixed Chinese candidate remains unchanged`() {
        assertEquals("Hello你好", InputCasePolicy.applyPattern("Hello你好", "HE"))
    }
}
