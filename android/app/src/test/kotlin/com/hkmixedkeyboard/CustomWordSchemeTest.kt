package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.decoder.JyutpingSyllables
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
        assertTrue(CustomWordValidator.validate(Scheme.JYUTPING, "測試", "xyz") is CustomWordValidator.Result.Invalid)
        assertTrue(CustomWordValidator.validate(Scheme.PINYIN, "測試", "zzzz") is CustomWordValidator.Result.Invalid)
    }

    @Test
    fun `continuous canonical romanization remains valid`() {
        assertEquals(
            CustomWordValidator.Result.Valid("我哋", "ngodei", Scheme.JYUTPING),
            CustomWordValidator.validate(Scheme.JYUTPING, "我哋", "ngo5 dei6")
        )
        assertEquals(
            CustomWordValidator.Result.Valid("你好嗎", "nihaoma", Scheme.PINYIN),
            CustomWordValidator.validate(Scheme.PINYIN, "你好嗎", "ni3 hao3 ma")
        )
    }

    @Test
    fun `every canonical custom Jyutping syllable is backed by a production character`() {
        val productionAtomicCodes = (
            readJyutping("src/main/assets/corpus/jyutping.csv") +
                readJyutping("src/main/assets/corpus/jyutping_overrides.csv")
            ).asSequence()
            .filter { (_, text, _) -> text.codePointCount(0, text.length) == 1 }
            .map { (code, _, _) -> code }
            .toSet()

        assertTrue(productionAtomicCodes.containsAll(JyutpingSyllables.all))
    }

    @Test
    fun `legacy two-column validation remains Quick`() {
        assertEquals(
            CustomWordValidator.Result.Valid("我哋", "qirp", Scheme.QUICK),
            CustomWordValidator.validate("我哋", "qirp")
        )
    }
}
