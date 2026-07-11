package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.*
import com.hkmixedkeyboard.memory.UserMemory
import org.junit.Assert.*
import org.junit.Test

/**
 * Memory behavior tests for assist candidates.
 *
 * Rules:
 *   - A single tap on an assist candidate RECORDS it in memory (count=1)
 *   - count=1 → confidence < 0.80 → NO hardOverride yet
 *   - 3 consistent taps → count=3, confidence=1.0 ≥ 0.80 → hardOverride fires
 *   - Memory records the candidate (text, type) but NOT the full surrounding sentence
 *   - Safe mode: zero memory writes regardless of tap count
 *
 * All tests are EXPECTED to PASS.
 */
class MemoryAssistTest {

    // ── 1. Single tap records memory at count=1, no hard override ─────────

    @Test
    fun `Single tap on assist candidate records count=1 in memory`() {
        val mock溝通 = cnChar("溝通", "communication")
        val memory = UserMemory()
        val ctrl = makeCtrl(memory = memory, classify = { buf ->
            assistCandidates(buf, listOf(mock溝通))
        })

        val state = ImeStateData(buffer = "communication", imeState = ImeState.COMPOSING)
        ctrl.onCandidateTap(mock溝通, state)

        val entry = memory.exactMatch("communication")
        assertNotNull("Memory entry should exist after single tap", entry)
        assertEquals("count should be 1 after single tap", 1, entry!!.count)
        assertEquals("cnCount should be 1", 1, entry.cnCount)
    }

    @Test
    fun `Single tap does not trigger hard override (confidence too low)`() {
        val mock溝通 = cnChar("溝通", "communication")
        val memory = UserMemory()
        val ctrl = makeCtrl(memory = memory, classify = { buf ->
            assistCandidates(buf, listOf(mock溝通))
        })

        val state = ImeStateData(buffer = "communication", imeState = ImeState.COMPOSING)
        ctrl.onCandidateTap(mock溝通, state)

        // After 1 tap: count=1, confidence=1.0 (only 1 record), but count < 3 → no override
        val override = memory.hardOverride("communication", isSensitive = false)
        assertNull("Single tap must not trigger hardOverride (count < 3)", override)
    }

    @Test
    fun `Two taps do not trigger hard override (count below threshold)`() {
        val mock溝通 = cnChar("溝通", "communication")
        val memory = UserMemory()
        val ctrl = makeCtrl(memory = memory, classify = { buf ->
            assistCandidates(buf, listOf(mock溝通))
        })

        repeat(2) {
            val state = ImeStateData(buffer = "communication", imeState = ImeState.COMPOSING)
            ctrl.onCandidateTap(mock溝通, state)
        }

        val override = memory.hardOverride("communication", isSensitive = false)
        assertNull("Two taps: count=2 < 3 → no hardOverride", override)
    }

    // ── 2. Three consistent taps → hard override fires ─────────────────────

    @Test
    fun `Three consistent taps create hard override for 溝通`() {
        val mock溝通 = cnChar("溝通", "communication")
        val memory = UserMemory()
        val ctrl = makeCtrl(memory = memory, classify = { buf ->
            assistCandidates(buf, listOf(mock溝通))
        })

        repeat(3) {
            val state = ImeStateData(buffer = "communication", imeState = ImeState.COMPOSING)
            ctrl.onCandidateTap(mock溝通, state)
        }

        val override = memory.hardOverride("communication", isSensitive = false)
        assertNotNull("3 consistent taps → hardOverride should fire", override)
        assertEquals("hardOverride should return 溝通", "溝通", override!!.text)
    }

    @Test
    fun `Space ignores hard override after three consistent taps`() {
        val mock溝通 = cnChar("溝通", "communication")
        val memory = UserMemory()
        // Seed memory: 3 CN picks for "communication"
        memory.seed("communication", "溝通", "isu", count = 3, isHkCore = false)

        val ctrl = makeCtrl(memory = memory, classify = { buf ->
            assistCandidates(buf, listOf(mock溝通))
        })

        val state = ImeStateData(buffer = "communication", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)

        assertEquals("Space should stay literal even when memory has a hard override",
            "communication ", out.committedText)
    }

    // ── 3. Mixed taps do not reach hard override confidence ───────────────

    @Test
    fun `Alternating CN and EN taps keep confidence below threshold`() {
        val mock溝通 = cnChar("溝通", "communication")
        val mockEn = enLiteralCand("communication")
        val memory = UserMemory()
        val ctrl = makeCtrl(memory = memory, classify = { buf ->
            assistCandidates(buf, listOf(mock溝通))
        })

        // Alternate: 2 CN, 1 EN → count=3, cnCount=2, confidence = 2/3 = 0.667 < 0.80
        ctrl.onCandidateTap(mock溝通, ImeStateData(buffer = "communication"))
        ctrl.onCandidateTap(mockEn, ImeStateData(buffer = "communication"))
        ctrl.onCandidateTap(mock溝通, ImeStateData(buffer = "communication"))

        val entry = memory.exactMatch("communication")
        assertNotNull(entry)
        assertEquals(3, entry!!.count)

        // confidence = max(2,1)/3 = 0.667 < 0.80 → no override
        val override = memory.hardOverride("communication", isSensitive = false)
        assertNull("Mixed picks: confidence < 0.80 → no hardOverride", override)
    }

    // ── 4. Memory candidate is candidate text only, not full sentence ──────

    @Test
    fun `Memory records candidate text not surrounding sentence`() {
        val mock溝通 = cnChar("溝通", "communication")
        val memory = UserMemory()
        val ctrl = makeCtrl(memory = memory, classify = { buf ->
            assistCandidates(buf, listOf(mock溝通))
        })

        val state = ImeStateData(buffer = "communication", imeState = ImeState.COMPOSING)
        ctrl.onCandidateTap(mock溝通, state)

        val entry = memory.exactMatch("communication")
        assertNotNull(entry)
        // Memory stores the candidate TEXT (溝通), keyed by BUFFER (communication).
        // It must NOT store any surrounding context, sentence, or app content.
        assertEquals("Memory stores candidate text only", "溝通", entry!!.candidate.text)
        assertEquals("Memory key is the buffer, not the sentence", "communication", entry.buffer)
    }

    // ── 5. Hard override entry survives session (memory persistence) ───────

    @Test
    fun `Hard override from memory does not affect subsequent Space`() {
        val memory = UserMemory()
        // Simulate previous session: user picked 溝通 from communication 5 times
        memory.seed("communication", "溝通", "isu", count = 5, isHkCore = false)

        val mock溝通 = cnChar("溝通", "communication")
        val ctrl = makeCtrl(memory = memory, classify = { buf ->
            assistCandidates(buf, listOf(mock溝通))
        })

        val state = ImeStateData(buffer = "communication", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)

        assertEquals("Seeded memory override must not fire on Space",
            "communication ", out.committedText)
        assertEquals("Literal Space commit is still learned", true, out.memoryWrite.shouldWrite)
    }
}
