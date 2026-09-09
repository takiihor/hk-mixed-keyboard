package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.CandidatePresentation
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidatePresentationTest {

    @Test
    fun `uses a Unicode code point label when a supplementary glyph is unavailable`() {
        assertEquals(
            "U+2003E",
            CandidatePresentation.label("𠀾") { false }
        )
    }

    @Test
    fun `keeps supported text and replaces only unsupported code points`() {
        assertEquals(
            "搭 U+282E2 出",
            CandidatePresentation.label("搭𨋢出") { glyph -> glyph != "𨋢" }
        )
    }

    @Test
    fun `preserves the normal label when every glyph is supported`() {
        assertEquals(
            "香港",
            CandidatePresentation.label("香港") { true }
        )
    }
}
