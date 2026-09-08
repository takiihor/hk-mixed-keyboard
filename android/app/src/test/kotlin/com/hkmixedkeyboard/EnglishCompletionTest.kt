package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.EnglishAssistEntry
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.engine.Classifier
import com.hkmixedkeyboard.engine.EnglishCompletionIndex
import com.hkmixedkeyboard.engine.EnglishLexicon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishCompletionTest {
    private val corpusDir = "src/main/assets/corpus"

    @Test
    fun `comm returns communication from corpus words`() {
        val index = EnglishCompletionIndex(
            listOf(
                EnglishAssistEntry("communication", "溝通", 0.95),
                EnglishAssistEntry("communicate", "溝通", 0.67),
                EnglishAssistEntry("community", "社區", 0.64)
            )
        )

        val suggestions = index.forPrefix("comm")

        assertTrue(suggestions.any { it.text == "communication" })
        assertTrue(suggestions.any { it.text == "communicate" })
        assertTrue(suggestions.any { it.text == "community" })
    }

    @Test
    fun `completion excludes exact word and respects frequency order`() {
        val index = EnglishCompletionIndex(
            listOf(
                EnglishAssistEntry("communication", "溝通", 0.95),
                EnglishAssistEntry("communication", "交流", 0.67),
                EnglishAssistEntry("communicate", "溝通", 0.80),
                EnglishAssistEntry("community", "社區", 0.70)
            )
        )

        assertFalse(index.forPrefix("communication").any { it.text == "communication" })
        assertEquals(
            listOf("communication", "communicate"),
            index.forPrefix("comm", limit = 2).map { it.text }
        )
    }

    @Test
    fun `mixed case prefix returns initial capital completions`() {
        val index = EnglishCompletionIndex(
            listOf(EnglishAssistEntry("hello", "你好", 0.95))
        )

        assertEquals(listOf("Hello"), index.forPrefix("He").map { it.text })
    }

    @Test
    fun `uppercase prefix returns uppercase completions`() {
        val index = EnglishCompletionIndex(
            listOf(EnglishAssistEntry("hello", "你好", 0.95))
        )

        assertEquals(listOf("HELLO"), index.forPrefix("HE").map { it.text })
    }

    @Test
    fun `exact match exclusion is case insensitive`() {
        val index = EnglishCompletionIndex(
            listOf(EnglishAssistEntry("hello", "你好", 0.95))
        )

        assertTrue(index.forPrefix("HELLO").isEmpty())
    }

    @Test
    fun `prefix shorter than two letters returns no completions`() {
        val index = EnglishCompletionIndex(
            listOf(EnglishAssistEntry("communication", "溝通", 0.95))
        )

        assertTrue(index.forPrefix("c").isEmpty())
    }

    @Test
    fun `real corpus provides English completion and Chinese suggestion for comm`() {
        val entries = readEnglishAssist("$corpusDir/english_assist.csv")
            .map { (english, chinese, frequency) ->
                EnglishAssistEntry(english, chinese, frequency)
            }
        val english = EnglishCompletionIndex(entries).forPrefix("comm")
        val chinese = Classifier(buildFullCorpusDecoder(corpusDir))
            .classify("comm", Scheme.QUICK)
            .cnCandidates

        assertTrue(english.any { it.text == "communication" })
        assertTrue(chinese.any { it.text == "溝通" })
    }

    // ── Hong Kong tokens (EnglishLexicon.LOCAL_TOKENS) ────────────────────

    @Test
    fun `a Hong Kong acronym completes with its canonical casing`() {
        val index = EnglishCompletionIndex(emptyList(), EnglishLexicon.LOCAL_TOKENS)

        assertEquals(listOf("MTR"), index.forPrefix("mt").map { it.text })
        // "hkd" itself is excluded as an exact match — completion, not lookup —
        // so its canonical casing is reached from the shorter prefix. Typing the
        // whole token gets its casing at commit, via CANONICAL_CASE.
        assertTrue(index.forPrefix("hk").map { it.text }.contains("HKD"))
    }

    @Test
    fun `canonical casing ignores how the token was typed`() {
        val index = EnglishCompletionIndex(emptyList(), EnglishLexicon.LOCAL_TOKENS)

        assertEquals(listOf("MTR"), index.forPrefix("MT").map { it.text })
        assertEquals(listOf("MTR"), index.forPrefix("Mt").map { it.text })
    }

    @Test
    fun `a token without canonical casing follows the typed pattern`() {
        val index = EnglishCompletionIndex(emptyList(), mapOf("doc" to null))

        assertEquals(listOf("doc"), index.forPrefix("do").map { it.text })
        assertEquals(listOf("Doc"), index.forPrefix("Do").map { it.text })
    }

    @Test
    fun `a local token outranks ordinary vocabulary for its own prefix`() {
        val index = EnglishCompletionIndex(
            listOf(EnglishAssistEntry("mtg", "會議", 0.9)),
            mapOf("mtr" to "MTR")
        )

        assertEquals("MTR", index.forPrefix("mt").first().text)
    }
}
