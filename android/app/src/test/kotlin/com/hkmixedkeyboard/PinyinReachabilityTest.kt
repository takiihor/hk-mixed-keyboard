package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.ImeContext
import com.hkmixedkeyboard.commit.ImeStateData
import com.hkmixedkeyboard.commit.Thresholds
import com.hkmixedkeyboard.decoder.PinyinDecoder
import com.hkmixedkeyboard.decoder.PinyinEntry
import com.hkmixedkeyboard.decoder.PinyinLexicon
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.memory.UserMemory
import com.hkmixedkeyboard.util.Csv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PinyinReachabilityTest {

    @Test
    fun `romanization input keeps the twenty first key while Quick retains its conservative cap`() {
        val existing = "a".repeat(Thresholds.maxBufferLength(Scheme.QUICK))
        val pinyin = makeCtrl(
            memory = UserMemory(),
            ctx = ImeContext(scheme = Scheme.PINYIN)
        ).onKeyPress("b", ImeStateData(buffer = existing))
        val quick = makeCtrl(
            memory = UserMemory(),
            ctx = ImeContext(scheme = Scheme.QUICK)
        ).onKeyPress("b", ImeStateData(buffer = existing))

        assertNull(pinyin.committedText)
        assertEquals(existing + "b", pinyin.newState.buffer)
        assertEquals(existing, quick.committedText)
        assertEquals("b", quick.newState.buffer)
        assertEquals(Thresholds.JYUTPING_MAX_BUFFER_LEN, Thresholds.maxBufferLength(Scheme.JYUTPING))
    }

    @Test
    fun `Pinyin still flushes at its bounded ceiling`() {
        val maximum = Thresholds.maxBufferLength(Scheme.PINYIN)
        val existing = "a".repeat(maximum)
        val output = makeCtrl(
            memory = UserMemory(),
            ctx = ImeContext(scheme = Scheme.PINYIN)
        ).onKeyPress("b", ImeStateData(buffer = existing))

        assertEquals(72, maximum)
        assertEquals(existing, output.committedText)
        assertEquals("b", output.newState.buffer)
    }

    @Test
    fun `Jyutping retains the longest shipped key without a literal flush`() {
        val key = File("src/main/assets/corpus/jyutping.csv").useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .drop(1)
                .map(Csv::split)
                .filter { it.size >= 3 }
                .maxBy { it[0].length }
                .first()
        }
        val prefix = key.dropLast(1)
        val output = makeCtrl(
            memory = UserMemory(),
            ctx = ImeContext(scheme = Scheme.JYUTPING)
        ).onKeyPress(key.last().toString(), ImeStateData(buffer = prefix))

        assertTrue(key.length > Thresholds.MAX_BUFFER_LEN)
        assertTrue(key.length <= Thresholds.maxBufferLength(Scheme.JYUTPING))
        assertNull(output.committedText)
        assertEquals(key, output.newState.buffer)
    }

    @Test
    fun `longest shipped Pinyin key fits the composition ceiling and remains exact`() {
        val row = File("src/main/assets/corpus/pinyin.csv").useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .drop(1)
                .map(Csv::split)
                .filter { it.size >= 3 }
                .maxBy { it[0].length }
        }
        val entry = PinyinEntry(row[0], row[1], row[2].toDouble())
        val result = PinyinDecoder(PinyinLexicon(listOf(entry))).decode(entry.pinyin)

        assertTrue(entry.pinyin.length <= Thresholds.maxBufferLength(Scheme.PINYIN))
        assertTrue(result.isExactCode)
        assertFalse(result.candidates.isEmpty())
        assertEquals(entry.chinese, result.candidates.first().text)
    }
}
