package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.SymbolKeyboardSpec
import com.hkmixedkeyboard.ui.SymbolPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolPageLayoutTest {

    @Test
    fun `symbol page data starts with Chinese comma`() {
        assertEquals("，", SymbolKeyboardSpec.page(SymbolPage.COMMON).rows.first().first().commitText)
    }

    @Test
    fun `symbol rows keep ten keys per row`() {
        assertEquals(listOf(10, 10, 10, 10), SymbolKeyboardSpec.page(SymbolPage.COMMON).rows.map { it.size })
    }

    @Test
    fun `symbol page has no repeated symbols`() {
        val symbols = SymbolKeyboardSpec.page(SymbolPage.COMMON).rows.flatten().map { it.commitText!!.visualDuplicateKey() }

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
        assertEquals("ABC", SymbolKeyboardSpec.bottomKeys(SymbolPage.COMMON, com.hkmixedkeyboard.ui.SymbolEnterAction.RETURN.keySpec()).first().label)
    }
}
