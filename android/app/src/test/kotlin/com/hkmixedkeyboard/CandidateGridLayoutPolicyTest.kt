package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.CandidateGridLayoutPolicy
import com.hkmixedkeyboard.ui.CandidateGridSizeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CandidateGridLayoutPolicyTest {

    @Test
    fun `grid fills viewport and cells divide it into four equal zero-base columns`() {
        val spec = CandidateGridLayoutPolicy.layoutSpec()

        assertEquals(4, spec.columnCount)
        assertEquals(0, spec.cellBaseWidth)
        assertEquals(1f, spec.cellColumnWeight)
        assertEquals(CandidateGridSizeMode.MATCH_PARENT, spec.gridWidthMode)
        assertNotEquals(CandidateGridSizeMode.WRAP_CONTENT, spec.gridWidthMode)
        assertEquals(CandidateGridSizeMode.WRAP_CONTENT, spec.gridHeightMode)
    }
}
