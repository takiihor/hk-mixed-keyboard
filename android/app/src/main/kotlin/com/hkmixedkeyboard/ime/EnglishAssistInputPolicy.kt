package com.hkmixedkeyboard.ime

/**
 * Lets the symbol panel extend an English-assist key without turning ordinary
 * punctuation into composition. The caller supplies the real corpus-prefix
 * lookup so this policy stays deterministic and Android-free.
 */
object EnglishAssistInputPolicy {
    fun shouldComposeSymbol(
        currentBuffer: String,
        symbol: String,
        hasAssistPrefix: (String) -> Boolean
    ): Boolean {
        if (currentBuffer.isEmpty() || symbol !in COMPOSING_SYMBOLS) return false
        val proposed = currentBuffer + symbol
        if (!proposed.all(::isEnglishAssistKeyCharacter)) return false
        return hasAssistPrefix(proposed.lowercase())
    }

    private fun isEnglishAssistKeyCharacter(character: Char): Boolean =
        character in 'a'..'z' || character in 'A'..'Z' ||
            character == '-' || character == '\''

    private val COMPOSING_SYMBOLS = setOf("-", "'")
}
