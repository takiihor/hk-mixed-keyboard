package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.ImeContext
import com.hkmixedkeyboard.commit.ImeStateData
import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.memory.MemoryEntry
import com.hkmixedkeyboard.memory.MemoryIndex
import com.hkmixedkeyboard.memory.UserMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizedSuggestionTest {

    @Test
    fun `learned English words match prefixes and rank by frequency`() {
        val memory = UserMemory()
        repeat(2) { memory.record("happy", enLiteralCand("happy"), false) }
        repeat(4) { memory.record("happen", enLiteralCand("happen"), false) }

        val suggestions = memory.suggestions("hap", isSensitive = false)

        assertEquals(listOf("happen", "happy"), suggestions.map { it.candidate.text })
        assertEquals(listOf(4, 2), suggestions.map { it.count })
    }

    @Test
    fun `learned Chinese choices remain separate and rank by frequency`() {
        val memory = UserMemory()
        repeat(2) { memory.record("nei", cnChar("你", "nei"), false) }
        repeat(5) { memory.record("nei", cnChar("呢", "nei"), false) }

        val suggestions = memory.suggestions("ne", isSensitive = false)

        assertEquals(listOf("呢", "你"), suggestions.map { it.candidate.text })
        assertEquals(listOf(5, 2), suggestions.map { it.count })
    }

    @Test
    fun `Chinese assist selection is counted as Chinese memory`() {
        val memory = UserMemory()
        val assist = DecodeCandidate(
            "溝通", "communication", SourceSchema.ENGLISH_ASSIST,
            CandidateType.ENGLISH_ASSIST, 0.95, false
        )

        memory.record("communication", assist, false)

        val entry = memory.exactMatch("communication")!!
        assertEquals(1, entry.cnCount)
        assertEquals(0, entry.enCount)
    }

    @Test
    fun `sensitive fields never return learned suggestions`() {
        val memory = UserMemory()
        memory.record("happy", enLiteralCand("happy"), false)

        assertTrue(memory.suggestions("hap", isSensitive = true).isEmpty())
    }

    @Test
    fun `always-space automatically learns literal buffer`() {
        val memory = UserMemory()
        val ctrl = makeCtrl(
            memory = memory,
            ctx = ImeContext(),
            classify = { clearEnglish(it) }
        )

        ctrl.onSpace(ImeStateData(buffer = "happy"))

        assertEquals("happy", memory.suggestions("hap", false).single().candidate.text)
    }

    @Test
    fun `cache hydration keeps newer in-session counts`() {
        val index = MemoryIndex()
        val candidate = cnChar("估", "or")

        repeat(3) { index.record("or", candidate) }
        index.putIfNewer(MemoryEntry("or", candidate, count = 1, cnCount = 1, enCount = 0))

        val suggestion = index.suggestions("or", limit = 8).single()
        assertEquals("估", suggestion.candidate.text)
        assertEquals(3, suggestion.count)
    }

}
