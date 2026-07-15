package com.hkmixedkeyboard.settings

import com.hkmixedkeyboard.decoder.JyutpingNormalizer
import com.hkmixedkeyboard.decoder.PinyinNormalizer
import com.hkmixedkeyboard.decoder.Scheme
import java.util.Locale

/** Validation shared by the custom-word form and CSV import. */
object CustomWordValidator {
    const val MAX_DISPLAY_CHARACTERS = 64
    const val MAX_QUICK_CODE_LENGTH = 8
    const val MAX_ROMANIZATION_CODE_LENGTH = 72
    const val MAX_IMPORT_ROWS = 5_000
    const val MAX_IMPORT_BYTES = 1_024 * 1_024L
    const val MAX_IMPORT_LINE_CHARACTERS = 512

    enum class Error {
        MISSING, LINE_BREAK, DISPLAY_TOO_LONG, QUICK_CODE, JYUTPING_CODE, PINYIN_CODE, SCHEME
    }

    sealed class Result {
        data class Valid(
            val display: String,
            val quickCode: String,
            val scheme: Scheme = Scheme.QUICK
        ) : Result()
        data class Invalid(val error: Error) : Result()
    }

    fun validate(displayInput: String, quickCodeInput: String): Result =
        validate(Scheme.QUICK, displayInput, quickCodeInput)

    fun validate(scheme: Scheme, displayInput: String, quickCodeInput: String): Result {
        val display = displayInput.trim()
        val rawCode = quickCodeInput.trim().lowercase(Locale.ROOT)
        if (display.isEmpty() || rawCode.isEmpty()) {
            return Result.Invalid(Error.MISSING)
        }
        if (displayInput.hasLineBreak() || quickCodeInput.hasLineBreak()) {
            return Result.Invalid(Error.LINE_BREAK)
        }
        if (display.codePointCount(0, display.length) > MAX_DISPLAY_CHARACTERS) {
            return Result.Invalid(Error.DISPLAY_TOO_LONG)
        }
        val normalizedCode = when (scheme) {
            Scheme.QUICK -> rawCode.takeIf {
                it.length <= MAX_QUICK_CODE_LENGTH && it.matches(Regex("[a-z]+"))
            } ?: return Result.Invalid(Error.QUICK_CODE)
            Scheme.JYUTPING -> rawCode.takeIf { it.length <= MAX_ROMANIZATION_CODE_LENGTH }
                ?.let { JyutpingNormalizer.normalize(it)?.key }
                ?: return Result.Invalid(Error.JYUTPING_CODE)
            Scheme.PINYIN -> rawCode.takeIf { it.length <= MAX_ROMANIZATION_CODE_LENGTH }
                ?.let(PinyinNormalizer::normalize)
                ?: return Result.Invalid(Error.PINYIN_CODE)
            else -> return Result.Invalid(Error.SCHEME)
        }
        return Result.Valid(display, normalizedCode, scheme)
    }

    fun canAcceptImportRow(importedRowCount: Int): Boolean = importedRowCount < MAX_IMPORT_ROWS

    private fun String.hasLineBreak(): Boolean = contains('\n') || contains('\r')
}
