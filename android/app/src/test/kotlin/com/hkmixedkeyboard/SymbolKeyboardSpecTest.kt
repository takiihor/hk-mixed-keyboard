package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.SymbolKeyboardSpec
import com.hkmixedkeyboard.ui.SymbolPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolKeyboardSpecTest {

    @Test
    fun `common page keeps the approved Traditional Chinese punctuation matrix`() {
        assertEquals(
            listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("，", "。", "？", "！", "、", "：", "；", "…", "—", "·"),
                listOf("「", "」", "『", "』", "“", "”", "‘", "’", "《", "》"),
                listOf("@", "#", "$", "%", "&", "*", "-", "+", "=", "_")
            ),
            SymbolKeyboardSpec.page(SymbolPage.COMMON).rows.map { row -> row.map { it.commitText } }
        )
    }

    @Test
    fun `extended page keeps the approved technical symbol matrix`() {
        assertEquals(
            listOf(
                listOf("(", ")", "[", "]", "{", "}", "<", ">", "/", "\\"),
                listOf("~", "`", "^", "|", "\\", "°", "•", "©", "®", "™"),
                listOf("±", "×", "÷", "≠", "≈", "≤", "≥", "√", "∞", "%"),
                listOf("€", "£", "¥", "₩", "₹", "¢", "§", "¶", "#", "@")
            ),
            SymbolKeyboardSpec.page(SymbolPage.EXTENDED).rows.map { row -> row.map { it.commitText } }
        )
    }

    @Test
    fun `each page has four rows of ten exact Unicode keys`() {
        SymbolPage.entries.forEach { page ->
            assertEquals(listOf(10, 10, 10, 10), SymbolKeyboardSpec.page(page).rows.map { it.size })
        }
    }

    @Test
    fun `page key labels and dots identify the active page`() {
        assertEquals("1/2", SymbolKeyboardSpec.pageKey(SymbolPage.COMMON).label)
        assertEquals(listOf(true, false), SymbolKeyboardSpec.pageKey(SymbolPage.COMMON).pageIndicator)
        assertEquals("#+=", SymbolKeyboardSpec.pageKey(SymbolPage.EXTENDED).label)
        assertEquals(listOf(false, true), SymbolKeyboardSpec.pageKey(SymbolPage.EXTENDED).pageIndicator)
    }

    @Test
    fun `long press alternatives keep exact Unicode including escaped backslash`() {
        // Brackets moved to page 2 when the digits took the first row, so look
        // across both pages rather than assuming which one a symbol sits on.
        fun alternatives(symbol: String) = (
            SymbolKeyboardSpec.page(SymbolPage.COMMON).rows +
                SymbolKeyboardSpec.page(SymbolPage.EXTENDED).rows
            )
            .flatten()
            .first { it.commitText == symbol }
            .longPressAlternatives
            .map { it.commitText }

        assertEquals(listOf(",", "、", ";", "："), alternatives("，"))
        assertEquals(listOf(".", "·", "…", "．"), alternatives("。"))
        assertEquals(listOf("?"), alternatives("？"))
        assertEquals(listOf("!"), alternatives("！"))
        assertEquals(listOf("\"", "„", "«"), alternatives("“"))
        assertEquals(listOf("\"", "»"), alternatives("”"))
        assertEquals(listOf("'", "`"), alternatives("‘"))
        assertEquals(listOf("'"), alternatives("’"))
        assertEquals(listOf("–", "—", "−"), alternatives("-"))
        assertEquals(listOf("€", "£", "¥", "₩", "₹", "¢"), alternatives("$"))
        assertEquals(listOf("[", "{", "<", "（", "〔", "【"), alternatives("("))
        assertEquals(listOf("]", "}", ">", "）", "〕", "】"), alternatives(")"))
        assertEquals(listOf("〈", "《", "≤"), alternatives("<"))
        assertEquals(listOf("〉", "》", "≥"), alternatives(">"))
        assertEquals(listOf("\\", "|", "÷"), alternatives("/"))
        // Digits reach their superscript, circled and Chinese forms.
        assertEquals(listOf("¹", "①", "一"), alternatives("1"))
        assertEquals(listOf("⁰", "○", "零"), alternatives("0"))
    }

    @Test
    fun `semantic labels describe Chinese punctuation and page action`() {
        val common = SymbolKeyboardSpec.page(SymbolPage.COMMON).rows.flatten()
        assertEquals("中文逗號", common.single { it.commitText == "，" }.accessibilityLabel)
        assertEquals("中文句號", common.single { it.commitText == "。" }.accessibilityLabel)
        assertEquals("頓號", common.single { it.commitText == "、" }.accessibilityLabel)
        assertEquals("左單引號", common.single { it.commitText == "「" }.accessibilityLabel)
        assertEquals("右單引號", common.single { it.commitText == "」" }.accessibilityLabel)
        assertEquals("左書名號", common.single { it.commitText == "《" }.accessibilityLabel)
        assertEquals("右書名號", common.single { it.commitText == "》" }.accessibilityLabel)
        assertEquals("切換至符號第 2 頁", SymbolKeyboardSpec.pageKey(SymbolPage.COMMON).accessibilityLabel)
    }

    @Test
    fun `long press alternatives are discoverable through the semantic description`() {
        // Row 1 is the digits now; the comma leads row 2.
        val comma = SymbolKeyboardSpec.page(SymbolPage.COMMON).rows[1].first()

        assertTrue(SymbolKeyboardSpec.accessibilityDescription(comma).contains("長按可選"))
        assertTrue(SymbolKeyboardSpec.accessibilityDescription(comma).contains("英文逗號"))
    }

    @Test
    fun `bottom key weights match the approved relative widths`() {
        assertEquals(
            listOf(1.25f, 0.90f, 2.60f, 1.05f, 1.20f),
            SymbolKeyboardSpec.bottomKeyWeights
        )
    }
}
