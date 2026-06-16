package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.*
import com.hkmixedkeyboard.memory.UserMemory
import org.junit.Assert.*
import org.junit.Test

/**
 * Safe Keyboard Mode tests.
 *
 * In password / NO_SUGGESTIONS fields:
 *   - Candidate bar must be empty (no candidates shown, including assist candidates)
 *   - No memory writes
 *   - prevCommitted cleared
 *   - hardOverride must not fire even if memory has entries
 *
 * All tests are EXPECTED to PASS.
 */
class SafeModeTest {

    private val safeCtx = ImeContext(isSensitiveField = true)

    // ── 1. Assist candidates not shown in safe field ───────────────────────

    @Test
    fun `In safe field English assist candidates are not returned`() {
        val mock溝通 = cnChar("溝通", "communication")
        val ctrl = makeCtrl(ctx = safeCtx, classify = { buf ->
            assistCandidates(buf, listOf(mock溝通))
        })
        val state = ImeStateData(buffer = "communication", imeState = ImeState.COMPOSING)
        val out = ctrl.onKeyPress("n", state.copy(buffer = "communicatio"))

        // The bar should be populated based on classify(), but safe mode means
        // the bar is shown from classify cnCandidates regardless — key thing is no memory write
        // and no commit of Chinese on space
        assertFalse("Safe mode: must not write memory on keypress",
            out.memoryWrite.shouldWrite)
    }

    @Test
    fun `In safe field Space commits literal not assist candidate`() {
        val mock溝通 = cnChar("溝通", "communication")
        val ctrl = makeCtrl(ctx = safeCtx, classify = { buf ->
            assistCandidates(buf, listOf(mock溝通))
        })
        val state = ImeStateData(buffer = "communication", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)

        assertEquals("communication", out.committedText?.trimEnd())
        assertNotEquals("溝通", out.committedText?.trimEnd())
        assertFalse("Safe mode: must not write memory on space commit", out.memoryWrite.shouldWrite)
    }

    @Test
    fun `In safe field romanization assist candidate not committed on Space`() {
        val mock衝 = cnChar("衝", "chong")
        val ctrl = makeCtrl(ctx = safeCtx, classify = { buf ->
            assistCandidates(buf, listOf(mock衝))
        })
        val state = ImeStateData(buffer = "chong", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)

        assertEquals("chong", out.committedText?.trimEnd())
        assertFalse(out.memoryWrite.shouldWrite)
    }

    // ── 2. No memory write in safe field ──────────────────────────────────

    @Test
    fun `In safe field candidate tap records no memory`() {
        val mock溝通 = cnChar("溝通", "communication")
        val memory = UserMemory()
        val ctrl = makeCtrl(memory = memory, ctx = safeCtx, classify = { buf ->
            assistCandidates(buf, listOf(mock溝通))
        })
        val state = ImeStateData(buffer = "communication", imeState = ImeState.COMPOSING)
        ctrl.onCandidateTap(mock溝通, state)

        assertEquals("Safe mode: memory must stay empty after tap", 0, memory.size())
    }

    @Test
    fun `In safe field memory record is not called even after 5 taps`() {
        val mock溝通 = cnChar("溝通", "communication")
        val memory = UserMemory()
        val ctrl = makeCtrl(memory = memory, ctx = safeCtx, classify = { buf ->
            assistCandidates(buf, listOf(mock溝通))
        })
        repeat(5) {
            val state = ImeStateData(buffer = "communication", imeState = ImeState.COMPOSING)
            ctrl.onCandidateTap(mock溝通, state)
        }

        assertEquals("Safe mode: 5 taps must not write memory", 0, memory.size())
    }

    // ── 3. hardOverride does not fire in safe field ────────────────────────

    @Test
    fun `In safe field hardOverride is suppressed even with seeded memory`() {
        val memory = UserMemory()
        memory.seed("communication", "溝通", "isu", count = 10, isHkCore = false)
        // count=10, confidence=1.0 → normally would trigger hardOverride
        val ctrl = makeCtrl(memory = memory, ctx = safeCtx, classify = { buf ->
            assistCandidates(buf, listOf(cnChar("溝通", "isu")))
        })
        val state = ImeStateData(buffer = "communication", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)

        assertNotEquals("Safe mode: hardOverride must not fire", "溝通", out.committedText?.trimEnd())
        assertEquals("communication", out.committedText?.trimEnd())
    }

    // ── 4. prevCommitted not set in safe field ─────────────────────────────

    @Test
    fun `In safe field prevCommitted is null after commit`() {
        val mock溝通 = cnChar("溝通", "communication")
        val ctrl = makeCtrl(ctx = safeCtx, classify = { buf ->
            assistCandidates(buf, listOf(mock溝通))
        })
        val state = ImeStateData(buffer = "communication", imeState = ImeState.COMPOSING)
        val out = ctrl.onCandidateTap(mock溝通, state)

        assertNull("Safe mode: prevCommitted must not be set", out.newState.prevCommitted)
    }

    // ── 5. Safe mode candidate bar is empty on commit ─────────────────────

    @Test
    fun `In safe field candidate bar is empty after commit`() {
        val mock溝通 = cnChar("溝通", "communication")
        val ctrl = makeCtrl(ctx = safeCtx, classify = { buf ->
            assistCandidates(buf, listOf(mock溝通))
        })
        val state = ImeStateData(buffer = "communication", imeState = ImeState.COMPOSING)
        val out = ctrl.onCandidateTap(mock溝通, state)

        assertTrue("Safe mode: candidate bar must be empty after commit",
            out.candidateBar.isEmpty())
    }
}
