package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.CandidateRenderSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CandidateRenderSnapshotTest {

    @Test
    fun `same candidate identities produce equal snapshots`() {
        val candidates = listOf(cnChar("唔", "rr"), enLiteralCand("rr"))

        assertEquals(
            CandidateRenderSnapshot.from(candidates),
            CandidateRenderSnapshot.from(candidates.map { it.copy() })
        )
    }

    @Test
    fun `candidate order and type affect snapshot`() {
        val first = CandidateRenderSnapshot.from(
            listOf(cnChar("唔", "rr"), enLiteralCand("rr"))
        )
        val second = CandidateRenderSnapshot.from(
            listOf(enLiteralCand("rr"), cnChar("唔", "rr"))
        )

        assertNotEquals(first, second)
    }
}
