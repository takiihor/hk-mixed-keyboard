package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.PinyinDecoder
import com.hkmixedkeyboard.decoder.PinyinEntry
import com.hkmixedkeyboard.decoder.PinyinLexicon
import com.hkmixedkeyboard.util.Csv
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PinyinMarketGateTest {
    private val decoder by lazy {
        val entries = File("src/main/assets/corpus/pinyin.csv").useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .drop(1)
                .map(Csv::split)
                .filter { it.size >= 3 }
                .map { PinyinEntry(it[0], it[1], it[2].toDouble()) }
                .toList()
        }
        PinyinDecoder(PinyinLexicon(entries))
    }

    @Test
    fun `common Traditional-HK phrases are reachable within Top-3`() {
        linkedMapOf(
            "nihao" to "你好", "xiexie" to "謝謝", "weishenme" to "為什麼",
            "zhongguo" to "中國", "xianggang" to "香港", "putonghua" to "普通話"
        ).forEach { (input, expected) ->
            assertTrue(
                "$input must surface $expected",
                decoder.decode(input).candidates.take(3).any { it.text == expected }
            )
        }
    }

    @Test
    fun `correction is tap-only and strict mode remains unchanged`() {
        assertTrue(decoder.decode("zhongguo").isExactCode)
        val correction = decoder.decode("zjongguo")
        assertFalse(correction.isExactCode)
        assertEquals("zhongguo", correction.candidates.first().code)
        assertEquals("↪ zhongguo", correction.candidates.first().annotation)
    }
}
