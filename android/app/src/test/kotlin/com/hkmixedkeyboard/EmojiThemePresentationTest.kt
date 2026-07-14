package com.hkmixedkeyboard

import com.hkmixedkeyboard.settings.KeyboardTheme
import com.hkmixedkeyboard.ui.EmojiPanelPresentationState
import com.hkmixedkeyboard.ui.toColors
import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiThemePresentationTest {

    @Test
    fun `theme change preserves Emoji panel presentation state`() {
        val state = EmojiPanelPresentationState(
            selectedCategory = 3,
            scrollY = 412,
            searchQuery = "笑",
            recentEmojis = listOf("😊", "😂"),
            skinTonePreference = 4
        )

        val themed = state.withTheme(KeyboardTheme.IOS_LIGHT.toColors())

        assertEquals(state.selectedCategory, themed.selectedCategory)
        assertEquals(state.scrollY, themed.scrollY)
        assertEquals(state.searchQuery, themed.searchQuery)
        assertEquals(state.recentEmojis, themed.recentEmojis)
        assertEquals(state.skinTonePreference, themed.skinTonePreference)
        assertEquals(KeyboardTheme.IOS_LIGHT.toColors(), themed.themeColors)
    }
}
