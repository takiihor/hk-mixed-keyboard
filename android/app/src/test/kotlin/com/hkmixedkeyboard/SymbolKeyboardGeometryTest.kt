package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.SymbolKeyboardGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolKeyboardGeometryTest {

    @Test
    fun `every symbol row has ten contiguous non overlapping hit cells at narrow width`() {
        val geometry = SymbolKeyboardGeometry.layout(widthPx = 320f, heightPx = 280f, density = 1f)

        assertEquals(4, geometry.symbolRows.size)
        geometry.symbolRows.forEach { row ->
            assertEquals(10, row.size)
            row.zipWithNext().forEach { (left, right) ->
                assertEquals(left.hitRect.right, right.hitRect.left, 0.001f)
                assertTrue(left.visualRect.right < right.visualRect.left)
            }
        }
    }

    @Test
    fun `narrow layout reduces visible gaps before reducing text scale`() {
        val regular = SymbolKeyboardGeometry.layout(widthPx = 480f, heightPx = 280f, density = 1f)
        val narrow = SymbolKeyboardGeometry.layout(widthPx = 240f, heightPx = 280f, density = 1f)

        assertTrue(narrow.horizontalGapPx < regular.horizontalGapPx)
        assertEquals(1f, narrow.symbolTextScale, 0.001f)
    }

    @Test
    fun `function key hit cells are contiguous and preserve the specified relative weights`() {
        val geometry = SymbolKeyboardGeometry.layout(widthPx = 420f, heightPx = 280f, density = 1f)
        val functionCells = geometry.functionRow

        assertEquals(5, functionCells.size)
        functionCells.zipWithNext().forEach { (left, right) ->
            assertEquals(left.hitRect.right, right.hitRect.left, 0.001f)
        }
        assertEquals(
            1.25f / 0.90f,
            functionCells[0].hitRect.width / functionCells[1].hitRect.width,
            0.001f
        )
    }
}
