package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.QuickPrefixCandidateIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickPrefixCandidateIndexTest {

    @Test
    fun `prefix candidates are pre-ranked without scanning exact index`() {
        val index = QuickPrefixCandidateIndex(
            mapOf(
                "rr" to listOf(cnChar("低", "rr", freq = 0.3)),
                "ra" to listOf(cnChar("高", "ra", freq = 0.9)),
                "rb" to listOf(cnChar("中", "rb", freq = 0.6))
            )
        )

        assertEquals(
            listOf("高", "中"),
            index.candidates("r", limit = 2).map { it.text }
        )
    }
}
