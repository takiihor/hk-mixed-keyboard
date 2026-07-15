package com.hkmixedkeyboard

import android.content.Context
import android.content.ContextWrapper
import com.hkmixedkeyboard.decoder.CharEntry
import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.CorpusBackedDecoder
import com.hkmixedkeyboard.decoder.CorpusLoader
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.HkscsSupplement
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.engine.CandidateDisplayPolicy
import com.hkmixedkeyboard.ime.CandidateCommitPolicy
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

    private val quickRows: List<List<String>> by lazy {
        File("src/main/assets/corpus/hk_core_chars.csv").useLines { lines ->
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
    fun `official overlay retains every HKSCS record and identifies unmapped fallback codes`() {
        val entries = HkscsSupplement.parse(supplementRows)

        assertEquals(4_606, entries.size)
        assertEquals("u200cd", entries.single { it.text == "𠃍" }.unicodeFallbackCode)
        assertEquals("u200d1", entries.single { it.text == "𠃑" }.unicodeFallbackCode)
    }

    @Test
    fun `supplemental entries are reachable through the production decoder`() {
        val entries = HkscsSupplement.parse(supplementRows)
        val loader = bareLoader().also { it.setHkscsEntries(entries) }
        val decoder = CorpusBackedDecoder(loader)

        assertTrue(decoder.decode("mi", Scheme.QUICK).candidates.any { it.text == "𠀾" })
        assertTrue(decoder.decode("bui", Scheme.JYUTPING).candidates.any { it.text == "𠀾" })
        val fallback = decoder.decode("u200cd", Scheme.QUICK).candidates.single()
        assertEquals("𠃍", fallback.text)
        assertEquals(SourceSchema.HKSCS_UNICODE, fallback.sourceSchema)
    }

    @Test
    fun `Unicode fallback is present and tap-only in every input mode`() {
        val entries = HkscsSupplement.parse(supplementRows)
        val decoder = CorpusBackedDecoder(bareLoader().also { it.setHkscsEntries(entries) })

        listOf(Scheme.QUICK, Scheme.JYUTPING, Scheme.PINYIN).forEach { scheme ->
            val fallback = decoder.decode("u200cd", scheme).candidates.single()
            assertEquals("𠃍", fallback.text)
            assertEquals(SourceSchema.HKSCS_UNICODE, fallback.sourceSchema)
            assertTrue(
                "$scheme must require an explicit candidate tap for a technical fallback",
                !CandidateCommitPolicy.isEligibleForSpace(fallback, scheme, "u200cd")
            )
        }
    }

    @Test
    fun `Unicode fallback is withheld when a bundled legacy route already reaches the character`() {
        val entries = HkscsSupplement.parse(supplementRows)
        val loader = bareLoader().also { it.setHkscsEntries(entries, includeLegacyRoute = true) }
        val decoder = CorpusBackedDecoder(loader)

        assertTrue(decoder.decode("u200ca", Scheme.QUICK).candidates.isEmpty())
        assertEquals("𠃍", decoder.decode("u200cd", Scheme.QUICK).candidates.single().text)
    }

    @Test
    fun `actual low ranked HKSCS Quick candidate remains in the expanded grid`() {
        val entries = HkscsSupplement.parse(supplementRows)
        val loader = bareLoader().also { it.setFullQuickEntries(entries) }
        val decoded = CorpusBackedDecoder(loader).decode("mi", Scheme.QUICK).candidates

        assertTrue("Fixture must exercise the former 80-candidate truncation",
            decoded.indexOfFirst { it.text == "𠀾" } >= 80)

        val expanded = CandidateDisplayPolicy().order(
            buffer = "mi",
            learned = emptyList(),
            english = emptyList(),
            decoded = decoded,
            literal = DecodeCandidate(
                "mi", "", SourceSchema.ENGLISH, CandidateType.EN_LITERAL, 0.0, false
            ),
            limit = CandidateDisplayPolicy.EXPANDED_LIMIT
        )

        assertTrue(expanded.any { it.text == "𠀾" })
    }

    private fun CorpusLoader.setHkscsEntries(
        entries: List<HkscsSupplement.Entry>,
        includeLegacyRoute: Boolean = false
    ) {
        setLazy("hkscsSupplement", lazyOf(entries))
        setLazy("chars", lazyOf(entries.filter { it.quickCode.isNotBlank() }.map {
            CharEntry(it.text, it.quickCode, "", 0.0, false)
        }))
        val hkscsJyutping = entries.flatMap { entry ->
            entry.jyutping.map { reading ->
                com.hkmixedkeyboard.decoder.JyutpingEntry(reading, entry.text, 0.0)
            }
        }
        val legacyRoute = if (includeLegacyRoute) listOf(
            com.hkmixedkeyboard.decoder.JyutpingEntry("legacy", "𠃊", 1.0)
        ) else emptyList()
        setLazy("jyutping", lazyOf(hkscsJyutping + legacyRoute))
        setLazy("englishAssist", lazyOf(emptyList<com.hkmixedkeyboard.decoder.EnglishAssistEntry>()))
        setLazy("phrases", lazyOf(emptyList<com.hkmixedkeyboard.decoder.PhraseEntry>()))
        setLazy("mixedPhrases", lazyOf(emptyList<com.hkmixedkeyboard.decoder.MixedPhraseEntry>()))
    }

    private fun CorpusLoader.setFullQuickEntries(entries: List<HkscsSupplement.Entry>) {
        val base = quickRows.mapNotNull { row ->
            if (row.size < 5) null
            else CharEntry(
                row[0], row[1], row[2], row[3].toDoubleOrNull() ?: 0.0,
                row[4].trim() == "1"
            )
        }
        val supplement = entries.filter { it.quickCode.isNotBlank() }.map {
            CharEntry(it.text, it.quickCode, "", 0.0, false)
        }
        setLazy("chars", lazyOf(base + supplement))
        setLazy("jyutping", lazyOf(emptyList<com.hkmixedkeyboard.decoder.JyutpingEntry>()))
        setLazy("phrases", lazyOf(emptyList<com.hkmixedkeyboard.decoder.PhraseEntry>()))
        setLazy("mixedPhrases", lazyOf(emptyList<com.hkmixedkeyboard.decoder.MixedPhraseEntry>()))
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
