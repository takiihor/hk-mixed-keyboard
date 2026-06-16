package com.hkmixedkeyboard

import com.hkmixedkeyboard.ime.CandidateRequestGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateRequestGateTest {

    @Test
    fun `only latest generation may render`() {
        val gate = CandidateRequestGate()
        val first = gate.next()
        val second = gate.next()

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun `invalidate rejects outstanding work`() {
        val gate = CandidateRequestGate()
        val generation = gate.next()

        gate.invalidate()

        assertFalse(gate.isCurrent(generation))
    }
}
