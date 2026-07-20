package com.hkmixedkeyboard

import android.content.Context
import android.content.ContextWrapper
import com.hkmixedkeyboard.decoder.CorpusBackedDecoder
import com.hkmixedkeyboard.decoder.CorpusLoader
import com.hkmixedkeyboard.decoder.EnglishAssistEntry
import com.hkmixedkeyboard.decoder.JyutpingEntry
import com.hkmixedkeyboard.decoder.Scheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JyutpingRankingRegressionTest {
    private val expectedTop = linkedMapOf(
        "hai" to "喺",
        "sik" to "食",
        "mou" to "冇",
        "zo" to "咗",
        "dei" to "哋",
        "keoi" to "佢",
        "gam" to "咁",
        "ngodei" to "我哋",
        "keoidei" to "佢哋",
        "haibin" to "喺邊",
        "haido" to "喺度",
        "moumantai" to "冇問題",
        "neihou" to "你好",
        "mgoi" to "唔該",
        "dimgaai" to "點解",
        "taihei" to "睇戲",
        "msai" to "唔使",
        "jigaa" to "而家",
        "kamjat" to "琴日",
        "gamjat" to "今日"
    )

    // Direct phrases whose toneless continuous key previously produced the wrong
    // synthesis (sikfaan -> 式飯, tingjat -> 聽一). The exact override must win.
    private val synthesisFixes = linkedMapOf(
        "sikfaan" to "食飯",
        "tingjat" to "聽日"
    )
    private val decoder by lazy { productionDecoder() }

    @Test
    fun `reviewed Cantonese candidates rank Top-1`() {
        expectedTop.forEach { (input, expected) ->
            assertEquals(input, expected, candidates(input).first())
        }
    }

    @Test
    fun `profanity remains available but never ranks Top-1 for hai`() {
        val ranked = candidates("hai")

        assertEquals("喺", ranked.first())
        assertNotEquals("閪", ranked.first())
        assertNotEquals("屄", ranked.first())
        assertTrue(ranked.contains("閪"))
    }

    @Test
    fun `explicit tones rank the matching reading and annotate mismatches truthfully`() {
        val hai6 = decoder.decode("hai6", Scheme.JYUTPING)
        assertEquals("係", hai6.candidates.first().text)
        assertEquals("hai6", hai6.candidates.first().annotation)
        assertTrue(hai6.candidates.single { it.text == "閪" }.annotation!!.contains("hai1"))

        assertEquals("喺", candidates("hai2").first())
        assertEquals("閪", candidates("hai1").first())
    }

    @Test
    fun `direct phrases fix toneless continuous-synthesis errors`() {
        synthesisFixes.forEach { (input, expected) ->
            assertEquals(
                "$input must exact-match the reviewed phrase, not a synthesis miss",
                expected,
                candidates(input).first()
            )
        }
    }

    @Test
    fun `reviewed ranking benchmark has perfect Top-1 accuracy`() {
        val hits = expectedTop.count { (input, expected) ->
            candidates(input).firstOrNull() == expected
        }
        val accuracy = hits.toDouble() / expectedTop.size

        assertEquals(1.0, accuracy, 0.0)
    }

    @Test
    fun `reviewed abbreviations are available but tap-only`() {
        val result = decoder.decode("nh", Scheme.JYUTPING)
        assertTrue(result.candidates.any { it.text == "你好" })
        assertTrue(!result.isExactCode)
    }

    private fun candidates(input: String): List<String> =
        decoder.decode(input, Scheme.JYUTPING).candidates.map { it.text }

    private fun productionDecoder(): CorpusBackedDecoder {
        val context = unsafe.javaClass
            .getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, ContextWrapper::class.java) as Context
        val loader = CorpusLoader(context)
        val rows = (
            readJyutping("src/main/assets/corpus/jyutping.csv") +
                readJyutping("src/main/assets/corpus/jyutping_overrides.csv")
            ).map { (jyutping, chinese, frequency) ->
                JyutpingEntry(jyutping, chinese, frequency)
            }
        val field = CorpusLoader::class.java.getDeclaredField("jyutping\$delegate")
        field.isAccessible = true
        field.set(loader, lazyOf(rows))
        val englishAssistField = CorpusLoader::class.java.getDeclaredField("englishAssist\$delegate")
        englishAssistField.isAccessible = true
        englishAssistField.set(loader, lazyOf(emptyList<EnglishAssistEntry>()))
        val tonalField = CorpusLoader::class.java.getDeclaredField(
            "jyutpingTonalReadingsByText\$delegate"
        )
        tonalField.isAccessible = true
        tonalField.set(loader, lazyOf(readTonalReadings()))
        return CorpusBackedDecoder(loader)
    }

    private fun readTonalReadings(): Map<String, List<String>> =
        File("../../corpus/reference/jyutping_tonal_readings.csv").useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .drop(1)
                .mapNotNull { line ->
                    val columns = line.split(',', limit = 3)
                    if (columns.size < 3) null else columns[0] to columns[2].split(' ')
                }
                .toMap()
        }

    private companion object {
        val unsafe: Any by lazy {
            Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").run {
                isAccessible = true
                get(null)
            }
        }
    }
}
