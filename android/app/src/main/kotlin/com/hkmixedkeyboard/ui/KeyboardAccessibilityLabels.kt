package com.hkmixedkeyboard.ui

/** Chinese descriptions for the keyboard's visible and virtual accessibility keys. */
object KeyboardAccessibilityLabels {
    private val cangjieRoots = mapOf(
        "A" to "日", "B" to "月", "C" to "金", "D" to "木", "E" to "水",
        "F" to "火", "G" to "土", "H" to "竹", "I" to "戈", "J" to "十",
        "K" to "大", "L" to "中", "M" to "一", "N" to "弓", "O" to "人",
        "P" to "心", "Q" to "手", "R" to "口", "S" to "尸", "T" to "廿",
        "U" to "山", "V" to "女", "W" to "田", "X" to "難", "Y" to "卜",
        "Z" to "重"
    )

    fun cangjieRootFor(label: String): String? = cangjieRoots[label]

    fun descriptionFor(
        label: String,
        showCangjieRoots: Boolean = true,
        spaceLabel: String = "速成",
        modeLabel: String = "速",
        shiftActive: Boolean = false,
        shiftLocked: Boolean = false
    ): String = when (label) {
        KeyboardLayout.KEY_BACKSPACE -> "刪除鍵"
        KeyboardLayout.KEY_SHIFT -> when {
            shiftLocked -> "大寫鎖定"
            shiftActive -> "切換到小寫"
            else -> "切換到大寫"
        }
        KeyboardLayout.KEY_ENTER -> "換行鍵"
        KeyboardLayout.KEY_SPACE -> "空格鍵，現時$spaceLabel"
        KeyboardLayout.KEY_EMOJI -> "表情符號鍵"
        KeyboardLayout.KEY_SYMBOL -> "符號鍵"
        KeyboardLayout.KEY_MODE -> "切換輸入模式，現時${modeName(modeLabel)}"
        KeyboardLayout.KEY_COMMA -> "逗號鍵"
        KeyboardLayout.KEY_PERIOD -> "句號鍵"
        KeyboardLayout.KEY_QUESTION -> "問號或驚嘆號鍵"
        KeyboardLayout.KEY_EXCLAIM -> "驚嘆號鍵"
        else -> letterOrNumberDescription(label, showCangjieRoots, shiftActive)
    }

    private fun letterOrNumberDescription(
        label: String,
        showCangjieRoots: Boolean,
        shiftActive: Boolean
    ): String {
        if (label.length == 1 && label[0] in '0'..'9') return "數字${label}鍵"
        val shown = if (label.length == 1 && label[0] in 'A'..'Z' && !shiftActive) {
            label.lowercase()
        } else {
            label
        }
        val root = cangjieRootFor(label)
        return if (showCangjieRoots && root != null) "${root}字根，${shown}鍵" else "${shown}鍵"
    }

    private fun modeName(modeLabel: String): String = when (modeLabel) {
        "速" -> "速成"
        "粵" -> "粵拼"
        "拼" -> "拼音"
        else -> modeLabel
    }
}
