package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.JyutpingReadingLookup
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.ui.JyutpingLearningPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JyutpingLearningPreviewTest {

    private val preview = JyutpingLearningPreview(
        JyutpingReadingLookup.from(
            "text,jyutping\n香港,hoeng1 gong2\n我,ngo5\n你,nei5\n".reader()
        )
    )

    @Test
    fun `live preview uses first Chinese candidate after English literal`() {
        assertEquals(
            "香港 · hoeng1 gong2",
            preview.liveLabel(
                Scheme.QUICK,
                listOf(enLiteralCand("hk"), cnPhrase("香港", "theng"))
            )
        )
    }

    @Test
    fun `live preview is only shown in Quick mode`() {
        assertNull(preview.liveLabel(Scheme.JYUTPING, listOf(cnChar("你", "on"))))
        assertNull(preview.liveLabel(Scheme.PINYIN, listOf(cnChar("你", "on"))))
    }

    @Test
    fun `missing exact phrase reading is not fabricated`() {
        assertNull(preview.liveLabel(Scheme.QUICK, listOf(cnPhrase("你我", "onhqi"))))
    }

    @Test
    fun `committed preview uses tapped candidate instead of leading candidate`() {
        val me = cnChar("我", "hqi")
        val you = cnChar("你", "on")

        assertEquals("我 · ngo5", preview.liveLabel(Scheme.QUICK, listOf(me, you)))
        assertEquals("你 · nei5", preview.committedLabel(Scheme.QUICK, you))
    }

    @Test
    fun `English literal has no committed preview`() {
        assertNull(preview.committedLabel(Scheme.QUICK, enLiteralCand("hello")))
    }
}
