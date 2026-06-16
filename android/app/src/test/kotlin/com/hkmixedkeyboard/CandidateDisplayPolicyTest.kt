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
}
