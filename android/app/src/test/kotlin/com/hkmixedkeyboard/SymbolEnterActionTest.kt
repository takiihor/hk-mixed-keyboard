package com.hkmixedkeyboard

import android.view.inputmethod.EditorInfo
import com.hkmixedkeyboard.ui.SymbolEnterAction
import org.junit.Assert.assertEquals
import org.junit.Test

class SymbolEnterActionTest {

    @Test
    fun `editor actions resolve to matching symbol enter presentation`() {
        assertEquals(SymbolEnterAction.SEARCH, SymbolEnterAction.fromImeOptions(EditorInfo.IME_ACTION_SEARCH))
        assertEquals(SymbolEnterAction.SEND, SymbolEnterAction.fromImeOptions(EditorInfo.IME_ACTION_SEND))
        assertEquals(SymbolEnterAction.NEXT, SymbolEnterAction.fromImeOptions(EditorInfo.IME_ACTION_NEXT))
        assertEquals(SymbolEnterAction.DONE, SymbolEnterAction.fromImeOptions(EditorInfo.IME_ACTION_DONE))
        assertEquals(SymbolEnterAction.GO, SymbolEnterAction.fromImeOptions(EditorInfo.IME_ACTION_GO))
    }

    @Test
    fun `no action or no enter action flag resolves to return`() {
        assertEquals(SymbolEnterAction.RETURN, SymbolEnterAction.fromImeOptions(EditorInfo.IME_ACTION_NONE))
        assertEquals(
            SymbolEnterAction.RETURN,
            SymbolEnterAction.fromImeOptions(EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION)
        )
    }
}
