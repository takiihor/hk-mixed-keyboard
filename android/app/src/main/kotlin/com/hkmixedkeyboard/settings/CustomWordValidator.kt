package com.hkmixedkeyboard.settings

import java.util.Locale

/** Validation shared by the custom-word form and CSV import. */
object CustomWordValidator {
    const val MAX_DISPLAY_CHARACTERS = 64
    const val MAX_QUICK_CODE_LENGTH = 8
    const val MAX_IMPORT_ROWS = 5_000
    const val MAX_IMPORT_BYTES = 1_024 * 1_024L
    const val MAX_IMPORT_LINE_CHARACTERS = 512

    sealed class Result {
        data class Valid(val display: String, val quickCode: String) : Result()
        data class Invalid(val message: String) : Result()
    }

    fun validate(displayInput: String, quickCodeInput: String): Result {
        val display = displayInput.trim()
        val quickCode = quickCodeInput.trim().lowercase(Locale.ROOT)
        if (display.isEmpty() || quickCode.isEmpty()) {
            return Result.Invalid("請填寫詞語及 Quick 碼")
        }
        if (displayInput.hasLineBreak() || quickCodeInput.hasLineBreak()) {
            return Result.Invalid("詞語及 Quick 碼不可包含換行")
        }
        if (display.codePointCount(0, display.length) > MAX_DISPLAY_CHARACTERS) {
            return Result.Invalid("詞語最多 64 個字元")
        }
        if (quickCode.length > MAX_QUICK_CODE_LENGTH || !quickCode.matches(Regex("[a-z]+"))) {
            return Result.Invalid("Quick 碼只可使用 1–8 個英文字母")
        }
        return Result.Valid(display, quickCode)
    }

    fun canAcceptImportRow(importedRowCount: Int): Boolean = importedRowCount < MAX_IMPORT_ROWS

    private fun String.hasLineBreak(): Boolean = contains('\n') || contains('\r')
}
