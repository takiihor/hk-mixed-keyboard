package com.hkmixedkeyboard.ui

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.hkmixedkeyboard.R

/** Resource-backed keyboard colour tokens shared by all keyboard renderers. */
data class KeyboardThemeColors(
    @ColorInt val keyboardBackground: Int,
    @ColorInt val keyBackground: Int,
    @ColorInt val specialKeyBackground: Int,
    @ColorInt val spaceKeyBackground: Int,
    @ColorInt val pressedKeyBackground: Int,
    @ColorInt val enterKeyBackground: Int,
    @ColorInt val label: Int,
    @ColorInt val hint: Int,
    @ColorInt val enterLabel: Int,
    @ColorInt val popupBackground: Int,
    @ColorInt val popupLabel: Int,
    @ColorInt val symbolKeyboardBackground: Int,
    @ColorInt val symbolKeyBackground: Int,
    @ColorInt val symbolFunctionKeyBackground: Int,
    @ColorInt val symbolPressedKeyBackground: Int,
    @ColorInt val symbolLabel: Int,
    @ColorInt val symbolIndicatorActive: Int,
    @ColorInt val symbolIndicatorInactive: Int,
    @ColorInt val symbolPopupBackground: Int,
    @ColorInt val symbolPopupLabel: Int
) {
    companion object {
        fun from(context: Context): KeyboardThemeColors = KeyboardThemeColors(
            keyboardBackground = color(context, R.color.keyboard_bg),
            keyBackground = color(context, R.color.key_bg),
            specialKeyBackground = color(context, R.color.key_bg_special),
            spaceKeyBackground = color(context, R.color.key_bg_space),
            pressedKeyBackground = color(context, R.color.key_bg_pressed),
            enterKeyBackground = color(context, R.color.key_bg_enter),
            label = color(context, R.color.key_label),
            hint = color(context, R.color.key_radical),
            enterLabel = color(context, R.color.key_label_enter),
            popupBackground = color(context, R.color.key_popup_bg),
            popupLabel = color(context, R.color.key_popup_label),
            symbolKeyboardBackground = color(context, R.color.symbol_keyboard_bg),
            symbolKeyBackground = color(context, R.color.symbol_key_bg),
            symbolFunctionKeyBackground = color(context, R.color.symbol_function_key_bg),
            symbolPressedKeyBackground = color(context, R.color.symbol_key_bg_pressed),
            symbolLabel = color(context, R.color.symbol_key_label),
            symbolIndicatorActive = color(context, R.color.symbol_page_indicator_active),
            symbolIndicatorInactive = color(context, R.color.symbol_page_indicator_inactive),
            symbolPopupBackground = color(context, R.color.symbol_popup_bg),
            symbolPopupLabel = color(context, R.color.symbol_popup_label)
        )

        private fun color(context: Context, id: Int): Int = ContextCompat.getColor(context, id)
    }
}
