package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.EnglishAssistEntry
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.engine.Classifier
import com.hkmixedkeyboard.engine.EnglishCompletionIndex
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
}
