package com.hkmixedkeyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hkmixedkeyboard.decoder.CorpusBackedDecoder
import com.hkmixedkeyboard.decoder.CorpusLoader
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.performance.LatencyDistribution
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypingLatencyTest {
    @Test
    fun warmFullCorpusDecodeP95StaysWithinCandidateCeiling() {
        val decoder = CorpusBackedDecoder(
            CorpusLoader(InstrumentationRegistry.getInstrumentation().targetContext)
        )
        val cases = listOf(
            Scheme.QUICK to "rryo",
            Scheme.JYUTPING to "nei5hou2",
            Scheme.PINYIN to "ni3hao3"
        )
        cases.forEach { (scheme, input) -> decoder.decode(input, scheme) }
        val distribution = LatencyDistribution(300)
        repeat(300) { index ->
            val (scheme, input) = cases[index % cases.size]
            val start = android.os.SystemClock.elapsedRealtimeNanos()
            decoder.decode(input, scheme)
            distribution.record(
                (android.os.SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L
            )
        }
        assertTrue(distribution.snapshot().p95Ms <= 150L)
    }
}
