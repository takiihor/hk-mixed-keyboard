package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.CharEntry
import com.hkmixedkeyboard.decoder.CorpusCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

class CorpusCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val readChar: (DataInputStream) -> CharEntry = {
        CharEntry(it.readUTF(), it.readUTF(), it.readUTF(), it.readDouble(), it.readBoolean())
    }
    private val writeChar: (DataOutputStream, CharEntry) -> Unit = { o, e ->
        o.writeUTF(e.char); o.writeUTF(e.quickCode); o.writeUTF(e.cangjieCode)
        o.writeDouble(e.freq); o.writeBoolean(e.isHkCore)
    }

    @Test
    fun `store then load round-trips rows exactly`() {
        val dir = tmp.newFolder("corpus")
        val rows = listOf(
            CharEntry("我", "hqi", "竹手戈", 0.91, true),
            CharEntry("你", "onf", "人弓火", 0.5, false),
            CharEntry("，", "", "", 0.0, false)
        )
        CorpusCache.store(dir, "chars", version = 7, rows = rows, writeRow = writeChar)
        assertEquals(rows, CorpusCache.load(dir, "chars", version = 7, readRow = readChar))
    }

    @Test
    fun `version mismatch is rejected so a stale cache is never read`() {
        val dir = tmp.newFolder("corpus")
        CorpusCache.store(dir, "chars", 7, listOf(CharEntry("我", "hqi", "", 0.9, true)), writeChar)
        assertNull(CorpusCache.load(dir, "chars", version = 8, readRow = readChar))
    }

    @Test
    fun `missing file returns null`() {
        assertNull(CorpusCache.load(tmp.newFolder("corpus"), "absent", 7, readChar))
    }

    @Test
    fun `truncated file is rejected (no trailing sentinel)`() {
        val dir = tmp.newFolder("corpus")
        val rows = listOf(CharEntry("我", "hqi", "", 0.9, true), CharEntry("你", "onf", "", 0.5, false))
        CorpusCache.store(dir, "chars", 7, rows, writeChar)
        val f = File(dir, "chars.idx")
        val bytes = f.readBytes()
        f.writeBytes(bytes.copyOf(bytes.size / 2)) // simulate a half-written/killed write
        assertNull(CorpusCache.load(dir, "chars", 7, readChar))
    }
}
