package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.JyutpingNormalizer
import com.hkmixedkeyboard.decoder.JyutpingToneIndex
import com.hkmixedkeyboard.decoder.JyutpingToneMatch
import org.junit.Assert.assertEquals
import org.junit.Test

class JyutpingToneIndexTest {
    private val index = JyutpingToneIndex(
        mapOf(
            "係" to listOf("hai6"),
            "喺" to listOf("hai2"),
            "閪" to listOf("hai1"),
            "你" to listOf("nei5"),
            "好" to listOf("hou2")
        )
    )

    @Test
    fun `explicit tone distinguishes homophones`() {
        val input = JyutpingNormalizer.normalize("hai6")!!

        assertEquals(JyutpingToneMatch.MATCH, index.classify("係", input))
        assertEquals(JyutpingToneMatch.MISMATCH, index.classify("喺", input))
        assertEquals(JyutpingToneMatch.MISMATCH, index.classify("閪", input))
    }

    @Test
    fun `phrase tones align by code point including partial tones`() {
        assertEquals(
            JyutpingToneMatch.MATCH,
            index.classify("你好", JyutpingNormalizer.normalize("nei5hou2")!!)
        )
        assertEquals(
            JyutpingToneMatch.MATCH,
            index.classify("你好", JyutpingNormalizer.normalize("nei hou2")!!)
        )
    }

    @Test
    fun `toneless input leaves the corpus ranking unchanged`() {
        assertEquals(
            JyutpingToneMatch.UNKNOWN,
            index.classify("喺", JyutpingNormalizer.normalize("hai")!!)
        )
    }
}
