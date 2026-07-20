package com.hkmixedkeyboard.ime

import android.text.InputType

object DirectInputPolicy {
    fun shouldCommitKeyDirectly(
        label: String,
        directLatinCommit: Boolean,
        compositionBuffer: String = "",
        directSymbolCommit: Boolean = false
    ): Boolean =
        (isAsciiDigit(label) && (directLatinCommit || !isUnicodeFallbackPrefix(compositionBuffer))) ||
            (directLatinCommit && isAsciiLetter(label)) ||
            ((directLatinCommit || directSymbolCommit) && isDirectEntrySymbol(label))

    fun shouldUseDirectLatinCommit(
        inputType: Int,
        packageName: String?,
        privateImeOptions: String?
    ): Boolean {
        if (inputType == InputType.TYPE_NULL) return true
        if (EditorLayoutPolicy.usesDirectEntry(inputType)) return true
        if (isPasswordStyleText(inputType)) return true

        val packageHint = packageName.orEmpty().lowercase()
        if (TERMINAL_PACKAGE_HINTS.any { it in packageHint }) return true

        val privateHint = privateImeOptions.orEmpty().lowercase()
        return TERMINAL_PRIVATE_HINTS.any { it in privateHint }
    }

    private fun isAsciiDigit(label: String): Boolean =
        label.length == 1 && label[0] in '0'..'9'

    private fun isAsciiLetter(label: String): Boolean =
        label.length == 1 && (label[0] in 'a'..'z' || label[0] in 'A'..'Z')

    private fun isDirectEntrySymbol(label: String): Boolean = label in DIRECT_ENTRY_SYMBOLS

    private fun isUnicodeFallbackPrefix(buffer: String): Boolean =
        UNICODE_FALLBACK_PREFIX.matches(buffer)

    private fun isPasswordStyleText(inputType: Int): Boolean {
        val base = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return base == InputType.TYPE_CLASS_TEXT &&
            variation in PASSWORD_STYLE_TEXT_VARIATIONS
    }

    private val PASSWORD_STYLE_TEXT_VARIATIONS = setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
    )

    private val TERMINAL_PACKAGE_HINTS = listOf(
        "termux",
        "terminal",
        "androidterm",
        "termius",
        "connectbot",
        "juicessh",
        "serverauditor",
        "ssh"
    )

    private val TERMINAL_PRIVATE_HINTS = listOf(
        "terminal",
        "termux",
        "shell",
        "ssh"
    )

    private val UNICODE_FALLBACK_PREFIX = Regex("(?i)u[0-9a-f]{0,4}")

    private val DIRECT_ENTRY_SYMBOLS = setOf("@", "/", "+", "-", "_", ":", "*", "#", ".")
}
