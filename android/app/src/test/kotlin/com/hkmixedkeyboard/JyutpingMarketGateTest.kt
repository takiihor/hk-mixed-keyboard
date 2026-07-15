package com.hkmixedkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JyutpingMarketGateTest {
    private val rows by lazy {
        (readJyutping("src/main/assets/corpus/jyutping.csv") +
            readJyutping("src/main/assets/corpus/jyutping_overrides.csv"))
            .groupBy { it.first }
    }

    @Test
    fun `reputation-sensitive and common Cantonese intents are safe Top-1`() {
        val expected = linkedMapOf(
            "hai" to "喺", "sik" to "食", "dei" to "哋", "gam" to "咁",
            "ngodei" to "我哋", "keoidei" to "佢哋", "neihou" to "你好",
            "mgoi" to "唔該", "tingjat" to "聽日", "dimgaai" to "點解",
            "sikfaan" to "食飯"
        )
        expected.forEach { (code, text) ->
            val top = rows[code].orEmpty().maxByOrNull { it.third }?.second
            assertEquals(code, text, top)
        }
        assertNotEquals("閪", rows["hai"].orEmpty().maxByOrNull { it.third }?.second)
        assertTrue(rows["hai"].orEmpty().any { it.second == "閪" })
    }

    @Test
    fun `all Jyutping asset rows have bounded canonical codes and Unicode-safe text`() {
        rows.forEach { (code, entries) ->
            assertTrue(code.matches(Regex("[a-z]+")))
            assertTrue(code.length <= 72)
            entries.forEach { (_, text, _) ->
                assertTrue(text.isNotEmpty())
                assertTrue(text.codePointCount(0, text.length) >= 1)
            }
        }
    }
}
