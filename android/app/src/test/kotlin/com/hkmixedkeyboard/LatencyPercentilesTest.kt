package com.hkmixedkeyboard

import com.hkmixedkeyboard.performance.LatencyDistribution
import org.junit.Assert.assertEquals
import org.junit.Test

class LatencyPercentilesTest {
    @Test
    fun `percentiles use nearest-rank and remain bounded`() {
        val distribution = LatencyDistribution(maxSamples = 5)
        listOf(10L, 20L, 30L, 40L, 50L, 60L).forEach(distribution::record)

        assertEquals(40L, distribution.snapshot().p50Ms)
        assertEquals(60L, distribution.snapshot().p95Ms)
        assertEquals(5, distribution.snapshot().sampleCount)
    }
}
