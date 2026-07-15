package com.hkmixedkeyboard.performance

data class LatencySnapshot(
    val sampleCount: Int,
    val p50Ms: Long,
    val p95Ms: Long,
    val p99Ms: Long,
    val maxMs: Long
)

/** Bounded rolling latency samples using nearest-rank percentiles. */
class LatencyDistribution(private val maxSamples: Int = 2_000) {
    init { require(maxSamples > 0) }

    private val samples = LongArray(maxSamples)
    private var count = 0
    private var next = 0

    @Synchronized
    fun record(milliseconds: Long) {
        samples[next] = milliseconds.coerceAtLeast(0)
        next = (next + 1) % maxSamples
        if (count < maxSamples) count++
    }

    @Synchronized
    fun snapshot(): LatencySnapshot {
        if (count == 0) return LatencySnapshot(0, 0, 0, 0, 0)
        val sorted = samples.copyOf(count).sortedArray()
        fun percentile(value: Double): Long {
            val rank = kotlin.math.ceil(value * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }
        return LatencySnapshot(
            sampleCount = sorted.size,
            p50Ms = percentile(0.50),
            p95Ms = percentile(0.95),
            p99Ms = percentile(0.99),
            maxMs = sorted.last()
        )
    }
}
