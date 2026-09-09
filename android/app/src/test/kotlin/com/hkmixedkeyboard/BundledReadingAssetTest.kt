package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.ReadingLookup
import com.hkmixedkeyboard.ui.ReadingHintPolicy
import com.hkmixedkeyboard.ui.ReadingHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the two shipped learning assets themselves: a regenerated, renamed, or
 * truncated CSV would otherwise silently turn the hints off in the running app,
 * because every lookup miss degrades to "no hint".
 */
class BundledReadingAssetTest {

    private val corpusDir = "src/main/assets/corpus"

    private fun load(name: String): ReadingLookup =
        File("$corpusDir/$name").bufferedReader().use { ReadingLookup.from(it) }

    private val jyutping by lazy { load("jyutping_readings.csv") }
    private val pinyin by lazy { load("pinyin_readings.csv") }

    @Test
    fun `bundled Jyutping readings carry tones and syllable spaces`() {
        assertEquals("hoeng1 gong2", jyutping.readingFor("香港"))
        assertEquals("nei5", jyutping.readingFor("你"))
    }

    @Test
    fun `bundled Pinyin readings carry tones and syllable spaces`() {
        assertEquals("xiang1 gang3", pinyin.readingFor("香港"))
        assertEquals("ni3", pinyin.readingFor("你"))
    }

    @Test
    fun `a polyphonic character teaches the reading its words actually use`() {
        // Resolved by how the words containing the character read, not by
        // whichever reading CC-CEDICT happened to list first.
        assertEquals("xing2", pinyin.readingFor("行"))
        assertEquals("zhong4", pinyin.readingFor("重"))
        assertEquals("le4", pinyin.readingFor("樂"))
        assertEquals("jue2", pinyin.readingFor("覺"))
    }

    @Test
    fun `a word keeps its own exact reading regardless of its characters`() {
        assertEquals("yin2 hang2", pinyin.readingFor("銀行"))
        assertEquals("shui4 jiao4", pinyin.readingFor("睡覺"))
    }

    @Test
    fun `Pinyin readings spell the u umlaut rather than the ASCII digraph`() {
        assertEquals("lü4", pinyin.readingFor("綠"))
        assertEquals("nü3", pinyin.readingFor("女"))
    }

    @Test
    fun `both hints render on one line from the shipped assets`() {
        val policy = ReadingHintPolicy(jyutping, pinyin)

        assertEquals(
            "香港 · 粵 hoeng1 gong2 · 拼 xiāng gǎng",
            policy.liveLabel(
                ReadingHints(jyutping = true, pinyin = true),
                listOf(cnPhrase("香港", "theng"))
            )
        )
    }

    @Test
    fun `Cantonese-only vocabulary has a Jyutping reading with no Mandarin one`() {
        // 唔 is everyday Cantonese and absent from CC-CEDICT's Mandarin readings.
        // The 粵拼 hint must still show rather than being suppressed by the miss.
        assertTrue(jyutping.readingFor("唔") != null)
        val policy = ReadingHintPolicy(jyutping, pinyin)
        val label = policy.liveLabel(
            ReadingHints(jyutping = true, pinyin = true),
            listOf(cnChar("唔", "rr"))
        )
        assertTrue("expected a 粵 reading in $label", label!!.contains("粵 "))
    }
}
