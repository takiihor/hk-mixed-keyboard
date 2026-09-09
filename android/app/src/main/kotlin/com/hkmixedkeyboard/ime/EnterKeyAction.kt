package com.hkmixedkeyboard.ime

import android.view.inputmethod.EditorInfo

/**
 * What the editor wants the return key to say and look like.
 *
 * iOS repaints the return key in the system accent and relabels it — Go, Search,
 * Send, Join — whenever the field declares an action, and that colour is how
 * people know a form is ready to submit. This keyboard already knew the action
 * (it has to, to send the right key event); the key simply never showed it.
 */
object EnterKeyAction {

    data class Action(val label: String, val accented: Boolean)

    private val NEWLINE = Action("↵", accented = false)

    fun forEditor(imeOptions: Int, inputType: Int): Action {
        // A multi-line field's return key inserts a newline whatever the action
        // bits say, so it must not be dressed up as a submit button.
        if (inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) return NEWLINE
        if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return NEWLINE

        return when (imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> Action("前往", accented = true)
            EditorInfo.IME_ACTION_SEARCH -> Action("搜尋", accented = true)
            EditorInfo.IME_ACTION_SEND -> Action("傳送", accented = true)
            EditorInfo.IME_ACTION_NEXT -> Action("下一項", accented = true)
            EditorInfo.IME_ACTION_DONE -> Action("完成", accented = true)
            EditorInfo.IME_ACTION_PREVIOUS -> Action("上一項", accented = true)
            else -> NEWLINE
        }
    }
}
