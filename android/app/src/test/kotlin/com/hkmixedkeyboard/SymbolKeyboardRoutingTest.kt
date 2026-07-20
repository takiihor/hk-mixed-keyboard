package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.SymbolEnterAction
import com.hkmixedkeyboard.ui.SymbolKeyboardRouting
import com.hkmixedkeyboard.ui.SymbolKeyboardSpec
import com.hkmixedkeyboard.ui.SymbolPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolKeyboardRoutingTest {

    @Test
    fun `text keys route their exact Unicode payload without punctuation conversion`() {
        val commonComma = SymbolKeyboardSpec.page(SymbolPage.COMMON).rows.first().first()
        val reverseSlash = SymbolKeyboardSpec.page(SymbolPage.EXTENDED).rows.first()[4]

        assertEquals(SymbolKeyboardRouting.Event.CommitText("，"), SymbolKeyboardRouting.eventFor(commonComma))
        assertEquals(SymbolKeyboardRouting.Event.CommitText("\\"), SymbolKeyboardRouting.eventFor(reverseSlash))
    }

    @Test
    fun `page switch and return alphabet never have text payloads`() {
        val commonBottom = SymbolKeyboardSpec.bottomKeys(SymbolPage.COMMON, SymbolEnterAction.RETURN.keySpec())
        val returnEvent = SymbolKeyboardRouting.eventFor(commonBottom[0])
        val pageEvent = SymbolKeyboardRouting.eventFor(commonBottom[1])

        assertEquals(SymbolKeyboardRouting.Event.ReturnAlphabet, returnEvent)
        assertEquals(SymbolKeyboardRouting.Event.TogglePage, pageEvent)
        assertFalse(returnEvent.insertsText)
        assertFalse(pageEvent.insertsText)
    }

    @Test
    fun `space backspace and enter are routed to existing handler events`() {
        val bottom = SymbolKeyboardSpec.bottomKeys(SymbolPage.EXTENDED, SymbolEnterAction.SEARCH.keySpec())

        assertEquals(SymbolKeyboardRouting.Event.Space, SymbolKeyboardRouting.eventFor(bottom[2]))
        assertEquals(SymbolKeyboardRouting.Event.Backspace, SymbolKeyboardRouting.eventFor(bottom[3]))
        assertEquals(SymbolKeyboardRouting.Event.Enter, SymbolKeyboardRouting.eventFor(bottom[4]))
        assertTrue(SymbolKeyboardRouting.eventFor(bottom[2]).usesExistingImeHandler)
        assertTrue(SymbolKeyboardRouting.eventFor(bottom[3]).usesExistingImeHandler)
        assertTrue(SymbolKeyboardRouting.eventFor(bottom[4]).usesExistingImeHandler)
    }
}
