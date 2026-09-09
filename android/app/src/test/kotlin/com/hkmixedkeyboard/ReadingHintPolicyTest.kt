package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.ReadingLookup
import com.hkmixedkeyboard.ui.CantoneseNotation
import com.hkmixedkeyboard.ui.ReadingHintPolicy
import com.hkmixedkeyboard.ui.ReadingHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingHintPolicyTest {

    private val policy = ReadingHintPolicy(
        jyutping = ReadingLookup.from(
            "text,jyutping\n香港,hoeng1 gong2\n我,ngo5\n你,nei5\n乜嘢,mat1 je5\n".reader()
        ),
        pinyin = ReadingLookup.from(
            "text,pinyin\n香港,xiang1 gang3\n我,wo3\n你,ni3\n".reader()
        )
    )

    private val jyutpingOnly = ReadingHints(jyutping = true, pinyin = false)
    private val pinyinOnly = ReadingHints(jyutping = false, pinyin = true)
    private val both = ReadingHints(jyutping = true, pinyin = true)

    @Test
    fun `live hint uses first Chinese candidate after English literal`() {
        assertEquals(
            "香港 · 粵 hoeng1 gong2",
            policy.liveLabel(
                jyutpingOnly,
                listOf(enLiteralCand("hk"), cnPhrase("香港", "theng"))
            )
        )
    }

    @Test
    fun `pinyin hint alone shows only the Mandarin reading`() {
        assertEquals(
            "香港 · 拼 xiāng gǎng",
            policy.liveLabel(pinyinOnly, listOf(cnPhrase("香港", "theng")))
        )
    }

    @Test
    fun `both hints share one labelled line, Jyutping first`() {
        assertEquals(
            "香港 · 粵 hoeng1 gong2 · 拼 xiāng gǎng",
            policy.liveLabel(both, listOf(cnPhrase("香港", "theng")))
        )
    }

    @Test
    fun `a reading missing from one romanization does not suppress the other`() {
        assertEquals(
            "乜嘢 · 粵 mat1 je5",
            policy.liveLabel(both, listOf(cnPhrase("乜嘢", "th")))
        )
    }

    @Test
    fun `no hint is shown when both toggles are off`() {
        assertNull(policy.liveLabel(ReadingHints.NONE, listOf(cnPhrase("香港", "theng"))))
        assertNull(policy.committedLabel(ReadingHints.NONE, cnChar("我", "hqi")))
    }

    @Test
    fun `missing exact phrase reading is not fabricated from its characters`() {
        assertNull(policy.liveLabel(both, listOf(cnPhrase("你我", "onhqi"))))
    }

    @Test
    fun `committed hint uses tapped candidate instead of leading candidate`() {
        val me = cnChar("我", "hqi")
        val you = cnChar("你", "on")

        assertEquals("我 · 粵 ngo5", policy.liveLabel(jyutpingOnly, listOf(me, you)))
        assertEquals("你 · 粵 nei5", policy.committedLabel(jyutpingOnly, you))
    }

    // ── Notation ──────────────────────────────────────────────────────────

    @Test
    fun `Pinyin is shown with the diacritics schools teach, not tone numbers`() {
        assertEquals(
            "香港 · 拼 xiāng gǎng",
            policy.liveLabel(pinyinOnly, listOf(cnPhrase("香港", "haeu")))
        )
    }

    @Test
    fun `Yale spells the same reading and is labelled as Yale`() {
        val yale = ReadingHints(jyutping = true, cantonese = CantoneseNotation.YALE)

        assertEquals(
            "香港 · 耶 hēung góng",
            policy.liveLabel(yale, listOf(cnPhrase("香港", "haeu")))
        )
    }

    @Test
    fun `both notations can appear together`() {
        val yaleAndPinyin = ReadingHints(
            jyutping = true, pinyin = true, cantonese = CantoneseNotation.YALE
        )

        assertEquals(
            "香港 · 耶 hēung góng · 拼 xiāng gǎng",
            policy.liveLabel(yaleAndPinyin, listOf(cnPhrase("香港", "haeu")))
        )
    }

    @Test
    fun `Jyutping remains the default spelling`() {
        assertEquals(CantoneseNotation.JYUTPING, ReadingHints().cantonese)
        assertEquals(
            "我 · 粵 ngo5",
            policy.liveLabel(jyutpingOnly, listOf(cnChar("我", "hqi")))
        )
    }

    @Test
    fun `English literal has no hint`() {
        assertNull(policy.liveLabel(both, listOf(enLiteralCand("hello"))))
        assertNull(policy.committedLabel(both, enLiteralCand("hello")))
    }
}
