package com.hkmixedkeyboard.engine

object InputCasePolicy {
    fun lookupForm(text: String): String = text.lowercase()

    fun applyPattern(candidate: String, typed: String): String {
        if (!candidate.isEnglishText()) return candidate

        val typedLetters = typed.filter { it.isAsciiLetter() }
        return when {
            typedLetters.length >= 2 && typedLetters.all(Char::isUpperCase) ->
                candidate.uppercase()
            typedLetters.firstOrNull()?.isUpperCase() == true ->
                candidate.lowercase().replaceFirstChar(Char::uppercase)
            else ->
                candidate.lowercase()
        }
    }

    private fun String.isEnglishText(): Boolean {
        val letters = filter(Char::isLetter)
        return letters.isNotEmpty() && letters.all { it.isAsciiLetter() }
    }

    private fun Char.isAsciiLetter(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z'
}
