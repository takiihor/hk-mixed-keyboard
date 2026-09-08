package com.hkmixedkeyboard.ime

/**
 * Splits a commit into the text written to the editor and whether a real ENTER key
 * event must follow it.
 *
 * Enter reaches the service as a trailing "\n" on the committed text, possibly with
 * flushed composition ahead of it ("ls\n" when a terminal's pass-through Enter
 * finalizes a buffer). Committing that newline as a character would insert a line
 * break instead of running the command or triggering the field's action, so it has
 * to be peeled off and sent as a key event.
 */
object EnterKeySplit {

    data class Result(val text: String, val sendEnter: Boolean)

    fun split(committedText: String, swallowEnter: Boolean): Result {
        val sendEnter = committedText.endsWith("\n") && !swallowEnter
        return Result(
            text = if (sendEnter) committedText.dropLast(1) else committedText,
            sendEnter = sendEnter
        )
    }
}
