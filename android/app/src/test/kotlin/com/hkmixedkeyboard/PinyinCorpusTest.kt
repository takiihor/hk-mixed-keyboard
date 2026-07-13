package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.PinyinDecoder
import com.hkmixedkeyboard.decoder.PinyinEntry
import com.hkmixedkeyboard.decoder.PinyinLexicon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PinyinCorpusTest {
    private val corpusFile = File("src/main/assets/corpus/pinyin.csv")

    private val decoder by lazy {
        PinyinDecoder(PinyinLexicon(readPinyin(corpusFile)))
    }

    @Test
    fun `representative exact words and phrases decode in Traditional Chinese`() {
        assertEquals("貓", decoder.decode("mao").candidates.first().text)
        assertTrue(decoder.decode("nihao").candidates.any { it.text == "你好" })
        assertTrue(decoder.decode("xianggang").candidates.any { it.text == "香港" })
        assertTrue(decoder.decode("putonghua").candidates.any { it.text == "普通話" })
    }

    @Test
    fun `exact syllable appends common longer phrases beyond lexical neighbors`() {
        val candidates = decoder.decode("xiang").candidates

        assertEquals("想", candidates.first().text)
        assertTrue(candidates.any { it.text == "香港" })
    }

    @Test
    fun `segmentable input composes before appending longer dictionary prefixes`() {
        val result = decoder.decode("tianan")

        assertTrue(result.isExactCode)
        assertTrue(result.cnHasPhraseMatch)
        assertTrue(result.candidates.size >= 2)
        assertEquals("天安", result.candidates.first().text)
        assertTrue(result.candidates.drop(1).any { it.text == "天安門" })
    }

    @Test
    fun `real asset excludes noncanonical placeholder reading`() {
        assertTrue(decoder.decode("xx").candidates.isEmpty())
        assertTrue(decoder.decode("xxxx").candidates.isEmpty())
    }

    @Test
    fun `polyphonic character is reachable by each reading`() {
        assertTrue(decoder.decode("xing").candidates.any { it.text == "行" })
        assertTrue(decoder.decode("hang").candidates.any { it.text == "行" })
    }

    @Test
    fun `runtime candidates never expose Latin dictionary headwords`() {
        assertTrue(decoder.decode("p").candidates.none { it.text == "P" })
        assertTrue(decoder.decode("pq").candidates.none { candidate ->
            candidate.text.codePoints().anyMatch { codePoint ->
                Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.HAN
            }
        })
    }

    private fun readPinyin(file: File): List<PinyinEntry> {
        val rows = ArrayList<PinyinEntry>()
        var headerSkipped = false
        file.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEachLine
            if (!headerSkipped) {
                headerSkipped = true
                return@forEachLine
            }
            val columns = trimmed.split(',')
            if (columns.size >= 3) {
                rows += PinyinEntry(columns[0], columns[1], columns[2].toDouble())
            }
        }
        return rows
    }
}
