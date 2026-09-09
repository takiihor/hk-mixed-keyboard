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
            CustomWordValidator.Result.Invalid("請填寫詞語及 Quick 碼"),
            CustomWordValidator.validate("", "qirp")
        )
    }

    @Test
    fun `display text longer than 64 characters is rejected`() {
        assertEquals(
            CustomWordValidator.Result.Invalid("詞語最多 64 個字元"),
            CustomWordValidator.validate("我".repeat(65), "qirp")
        )
    }

    @Test
    fun `line breaks are rejected`() {
        assertEquals(
            CustomWordValidator.Result.Invalid("詞語及 Quick 碼不可包含換行"),
            CustomWordValidator.validate("我\n哋", "qirp")
        )
    }

    @Test
    fun `Quick code must contain letters only`() {
        assertEquals(
            CustomWordValidator.Result.Invalid("Quick 碼只可使用 1–8 個英文字母"),
            CustomWordValidator.validate("我哋", "qir1")
        )
    }

    @Test
    fun `Quick code longer than eight letters is rejected`() {
        assertEquals(
            CustomWordValidator.Result.Invalid("Quick 碼只可使用 1–8 個英文字母"),
            CustomWordValidator.validate("我哋", "abcdefghi")
        )
    }

    @Test
    fun `import accepts at most 5000 valid rows`() {
        assertTrue(CustomWordValidator.canAcceptImportRow(4_999))
        assertFalse(CustomWordValidator.canAcceptImportRow(5_000))
    }
}
