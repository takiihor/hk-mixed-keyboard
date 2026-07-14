package com.hkmixedkeyboard.ui

import android.view.inputmethod.EditorInfo

enum class SymbolEnterAction(
    val accessibilityLabel: String,
    val icon: SymbolKeyIcon
) {
    SEARCH("搜尋", SymbolKeyIcon.SEARCH),
    SEND("傳送", SymbolKeyIcon.SEND),
    NEXT("下一步", SymbolKeyIcon.NEXT),
    DONE("完成", SymbolKeyIcon.DONE),
    GO("前往", SymbolKeyIcon.GO),
    RETURN("Enter", SymbolKeyIcon.RETURN);

    fun keySpec(): SymbolKeySpec = SymbolKeySpec(
        label = "",
        accessibilityLabel = accessibilityLabel,
        role = SymbolKeyRole.ENTER,
        icon = icon
    )

    companion object {
        fun fromImeOptions(imeOptions: Int): SymbolEnterAction {
            if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return RETURN
            return when (imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_SEARCH -> SEARCH
                EditorInfo.IME_ACTION_SEND -> SEND
                EditorInfo.IME_ACTION_NEXT -> NEXT
                EditorInfo.IME_ACTION_DONE -> DONE
                EditorInfo.IME_ACTION_GO -> GO
                else -> RETURN
            }
        }
    }
}
