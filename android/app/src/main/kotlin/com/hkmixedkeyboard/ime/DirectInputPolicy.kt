package com.hkmixedkeyboard.ime

import android.text.InputType
import com.hkmixedkeyboard.settings.DirectInputMode

object DirectInputPolicy {
    fun shouldCommitKeyDirectly(
        label: String,
        directLatinCommit: Boolean,
        compositionBuffer: String = ""
    ): Boolean =
        (isAsciiDigit(label) && (directLatinCommit || !isUnicodeFallbackPrefix(compositionBuffer))) ||
            (directLatinCommit && isAsciiLetter(label))

    /**
     * [mode] is the user's setting: AUTO detects the field, ALWAYS/NEVER override a
     * wrong guess. Detection can only ever be a heuristic — an app that reports an
     * ordinary text field but behaves like a terminal is indistinguishable — so the
     * override is the escape hatch rather than an ever-growing hint list.
     */
    fun shouldUseDirectLatinCommit(
        inputType: Int,
        packageName: String?,
        privateImeOptions: String?,
        mode: DirectInputMode = DirectInputMode.AUTO
    ): Boolean = when (mode) {
        DirectInputMode.ALWAYS -> true
        DirectInputMode.NEVER -> false
        DirectInputMode.AUTO -> detectDirectLatinField(inputType, packageName, privateImeOptions)
    }

    private fun detectDirectLatinField(
        inputType: Int,
        packageName: String?,
        privateImeOptions: String?
    ): Boolean {
        if (inputType == InputType.TYPE_NULL) return true
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

    // Substring matches against the client package name. Terminals first, then
    // remote-desktop clients: a remote session forwards every keystroke to another
    // machine, so composing text would be swallowed or echoed twice.
    private val TERMINAL_PACKAGE_HINTS = listOf(
        "termux",
        "terminal",
        "androidterm",
        "termius",
        "connectbot",
        "juicessh",
        "serverauditor",
        "ssh",
        "mosh",
        "putty",
        "microsoft.rdc",
        "rdp",
        "vnc",
        "teamviewer",
        "rustdesk",
        "anydesk",
        "remotedesktop"
    )

    private val TERMINAL_PRIVATE_HINTS = listOf(
        "terminal",
        "termux",
        "shell",
        "ssh"
    )

    private val UNICODE_FALLBACK_PREFIX = Regex("(?i)u[0-9a-f]{0,4}")
}
