package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.SymbolPageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolPageLayoutTest {

    @Test
    fun `symbol page first row includes half-width period`() {
        assertTrue(SymbolPageView.SYMBOL_ROWS.first().contains("."))
    }

    @Test
    fun `symbol rows keep ten keys per row`() {
        assertEquals(listOf(10, 10, 10, 10), SymbolPageView.SYMBOL_ROWS.map { it.size })
    }

    @Test
    fun `symbol page has no repeated symbols`() {
        val symbols = SymbolPageView.SYMBOL_ROWS.flatten().map { it.visualDuplicateKey() }

        assertEquals(symbols.distinct(), symbols)
    }

    private fun String.visualDuplicateKey(): String = when (this) {
        "，" -> ","
        "？" -> "?"
        "！" -> "!"
        "（" -> "("
        "）" -> ")"
        else -> this
    }

    @Test
    fun `symbol page return key is labelled ABC`() {
        assertEquals("ABC", SymbolPageView.RETURN_LABEL)
    }
}
