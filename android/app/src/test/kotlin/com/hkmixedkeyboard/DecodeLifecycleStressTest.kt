package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.ime.DecodeWorkCoordinator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodeLifecycleStressTest {
    @Test
    fun `one hundred thousand generated lifecycle events never revive cancelled work`() {
        val coordinator = DecodeWorkCoordinator()
        var generation = 0L
        repeat(100_000) { event ->
            val request = DecodeWorkCoordinator.Request(
                buffer = "n${event % 17}",
                generation = ++generation,
                session = event / 97L,
                scheme = Scheme.entries[event % 3]
            )
            val action = coordinator.enqueue(request)
            if (action is DecodeWorkCoordinator.Action.Post) {
                assertTrue(coordinator.begin(request))
            }
            if (event % 11 == 0) {
                coordinator.cancel()
                assertFalse(coordinator.begin(request))
            } else {
                coordinator.finish(request)
            }
        }
    }
}
