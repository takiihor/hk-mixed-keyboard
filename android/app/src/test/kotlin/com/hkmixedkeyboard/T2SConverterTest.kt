package com.hkmixedkeyboard

import com.hkmixedkeyboard.engine.T2SConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 簡體輸出 conversion, run against the real shipped table
 * (assets/t2s/t2s_map.tsv, derived from OpenCC TSCharacters + TSPhrases).
 */
class T2SConverterTest {

    companion object {
        private val converter = T2SConverter()

        @BeforeClass @JvmStatic
        fun load() {
            File("src/main/assets/t2s/t2s_map.tsv").bufferedReader().use {
                converter.load(it)
            }
        }
    }

    @Test
    fun `table loads`() {
        assertTrue(converter.isLoaded)
    }

    @Test
    fun `common phrases convert`() {
        assertEquals("听日见", converter.convert("聽日見"))
        assertEquals("为什么", converter.convert("為什麼"))
        assertEquals("发现", converter.convert("發現"))
        assertEquals("头发", converter.convert("頭髮"))
        assertEquals("台湾", converter.convert("臺灣"))
        assertEquals("千钧一发", converter.convert("千鈞一髮"))
    }

    @Test
    fun `phrase overrides beat char mappings`() {
        // 乾 → 干 char-level, but the identity phrase keeps 乾隆 intact.
        assertEquals("乾隆皇帝", converter.convert("乾隆皇帝"))
        assertEquals("干杯", converter.convert("乾杯"))
        assertEquals("一目了然", converter.convert("一目瞭然"))
    }

    @Test
    fun `cantonese chars pass through`() {
        assertEquals("嘅冇喺哋", converter.convert("嘅冇喺哋"))
        assertEquals("唔该晒", converter.convert("唔該晒"))
    }

    @Test
    fun `latin and punctuation untouched`() {
        assertEquals("hello world!", converter.convert("hello world!"))
        assertEquals("send返", converter.convert("send返"))
        assertEquals("，。？！", converter.convert("，。？！"))
    }

    @Test
    fun `conversion preserves length`() {
        // The commit pipeline records traditional lengths for backspace reverts,
        // so every conversion must be same-length.
        for (t in listOf("聽日見", "一目瞭然", "乾隆皇帝", "為咗頭髮", "廣州話")) {
            assertEquals(t.length, converter.convert(t).length)
        }
    }

    @Test
    fun `unloaded converter is a no-op`() {
        val empty = T2SConverter()
        assertFalse(empty.isLoaded)
        assertEquals("聽日見", empty.convert("聽日見"))
    }
}
