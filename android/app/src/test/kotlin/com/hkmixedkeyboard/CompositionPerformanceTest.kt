package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.ImeStateData
import com.hkmixedkeyboard.commit.Thresholds
import com.hkmixedkeyboard.memory.UserMemory
import org.junit.Assert.assertEquals
import org.junit.Test

class CompositionPerformanceTest {

    @Test
    fun `letter press updates state without synchronous classification`() {
        var classifications = 0
        val ctrl = makeCtrl(memory = UserMemory(), classify = {
            classifications++
            unknown(it)
        })

        val out = ctrl.onKeyPress("a", ImeStateData())

        assertEquals("a", out.newState.buffer)
        assertEquals(0, classifications)
    }

    @Test
    fun `composing backspace updates state without synchronous classification`() {
        var classifications = 0
        val ctrl = makeCtrl(memory = UserMemory(), classify = {
            classifications++
            unknown(it)
        })

        val out = ctrl.onBackspace(ImeStateData(buffer = "comm"))

        assertEquals("com", out.newState.buffer)
        assertEquals(0, classifications)
    }

    @Test
    fun `buffer overflow commits existing text before starting new composition`() {
        val existing = "a".repeat(Thresholds.MAX_BUFFER_LEN)
        val ctrl = makeCtrl(memory = UserMemory(), classify = { unknown(it) })

        val out = ctrl.onKeyPress("b", ImeStateData(buffer = existing))

        assertEquals(existing, out.committedText)
        assertEquals("b", out.newState.buffer)
    }
}
