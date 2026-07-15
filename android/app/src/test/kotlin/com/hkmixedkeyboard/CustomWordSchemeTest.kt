package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.settings.CustomWordValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomWordSchemeTest {
    @Test
    fun `custom codes validate according to their scheme`() {
        assertTrue(CustomWordValidator.validate(Scheme.QUICK, "我哋", "qirp") is CustomWordValidator.Result.Valid)
        assertTrue(CustomWordValidator.validate(Scheme.JYUTPING, "我哋", "ngo5 dei6") is CustomWordValidator.Result.Valid)
        assertTrue(CustomWordValidator.validate(Scheme.PINYIN, "香港", "xiang1 gang3") is CustomWordValidator.Result.Valid)
    }

    @Test
    fun `invalid scheme-specific code is rejected without partial acceptance`() {
        assertTrue(CustomWordValidator.validate(Scheme.QUICK, "我哋", "ngo5") is CustomWordValidator.Result.Invalid)
        assertTrue(CustomWordValidator.validate(Scheme.JYUTPING, "我哋", "ngo7") is CustomWordValidator.Result.Invalid)
        assertTrue(CustomWordValidator.validate(Scheme.PINYIN, "香港", "xiang6") is CustomWordValidator.Result.Invalid)
    }

    @Test
    fun `legacy two-column validation remains Quick`() {
        assertEquals(
            CustomWordValidator.Result.Valid("我哋", "qirp", Scheme.QUICK),
            CustomWordValidator.validate("我哋", "qirp")
        )
    }
}
