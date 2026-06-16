package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.KeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardLayoutTest {

    @Test
    fun `layout has the requested five rows`() {
        assertEquals(5, KeyboardLayout.rows.size)
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            KeyboardLayout.rows[0].keys.map { it.label }
        )
        assertEquals(
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            KeyboardLayout.rows[1].keys.map { it.label }
        )
        assertEquals(
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            KeyboardLayout.rows[2].keys.map { it.label }
        )
        assertEquals(
            listOf("⇧", "Z", "X", "C", "V", "B", "N", "M", "⌫"),
            KeyboardLayout.rows[3].keys.map { it.label }
        )
        assertEquals(
            listOf("符", "😊", "⌨", " ", "。", "，", "↵"),
            KeyboardLayout.rows[4].keys.map { it.label }
        )
    }

    @Test
    fun `space bar width`() {
        val space = KeyboardLayout.rows[4].keys.single { it.label == " " }

        // Trimmed 4.0 → 3.0 to make room for the dedicated 😊 emoji key.
        assertEquals(3.0f, space.widthUnits)
    }

    @Test
    fun `scheme-switch key is present in the bottom row`() {
        assertEquals(
            1,
            KeyboardLayout.rows[4].keys.count { it.label == KeyboardLayout.KEY_MODE }
        )
    }

    @Test
    fun `full rows span the entire width`() {
        // Rows 0,1,3,4 fill 0 → 10; the home row (2) is the half-key stagger.
        for (i in listOf(0, 1, 3, 4)) {
            val first = KeyboardLayout.rows[i].keys.first()
            val last = KeyboardLayout.rows[i].keys.last()
            assertEquals("row $i left", 0f, first.startUnits)
            assertEquals("row $i right", 10f, last.startUnits + last.widthUnits)
        }
    }

    @Test
    fun `A is centered exactly between Q and W`() {
        val cells = KeyboardLayout.buildCells(width = 1000f, height = 500f)
        fun center(label: String) = cells.single { it.key.label == label }.bounds.centerX

        assertEquals((center("Q") + center("W")) / 2f, center("A"), 0.001f)
    }

    @Test
    fun `number row is shorter than typing rows`() {
        assertEquals(0.85f, KeyboardLayout.rows.first().heightWeight)
        assertEquals(1f, KeyboardLayout.rows[1].heightWeight)
    }
}
