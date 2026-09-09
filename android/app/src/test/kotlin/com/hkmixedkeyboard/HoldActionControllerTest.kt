package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.HoldActionController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldActionControllerTest {

    @Test
    fun `repeating press emits immediately then repeats until release`() {
        val scheduler = FakeScheduler()
        val controller = HoldActionController(scheduler)
        var deletes = 0

        controller.pressRepeating(action = { deletes++ })
        assertEquals(1, deletes)

        scheduler.advanceBy(HoldActionController.REPEAT_INITIAL_DELAY_MS)
        assertEquals(2, deletes)

        scheduler.advanceBy(HoldActionController.REPEAT_INTERVAL_MS * 2)
        assertEquals(4, deletes)

        controller.release()
        scheduler.advanceBy(HoldActionController.REPEAT_INTERVAL_MS * 2)
        assertEquals(4, deletes)
    }

    @Test
    fun `short question press emits question on release`() {
        val scheduler = FakeScheduler()
        val controller = HoldActionController(scheduler)
        val emitted = mutableListOf<String>()

        controller.pressLong(tap = { emitted += "？" }, longPress = { emitted += "！" })
        scheduler.advanceBy(HoldActionController.LONG_PRESS_DELAY_MS - 1)
        controller.release()

        assertEquals(listOf("？"), emitted)
    }

    @Test
    fun `long question press emits exclamation and suppresses question`() {
        val scheduler = FakeScheduler()
        val controller = HoldActionController(scheduler)
        val emitted = mutableListOf<String>()

        controller.pressLong(tap = { emitted += "？" }, longPress = { emitted += "！" })
        scheduler.advanceBy(HoldActionController.LONG_PRESS_DELAY_MS)
        controller.release()

        assertEquals(listOf("！"), emitted)
    }

    private class FakeScheduler : HoldActionController.Scheduler {
        private data class Job(
            val at: Long,
            val action: () -> Unit,
            var cancelled: Boolean = false
        ) : HoldActionController.Cancellable {
            override fun cancel() {
                cancelled = true
            }
        }

        private var now = 0L
        private val jobs = mutableListOf<Job>()

        override fun schedule(delayMs: Long, action: () -> Unit): HoldActionController.Cancellable {
            return Job(now + delayMs, action).also { jobs += it }
        }

        fun advanceBy(ms: Long) {
            val target = now + ms
            while (true) {
                val next = jobs
                    .filter { !it.cancelled && it.at <= target }
                    .minByOrNull { it.at }
                    ?: break
                jobs.remove(next)
                now = next.at
                next.action()
            }
            now = target
        }
    }

    // ── Accelerating and escalating repeat ────────────────────────────────

    @Test
    fun `character repeat speeds up while the key is held`() {
        val scheduler = FakeScheduler()
        val controller = HoldActionController(scheduler)
        var deletes = 0

        controller.pressRepeating(action = { deletes++ })
        // Past the initial delay, then through the slow phase into the fast one.
        scheduler.advanceBy(HoldActionController.REPEAT_INITIAL_DELAY_MS)
        repeat(HoldActionController.ACCELERATE_AFTER_REPEATS) {
            scheduler.advanceBy(HoldActionController.REPEAT_INTERVAL_MS)
        }
        val beforeFast = deletes
        scheduler.advanceBy(HoldActionController.FAST_REPEAT_INTERVAL_MS)

        assertEquals("a fast-phase tick should still delete", beforeFast + 1, deletes)
    }

    @Test
    fun `a long hold escalates from characters to whole words`() {
        val scheduler = FakeScheduler()
        val controller = HoldActionController(scheduler)
        var chars = 0
        var words = 0

        controller.pressRepeating(action = { chars++ }, onEscalate = { words++ })
        scheduler.advanceBy(HoldActionController.REPEAT_INITIAL_DELAY_MS)
        repeat(HoldActionController.ESCALATE_AFTER_REPEATS + 4) {
            scheduler.advanceBy(HoldActionController.REPEAT_INTERVAL_MS)
        }

        assertTrue("expected escalation to whole words, got none", words > 0)
        assertTrue("characters should still have gone first", chars > 0)
    }

    @Test
    fun `without an escalation callback the repeat stays on characters`() {
        val scheduler = FakeScheduler()
        val controller = HoldActionController(scheduler)
        var chars = 0

        controller.pressRepeating(action = { chars++ })
        scheduler.advanceBy(HoldActionController.REPEAT_INITIAL_DELAY_MS)
        repeat(HoldActionController.ESCALATE_AFTER_REPEATS + 10) {
            scheduler.advanceBy(HoldActionController.REPEAT_INTERVAL_MS)
        }

        assertTrue(chars > HoldActionController.ESCALATE_AFTER_REPEATS)
    }
}
