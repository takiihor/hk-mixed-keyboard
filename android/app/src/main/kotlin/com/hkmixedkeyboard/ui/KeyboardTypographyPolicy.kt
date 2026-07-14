package com.hkmixedkeyboard.ui

/** Typography and corner anchoring shared by the main keyboard renderer. */
object KeyboardTypographyPolicy {
    const val MAIN_LABEL_TEXT_SIZE_SP = 17f
    const val CANGJIE_ROOT_TEXT_SIZE_SP = 16f
    const val LATIN_HINT_TEXT_SIZE_SP = 13f
    const val SPACE_LABEL_TEXT_SIZE_SP = 13f
    const val POPUP_LABEL_TEXT_SIZE_SP = 21f
    const val HINT_INSET_DP = 3f

    fun latinHintX(keyRightPx: Float, density: Float): Float =
        keyRightPx - HINT_INSET_DP * density

    fun latinHintBaseline(keyTopPx: Float, density: Float, hintAscentPx: Float): Float =
        keyTopPx + HINT_INSET_DP * density - hintAscentPx
}
