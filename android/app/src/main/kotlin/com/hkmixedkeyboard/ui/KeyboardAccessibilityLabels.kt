package com.hkmixedkeyboard.ui

import android.content.Context
import com.hkmixedkeyboard.R
import java.util.Locale

/** Localized descriptions for the keyboard's visible and virtual accessibility keys. */
object KeyboardAccessibilityLabels {
    private val cangjieRoots = mapOf(
        "A" to "日", "B" to "月", "C" to "金", "D" to "木", "E" to "水",
        "F" to "火", "G" to "土", "H" to "竹", "I" to "戈", "J" to "十",
        "K" to "大", "L" to "中", "M" to "一", "N" to "弓", "O" to "人",
        "P" to "心", "Q" to "手", "R" to "口", "S" to "尸", "T" to "廿",
        "U" to "山", "V" to "女", "W" to "田", "X" to "難", "Y" to "卜",
        "Z" to "重"
    )

    data class Text(
        val locale: Locale,
        val deleteKey: String,
        val capsLocked: String,
        val switchLower: String,
        val switchUpper: String,
        val enterKey: String,
        val spaceCurrentFormat: String,
        val emojiKey: String,
        val symbolKey: String,
        val settingsKey: String,
        val nextImeKey: String,
        val modeCurrentFormat: String,
        val pinModeKey: String,
        val commaKey: String,
        val periodKey: String,
        val questionKey: String,
        val exclaimKey: String,
        val numberKeyFormat: String,
        val rootKeyFormat: String,
        val keyFormat: String,
        val quickName: String,
        val jyutpingName: String,
        val pinyinName: String
    )

    fun from(context: Context): Text = Text(
        locale = context.resources.configuration.locales[0],
        deleteKey = context.getString(R.string.key_a11y_delete),
        capsLocked = context.getString(R.string.key_a11y_caps_locked),
        switchLower = context.getString(R.string.key_a11y_switch_lower),
        switchUpper = context.getString(R.string.key_a11y_switch_upper),
        enterKey = context.getString(R.string.key_a11y_enter),
        spaceCurrentFormat = context.getString(R.string.key_a11y_space_current),
        emojiKey = context.getString(R.string.key_a11y_emoji),
        symbolKey = context.getString(R.string.key_a11y_symbol),
        settingsKey = context.getString(R.string.key_a11y_settings),
        nextImeKey = context.getString(R.string.key_a11y_next_ime),
        modeCurrentFormat = context.getString(R.string.key_a11y_mode_current),
        pinModeKey = context.getString(R.string.key_a11y_pin_mode),
        commaKey = context.getString(R.string.key_a11y_comma),
        periodKey = context.getString(R.string.key_a11y_period),
        questionKey = context.getString(R.string.key_a11y_question),
        exclaimKey = context.getString(R.string.key_a11y_exclaim),
        numberKeyFormat = context.getString(R.string.key_a11y_number),
        rootKeyFormat = context.getString(R.string.key_a11y_root),
        keyFormat = context.getString(R.string.key_a11y_plain),
        quickName = context.getString(R.string.quick_label),
        jyutpingName = context.getString(R.string.jyutping_label),
        pinyinName = context.getString(R.string.pinyin_label)
    )

    fun cangjieRootFor(label: String): String? = cangjieRoots[label]

    fun descriptionFor(
        label: String,
        showCangjieRoots: Boolean = true,
        spaceLabel: String = "速成",
        modeLabel: String = "速",
        shiftActive: Boolean = false,
        shiftLocked: Boolean = false,
        text: Text = TRADITIONAL_CHINESE
    ): String = when (label) {
        KeyboardLayout.KEY_BACKSPACE -> text.deleteKey
        KeyboardLayout.KEY_SHIFT -> when {
            shiftLocked -> text.capsLocked
            shiftActive -> text.switchLower
            else -> text.switchUpper
        }
        KeyboardLayout.KEY_ENTER -> text.enterKey
        KeyboardLayout.KEY_SPACE -> format(text, text.spaceCurrentFormat, spaceLabel)
        KeyboardLayout.KEY_EMOJI -> text.emojiKey
        KeyboardLayout.KEY_SYMBOL -> text.symbolKey
        KeyboardLayout.KEY_SETTINGS -> text.settingsKey
        KeyboardLayout.KEY_NEXT_IME -> text.nextImeKey
        KeyboardLayout.KEY_MODE -> format(text, text.modeCurrentFormat, modeName(modeLabel, text))
        KeyboardLayout.KEY_PIN_MODE -> text.pinModeKey
        KeyboardLayout.KEY_COMMA -> text.commaKey
        KeyboardLayout.KEY_PERIOD -> text.periodKey
        KeyboardLayout.KEY_QUESTION -> text.questionKey
        KeyboardLayout.KEY_EXCLAIM -> text.exclaimKey
        else -> letterOrNumberDescription(label, showCangjieRoots, shiftActive, text)
    }

    private fun letterOrNumberDescription(
        label: String,
        showCangjieRoots: Boolean,
        shiftActive: Boolean,
        text: Text
    ): String {
        if (label.length == 1 && label[0] in '0'..'9') {
            return format(text, text.numberKeyFormat, label)
        }
        val shown = if (label.length == 1 && label[0] in 'A'..'Z' && !shiftActive) {
            label.lowercase()
        } else {
            label
        }
        val root = cangjieRootFor(label)
        return if (showCangjieRoots && root != null) {
            format(text, text.rootKeyFormat, root, shown)
        } else {
            format(text, text.keyFormat, shown)
        }
    }

    private fun modeName(modeLabel: String, text: Text): String = when (modeLabel) {
        "速" -> text.quickName
        "粵" -> text.jyutpingName
        "拼" -> text.pinyinName
        else -> modeLabel
    }

    private fun format(text: Text, pattern: String, vararg values: Any): String =
        String.format(text.locale, pattern, *values)

    private val TRADITIONAL_CHINESE = Text(
        locale = Locale.TRADITIONAL_CHINESE,
        deleteKey = "刪除鍵",
        capsLocked = "大寫鎖定",
        switchLower = "切換到小寫",
        switchUpper = "切換到大寫",
        enterKey = "換行鍵",
        spaceCurrentFormat = "空格鍵，現時%1\$s",
        emojiKey = "表情符號鍵",
        symbolKey = "符號鍵",
        settingsKey = "鍵盤設定",
        nextImeKey = "切換鍵盤",
        modeCurrentFormat = "切換輸入模式，現時%1\$s",
        pinModeKey = "切換至數字密碼鍵盤",
        commaKey = "逗號鍵",
        periodKey = "句號鍵",
        questionKey = "問號或驚嘆號鍵",
        exclaimKey = "驚嘆號鍵",
        numberKeyFormat = "數字%1\$s鍵",
        rootKeyFormat = "%1\$s字根，%2\$s鍵",
        keyFormat = "%1\$s鍵",
        quickName = "速成",
        jyutpingName = "粵拼",
        pinyinName = "拼音"
    )
}
