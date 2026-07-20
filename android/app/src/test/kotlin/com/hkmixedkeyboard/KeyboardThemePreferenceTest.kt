package com.hkmixedkeyboard

import com.hkmixedkeyboard.settings.KeyboardTheme
import com.hkmixedkeyboard.settings.KeyboardThemePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardThemePreferenceTest {

    @Test
    fun `missing theme preference defaults to dark`() {
        assertEquals(KeyboardTheme.DARK, KeyboardThemePreference.resolve(null))
    }

    @Test
    fun `known stored theme values restore their themes`() {
        assertEquals(KeyboardTheme.DARK, KeyboardThemePreference.resolve("dark"))
        assertEquals(KeyboardTheme.IOS_LIGHT, KeyboardThemePreference.resolve("ios_light"))
    }

    @Test
    fun `unknown stored theme value falls back to dark`() {
        assertEquals(KeyboardTheme.DARK, KeyboardThemePreference.resolve("unexpected"))
    }

    @Test
    fun `themes serialize to stable preference values`() {
        assertEquals("dark", KeyboardThemePreference.serialize(KeyboardTheme.DARK))
        assertEquals("ios_light", KeyboardThemePreference.serialize(KeyboardTheme.IOS_LIGHT))
    }

    @Test
    fun `settings labels and accessibility state identify each theme`() {
        assertEquals("深色", KeyboardThemePreference.label(KeyboardTheme.DARK))
        assertEquals("淺色（iPhone 風格）", KeyboardThemePreference.label(KeyboardTheme.IOS_LIGHT))
        assertEquals("深色鍵盤主題，已選取",
            KeyboardThemePreference.accessibilityDescription(KeyboardTheme.DARK, true))
        assertEquals("淺色 iPhone 風格鍵盤主題，未選取",
            KeyboardThemePreference.accessibilityDescription(KeyboardTheme.IOS_LIGHT, false))
    }
}
