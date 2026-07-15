package com.hkmixedkeyboard

import com.hkmixedkeyboard.settings.CustomWordValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomWordValidatorTest {
    @Test
    fun `valid custom word is trimmed and normalized`() {
        assertEquals(
            CustomWordValidator.Result.Valid(display = "我哋", quickCode = "qirp"),
            CustomWordValidator.validate(" 我哋 ", " QIRP ")
        )
    }

    @Test
    fun `blank custom word fields are rejected`() {
        assertEquals(
            CustomWordValidator.Result.Invalid(CustomWordValidator.Error.MISSING),
            CustomWordValidator.validate("", "qirp")
        )
    }

    @Test
    fun `display text longer than 64 characters is rejected`() {
        assertEquals(
            CustomWordValidator.Result.Invalid(CustomWordValidator.Error.DISPLAY_TOO_LONG),
            CustomWordValidator.validate("我".repeat(65), "qirp")
        )
    }

    @Test
    fun `line breaks are rejected`() {
        assertEquals(
            CustomWordValidator.Result.Invalid(CustomWordValidator.Error.LINE_BREAK),
            CustomWordValidator.validate("我\n哋", "qirp")
        )
    }

    @Test
    fun `Quick code must contain letters only`() {
        assertEquals(
            CustomWordValidator.Result.Invalid(CustomWordValidator.Error.QUICK_CODE),
            CustomWordValidator.validate("我哋", "qir1")
        )
    }

    @Test
    fun `Quick code longer than eight letters is rejected`() {
        assertEquals(
            CustomWordValidator.Result.Invalid(CustomWordValidator.Error.QUICK_CODE),
            CustomWordValidator.validate("我哋", "abcdefghi")
        )
    }

    @Test
    fun `import accepts at most 5000 valid rows`() {
        assertTrue(CustomWordValidator.canAcceptImportRow(4_999))
        assertFalse(CustomWordValidator.canAcceptImportRow(5_000))
    }
}
