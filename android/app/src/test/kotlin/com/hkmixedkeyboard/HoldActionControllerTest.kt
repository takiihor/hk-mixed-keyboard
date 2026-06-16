package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.HoldActionController
import org.junit.Assert.assertEquals
import org.junit.Test

class HoldActionControllerTest {

    @Test
    fun `repeating press emits immediately then repeats until release`() {
        val scheduler = FakeScheduler()
        val controller = HoldActionController(scheduler)
        var deletes = 0

        controller.pressRepeating { deletes++ }
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
}
