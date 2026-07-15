package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.engine.CandidateDisplayPolicy
import com.hkmixedkeyboard.memory.MemorySuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateDisplayPolicyTest {

    private val policy = CandidateDisplayPolicy()

    @Test
    fun `two letters show personalized and decoded Chinese before English`() {
        val result = policy.order(
            buffer = "rr",
            learned = listOf(
                MemorySuggestion(cnChar("唔", "rr"), 5),
                MemorySuggestion(enLiteralCand("array"), 8)
            ),
            english = listOf(enLiteralCand("array")),
            decoded = listOf(cnChar("呂", "rr")),
            literal = enLiteralCand("rr")
        )

        assertEquals(listOf("唔", "呂"), result.take(2).map { it.text })
        assertTrue(result.indexOfFirst { it.text == "array" } > 1)
    }

    @Test
    fun `three letters show personalized English and completions before Chinese`() {
        val result = policy.order(
            buffer = "comm",
            learned = listOf(
                MemorySuggestion(cnChar("溝通", "comm"), 9),
                MemorySuggestion(enLiteralCand("communication"), 4)
            ),
            english = listOf(enLiteralCand("community")),
            decoded = listOf(
                DecodeCandidate(
                    "溝通", "communication", SourceSchema.ENGLISH_ASSIST,
                    CandidateType.ENGLISH_ASSIST, 0.95, false
                )
            ),
            literal = enLiteralCand("comm")
        )

        assertEquals(
            listOf("communication", "community", "comm"),
            result.take(3).map { it.text }
        )
        assertTrue(result.any { it.text == "溝通" })
    }

    @Test
    fun `exact English assist meaning ranks before English prefix completions`() {
        val exactMeaning = DecodeCandidate(
            "貓", "cat", SourceSchema.ENGLISH_ASSIST,
            CandidateType.ENGLISH_ASSIST, 0.72, false
        )
        val result = policy.order(
            buffer = "cat",
            learned = listOf(MemorySuggestion(enLiteralCand("category"), 4)),
            english = listOf(enLiteralCand("category")),
            decoded = listOf(exactMeaning),
            literal = enLiteralCand("cat")
        )

        assertEquals(listOf("貓", "category"), result.take(2).map { it.text })
    }

    @Test
    fun `english word surfaces Chinese meaning right after the literal`() {
        // Typing an English word: the completions + literal lead, but the
        // Traditional-Chinese meaning must follow immediately after the literal —
        // ahead of trailing English decoder noise, not at the end of the bar.
        val meaning = DecodeCandidate(
            "動作", "act", SourceSchema.ENGLISH_ASSIST, CandidateType.ENGLISH_ASSIST, 0.7, false
        )
        val enNoise = DecodeCandidate(
            "actor", "act", SourceSchema.ENGLISH, CandidateType.EN_LITERAL, 0.0, false
        )
        val result = policy.order(
            buffer = "action",
            learned = emptyList(),
            english = listOf(enLiteralCand("action")),
            decoded = listOf(meaning, enNoise),
            literal = enLiteralCand("action")
        ).map { it.text }

        val literalIdx = result.indexOf("action")
        val meaningIdx = result.indexOf("動作")
        val noiseIdx = result.indexOf("actor")
        assertTrue("Chinese meaning should come after the literal, got: $result",
            meaningIdx > literalIdx)
        assertTrue("Chinese meaning should come before English decoder noise, got: $result",
            meaningIdx < noiseIdx)
    }

    @Test
    fun `custom words rank first even for a long quick code`() {
        val custom = DecodeCandidate(
            "我哋", "ogrp", SourceSchema.USER_MEMORY, CandidateType.CHAR, 0.95, true
        )
        val result = policy.order(
            buffer = "ogrp",
            learned = emptyList(),
            english = listOf(enLiteralCand("ogre")),
            decoded = listOf(custom),
            literal = enLiteralCand("ogrp")
        )

        assertEquals("我哋", result.first().text)
    }

    @Test
    fun `romanization mode keeps Chinese first even for long buffers`() {
        // In 粵拼, "leng" is Cantonese romanization → the Chinese is what's wanted,
        // and English-word noise should not be injected (caller passes english=[]).
        val result = policy.order(
            buffer = "leng",
            learned = emptyList(),
            english = emptyList(),
            decoded = listOf(cnChar("靚", "leng")),
            literal = enLiteralCand("leng"),
            chineseFirst = true
        )

        assertEquals("靚", result.first().text)
        assertTrue(result.indexOfFirst { it.text == "leng" } > 0)
    }

    @Test
    fun `ordering removes duplicate text while preserving first ranked source`() {
        val personalized = enLiteralCand("communication").copy(frequency = 0.4)
        val corpus = enLiteralCand("communication").copy(frequency = 0.95)

        val result = policy.order(
            buffer = "comm",
            learned = listOf(MemorySuggestion(personalized, 3)),
            english = listOf(corpus),
            decoded = emptyList(),
            literal = enLiteralCand("comm")
        )

        assertEquals(1, result.count { it.text == "communication" })
        assertEquals(0.4, result.first().frequency, 0.0)
    }

    @Test
    fun `expanded candidate limit keeps lower ranked quick candidates`() {
        val decoded = (1..20).map { i ->
            cnChar("候$i", "or", freq = (100 - i).toDouble())
        }

        val bar = policy.order(
            buffer = "or",
            learned = emptyList(),
            english = emptyList(),
            decoded = decoded,
            literal = enLiteralCand("or"),
            limit = CandidateDisplayPolicy.BAR_LIMIT
        )
        val expanded = policy.order(
            buffer = "or",
            learned = emptyList(),
            english = emptyList(),
            decoded = decoded,
            literal = enLiteralCand("or"),
            limit = CandidateDisplayPolicy.EXPANDED_LIMIT
        )

        assertEquals(false, bar.any { it.text == "候16" })
        assertEquals(true, expanded.any { it.text == "候16" })
    }

    @Test
    fun `expanded grid retains supplementary HKSCS candidate beyond the bar cap`() {
        val baseCandidates = (1..90).map { i ->
            cnChar("候$i", "mi", freq = (100 - i).toDouble())
        }
        val hkscsSupplement = cnChar("𠀾", "mi", freq = 0.0)

        val expanded = policy.order(
            buffer = "mi",
            learned = emptyList(),
            english = emptyList(),
            decoded = baseCandidates + hkscsSupplement,
            literal = enLiteralCand("mi"),
            limit = CandidateDisplayPolicy.EXPANDED_LIMIT
        )

        assertEquals(90, expanded.indexOfFirst { it.text == "𠀾" })
    }

    @Test
    fun `next character predictions promote learned choices`() {
        val result = policy.orderPredictions(
            learned = listOf(MemorySuggestion(cnChar("估", "我"), 3)),
            decoded = listOf(
                cnChar("哋", "我", freq = 0.95),
                cnChar("估", "我", freq = 0.1)
            )
        )

        assertEquals(listOf("估", "哋"), result.take(2).map { it.text })
    }

    @Test
    fun `learned Chinese candidate is marked as user memory and ranked first`() {
        val result = policy.order(
            buffer = "or",
            learned = listOf(MemorySuggestion(cnChar("估", "or", isHkCore = false), 3)),
            english = emptyList(),
            decoded = listOf(cnChar("嗰", "or", isHkCore = false, freq = 0.95)),
            literal = enLiteralCand("or")
        )

        assertEquals("估", result.first().text)
        assertEquals(SourceSchema.USER_MEMORY, result.first().sourceSchema)
    }
}
