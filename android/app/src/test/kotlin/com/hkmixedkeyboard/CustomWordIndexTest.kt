package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.CustomWordIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomWordIndexTest {

    private val index = CustomWordIndex(
        mapOf(
            "ogrp" to listOf(cnChar("我哋", "ogrp", isHkCore = true)),
            "qirp" to listOf(cnChar("你哋", "qirp", isHkCore = true))
        )
    )

    @Test
    fun `exact code returns the custom word`() {
        assertEquals(listOf("我哋"), index.exact("ogrp").map { it.text })
    }

    @Test
    fun `unknown code returns nothing`() {
        assertTrue(index.exact("zzzz").isEmpty())
    }

    @Test
    fun `prefix is recognized and surfaces matching words`() {
        assertTrue(index.hasPrefix("og"))
        assertEquals(listOf("我哋"), index.prefixMatches("og").map { it.text })
    }

    @Test
    fun `non-prefix returns no matches`() {
        assertFalse(index.hasPrefix("zz"))
        assertTrue(index.prefixMatches("zz").isEmpty())
    }

    @Test
    fun `empty index is inert`() {
        assertTrue(CustomWordIndex.EMPTY.isEmpty)
        assertTrue(CustomWordIndex.EMPTY.exact("ogrp").isEmpty())
        assertFalse(CustomWordIndex.EMPTY.hasPrefix("o"))
    }
}
