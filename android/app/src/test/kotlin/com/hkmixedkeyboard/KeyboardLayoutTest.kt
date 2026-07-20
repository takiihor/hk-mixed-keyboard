package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.KeyboardLayout
import com.hkmixedkeyboard.ui.KeyboardSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `text action row uses the requested key widths`() {
        val actionRow = KeyboardLayout.rows[4].keys

        assertEquals(1.05f, actionRow.single { it.label == KeyboardLayout.KEY_MODE }.widthUnits)
        assertEquals(3.50f, actionRow.single { it.label == KeyboardLayout.KEY_SPACE }.widthUnits)
        assertEquals(1.05f, actionRow.single { it.label == KeyboardLayout.KEY_PERIOD }.widthUnits)
        assertEquals(1.05f, actionRow.single { it.label == KeyboardLayout.KEY_COMMA }.widthUnits)
    }

    @Test
    fun `every editor layout omits accidental settings and next IME actions`() {
        KeyboardSurface.entries.forEach { surface ->
            val rows = KeyboardLayout.rowsFor(surface, showNextInputMethod = true)
            val labels = rows.flatMap { row -> row.keys.map { it.label } }

            assertFalse("$surface must not expose Settings", labels.contains(KeyboardLayout.KEY_SETTINGS))
            assertFalse("$surface must not expose next IME", labels.contains(KeyboardLayout.KEY_NEXT_IME))
            if (surface != KeyboardSurface.NUMERIC_PASSWORD) {
                assertEquals(
                    "$surface action row width",
                    KeyboardLayout.GRID_WIDTH_UNITS,
                    rows.last().keys.sumOf { it.widthUnits.toDouble() }.toFloat(),
                    0.001f
                )
            }
        }
    }

    @Test
    fun `numeric password uses a centered four-row PIN grid`() {
        val rows = KeyboardLayout.rowsFor(
            KeyboardSurface.NUMERIC_PASSWORD,
            showNextInputMethod = false
        )

        assertEquals(
            listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("0", KeyboardLayout.KEY_BACKSPACE)
            ),
            rows.map { row -> row.keys.map { it.label } }
        )

        val cells = KeyboardLayout.buildCells(width = 1000f, height = 400f, rows = rows)
        assertEquals(100f, cells.single { it.key.label == "1" }.bounds.left, 0.001f)
        assertEquals(900f, cells.single { it.key.label == "3" }.bounds.right, 0.001f)
        assertEquals(100f, cells.single { it.key.label == "1" }.bounds.bottom, 0.001f)
        val zero = cells.single { it.key.label == "0" }.bounds
        assertEquals(100f, zero.bottom - zero.top, 0.001f)
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
    fun `all five rows use the comfortable 52dp height`() {
        assertEquals(52f, KeyboardLayout.BASE_ROW_HEIGHT_DP)
        assertEquals(
            listOf(1f, 1f, 1f, 1f, 1f),
            KeyboardLayout.rows.map { it.heightWeight }
        )

        val cells = KeyboardLayout.buildCells(width = 410f, height = 260f)
        assertEquals(52f, cells.single { it.key.label == "1" }.bounds.bottom, 0.001f)
        assertEquals(
            52f,
            cells.single { it.key.label == "Q" }.bounds.bottom -
                cells.single { it.key.label == "Q" }.bounds.top,
            0.001f
        )
    }

    @Test
    fun `compact geometry preserves representative key widths and positions`() {
        val cells = KeyboardLayout.buildCells(width = 1000f, height = 240f)
        val q = cells.single { it.key.label == "Q" }
        val a = cells.single { it.key.label == "A" }
        val mode = cells.single { it.key.label == KeyboardLayout.KEY_MODE }
        val space = cells.single { it.key.label == KeyboardLayout.KEY_SPACE }
        val period = cells.single { it.key.label == KeyboardLayout.KEY_PERIOD }
        val comma = cells.single { it.key.label == KeyboardLayout.KEY_COMMA }

        assertEquals(100f, q.bounds.right - q.bounds.left, 0.001f)
        assertEquals(50f, a.bounds.left, 0.001f)
        assertEquals(105f, mode.bounds.right - mode.bounds.left, 0.001f)
        assertEquals(350f, space.bounds.right - space.bounds.left, 0.001f)
        assertEquals(105f, period.bounds.right - period.bounds.left, 0.001f)
        assertEquals(105f, comma.bounds.right - comma.bounds.left, 0.001f)
    }

    @Test
    fun `fixed accessible candidate bar plus comfortable keyboard reserves 308dp`() {
        val density = 2f
        assertEquals((260f * density).toInt(), KeyboardLayout.keyboardHeightPx(density))
        assertEquals((308f * density).toInt(), KeyboardLayout.inputViewMinHeightPx(density))
    }
}
