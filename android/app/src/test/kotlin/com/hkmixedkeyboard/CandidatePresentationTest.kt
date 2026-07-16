package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
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

    @Test
    fun `hides Jyutping candidate annotation when readings are disabled`() {
        assertEquals(
            "係",
            CandidatePresentation.label(
                candidate = jyutpingCandidate(),
                hasGlyph = { true },
                showJyutpingReadings = false
            )
        )
    }

    @Test
    fun `shows Jyutping candidate annotation when readings are enabled`() {
        assertEquals(
            "係 · hai6",
            CandidatePresentation.label(
                candidate = jyutpingCandidate(),
                hasGlyph = { true },
                showJyutpingReadings = true
            )
        )
    }

    @Test
    fun `keeps non Jyutping annotations visible when readings are disabled`() {
        val pinyinCorrection = jyutpingCandidate().copy(
            sourceSchema = SourceSchema.PINYIN_CORRECTION,
            annotation = "↪ hai"
        )

        assertEquals(
            "係 · ↪ hai",
            CandidatePresentation.label(
                candidate = pinyinCorrection,
                hasGlyph = { true },
                showJyutpingReadings = false
            )
        )
    }

    private fun jyutpingCandidate() = DecodeCandidate(
        text = "係",
        code = "hai6",
        sourceSchema = SourceSchema.JYUTPING,
        type = CandidateType.CHAR,
        frequency = 1.0,
        isHkCore = true,
        annotation = "hai6"
    )
}
