package com.hkmixedkeyboard

import com.hkmixedkeyboard.settings.KeyboardPrefs
import com.hkmixedkeyboard.ui.ReadingHints
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardPrefsTest {

    @Test
    fun `simplified output defaults off`() {
        assertFalse(KeyboardPrefs().simplifiedOutput)
    }

    @Test
    fun `Jyutping hint defaults on and Pinyin hint defaults off`() {
        assertTrue(KeyboardPrefs().jyutpingHint)
        assertFalse(KeyboardPrefs().pinyinHint)
    }

    @Test
    fun `reading hints report whether any is enabled`() {
        assertFalse(ReadingHints.NONE.any)
        assertTrue(ReadingHints(jyutping = true).any)
        assertTrue(ReadingHints(pinyin = true).any)
    }
}
