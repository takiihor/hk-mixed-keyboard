package com.hkmixedkeyboard.ui

import com.hkmixedkeyboard.settings.KeyboardTheme

/** State that must survive a palette update in the Emoji surface. */
data class EmojiPanelPresentationState(
    val selectedCategory: Int = 0,
    val scrollY: Int = 0,
    val searchQuery: String = "",
    val recentEmojis: List<String> = emptyList(),
    val skinTonePreference: Int = 0,
    val themeColors: KeyboardThemeColors = KeyboardTheme.DARK.toColors()
) {
    fun withTheme(colors: KeyboardThemeColors): EmojiPanelPresentationState =
        copy(themeColors = colors)
}
