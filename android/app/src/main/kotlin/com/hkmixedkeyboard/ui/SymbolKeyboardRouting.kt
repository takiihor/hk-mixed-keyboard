package com.hkmixedkeyboard.ui

/** Pure translation from a rendered key spec to one IME-level operation. */
object SymbolKeyboardRouting {
    sealed class Event(
        val insertsText: Boolean = false,
        val usesExistingImeHandler: Boolean = false
    ) {
        data class CommitText(val text: String) : Event(insertsText = true)
        data object TogglePage : Event()
        data object ReturnAlphabet : Event()
        data object Space : Event(usesExistingImeHandler = true)
        data object Backspace : Event(usesExistingImeHandler = true)
        data object Enter : Event(usesExistingImeHandler = true)
    }

    fun eventFor(key: SymbolKeySpec): Event = when (key.role) {
        SymbolKeyRole.TEXT -> Event.CommitText(requireNotNull(key.commitText))
        SymbolKeyRole.TOGGLE_PAGE -> Event.TogglePage
        SymbolKeyRole.RETURN_TO_ALPHABET -> Event.ReturnAlphabet
        SymbolKeyRole.SPACE -> Event.Space
        SymbolKeyRole.BACKSPACE -> Event.Backspace
        SymbolKeyRole.ENTER -> Event.Enter
    }
}
