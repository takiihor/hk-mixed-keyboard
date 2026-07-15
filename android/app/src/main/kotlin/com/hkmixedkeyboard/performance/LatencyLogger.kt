package com.hkmixedkeyboard.performance

import android.util.Log
import android.os.SystemClock
import com.hkmixedkeyboard.BuildConfig

object LatencyLogger {
    private const val TAG = "HkIme.Latency"
    @Volatile private var keyDownNs = 0L
    private val acknowledgement = LatencyDistribution()
    private val candidateUpdate = LatencyDistribution()

    fun keyDown() {
        keyDownNs = SystemClock.elapsedRealtimeNanos()
    }

    fun visualFeedback() {
        record("visual_feedback", keyDownNs, acknowledgement)
    }

    fun decodeStart() {
        if (BuildConfig.PERF_TRACING) log("decode_start", keyDownNs)
    }

    fun decodeEnd(bufLen: Int, candidateCount: Int, scheme: String) {
        if (!BuildConfig.PERF_TRACING) return
        val ms = elapsedMs(keyDownNs)
        Log.d(TAG, "decode_end ms=$ms buf_len=$bufLen candidates=$candidateCount scheme=$scheme")
    }

    fun firstCandidateRender() {
        record("first_candidate_render", keyDownNs, candidateUpdate)
    }

    fun snapshot(): Pair<LatencySnapshot, LatencySnapshot> =
        acknowledgement.snapshot() to candidateUpdate.snapshot()

    private fun record(event: String, since: Long, distribution: LatencyDistribution) {
        if (since == 0L) return
        val ms = elapsedMs(since)
        distribution.record(ms)
        if (BuildConfig.PERF_TRACING) Log.d(TAG, "$event ms=$ms")
    }

    private fun log(event: String, since: Long) {
        val ms = elapsedMs(since)
        Log.d(TAG, "$event ms=$ms")
    }

    private fun elapsedMs(sinceNs: Long): Long =
        ((SystemClock.elapsedRealtimeNanos() - sinceNs) / 1_000_000L).coerceAtLeast(0L)
}
