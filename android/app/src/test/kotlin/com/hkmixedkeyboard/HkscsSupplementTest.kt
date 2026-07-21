package com.hkmixedkeyboard

import android.content.Context
import android.content.ContextWrapper
import com.hkmixedkeyboard.decoder.CharEntry
import com.hkmixedkeyboard.decoder.CorpusBackedDecoder
import com.hkmixedkeyboard.decoder.CorpusLoader
import com.hkmixedkeyboard.decoder.HkscsSupplement
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.util.Csv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HkscsSupplementTest {

    private val supplementRows: List<List<String>> by lazy {
        File("src/main/assets/corpus/hkscs_supplement.csv").useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .drop(1)
                .map(Csv::split)
                .toList()
        }
    }

    @Test
    fun `official HKSCS supplement exposes Quick and Jyutping readings`() {
        val entries = HkscsSupplement.parse(supplementRows)
        val entry = entries.single { it.text == "𠀾" }

        assertEquals("mi", entry.quickCode)
        assertEquals(listOf("bui"), entry.jyutping)
        assertEquals(1, entry.text.codePointCount(0, entry.text.length))
    }

    @Test
    fun `supplemental entries are reachable through the production decoder`() {
        val entries = HkscsSupplement.parse(supplementRows)
        val loader = bareLoader().also { it.setHkscsEntries(entries) }
        val decoder = CorpusBackedDecoder(loader)

        assertTrue(decoder.decode("mi", Scheme.QUICK).candidates.any { it.text == "𠀾" })
        assertTrue(decoder.decode("bui", Scheme.JYUTPING).candidates.any { it.text == "𠀾" })
    }

    @Test
    fun `records without an official mapping get a tap-only Unicode route`() {
        val entries = HkscsSupplement.parse(supplementRows)
        val unmapped = entries.filter { it.unicodeFallbackCode != null }

        assertEquals(
            listOf("u200ca", "u200cb", "u200cd", "u200d1", "u2010c", "u2010e", "u21fe8"),
            unmapped.map { it.unicodeFallbackCode }
        )
        // The escape must never be presented as a linguistic reading.
        assertTrue(unmapped.all { it.quickCode.isBlank() && it.jyutping.isEmpty() })
    }

    @Test
    fun `the Unicode fallback is offered but never exact`() {
        val loader = bareLoader().also { it.setHkscsEntries(HkscsSupplement.parse(supplementRows)) }
        val result = CorpusBackedDecoder(loader).decode("u200cd", Scheme.QUICK)

        assertEquals("\uD840\uDCCD", result.candidates.single().text)
        assertEquals(SourceSchema.HKSCS_UNICODE, result.candidates.single().sourceSchema)
        // isExactCode stays false, so Space/punctuation keep committing the literal
        // buffer rather than auto-committing a technical escape.
        assertTrue(!result.isExactCode)
    }

    private fun CorpusLoader.setHkscsEntries(entries: List<HkscsSupplement.Entry>) {
        // Also injected so the fallback index never touches assets, which a
        // unit-test Context cannot read.
        setLazy("hkscsSupplement", lazyOf(entries))
        setLazy("chars", lazyOf(entries.filter { it.quickCode.isNotBlank() }.map {
            CharEntry(it.text, it.quickCode, "", 0.0, false)
        }))
        setLazy("jyutping", lazyOf(entries.flatMap { entry ->
            entry.jyutping.map { reading ->
                com.hkmixedkeyboard.decoder.JyutpingEntry(reading, entry.text, 0.0)
            }
        }))
        setLazy("phrases", lazyOf(emptyList<com.hkmixedkeyboard.decoder.PhraseEntry>()))
    }

    private fun CorpusLoader.setLazy(property: String, value: Lazy<*>) {
        val field = CorpusLoader::class.java.getDeclaredField("${property}\$delegate")
        field.isAccessible = true
        field.set(this, value)
    }

    private fun bareLoader(): CorpusLoader {
        val context = unsafe.javaClass
            .getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, ContextWrapper::class.java) as Context
        return CorpusLoader(context)
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
