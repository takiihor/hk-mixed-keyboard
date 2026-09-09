package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.HoldActionController
import com.hkmixedkeyboard.ui.SpaceGestureController
import org.junit.Assert.assertEquals
import org.junit.Test

class SpaceGestureControllerTest {

    @Test
    fun `space tap emits one space on release`() {
        val fixture = Fixture()

        fixture.controller.press(startX = 100f)
        fixture.controller.release(releasedInside = true)

        assertEquals(listOf("space"), fixture.events)
    }

    @Test
    fun `stationary 600ms hold opens cursor mode`() {
        val fixture = Fixture()

        fixture.controller.press(startX = 100f)
        fixture.scheduler.advanceBy(SpaceGestureController.LONG_PRESS_DELAY_MS - 1)
        assertEquals(emptyList<String>(), fixture.events)

        fixture.scheduler.advanceBy(1)

        assertEquals(listOf("cursor:on"), fixture.events)
    }

    @Test
    fun `hold then drag steers the caret, the way iOS does`() {
        val fixture = Fixture()

        fixture.controller.press(startX = 100f)
        fixture.scheduler.advanceBy(SpaceGestureController.LONG_PRESS_DELAY_MS)
        fixture.controller.move(x = 136f, holdEligible = true)
        fixture.controller.move(x = 154f, holdEligible = true)
        fixture.controller.release(releasedInside = true)

        assertEquals(
            listOf("cursor:on", "swipe:1", "swipe:1", "swipe:1", "cursor:off"),
            fixture.events
        )
    }

    @Test
    fun `a drag in cursor mode survives leaving the space bar`() {
        // iOS keeps steering once the trackpad is open, however far the finger
        // wanders; cancelling there would strand the caret mid-sentence.
        val fixture = Fixture()

        fixture.controller.press(startX = 100f)
        fixture.scheduler.advanceBy(SpaceGestureController.LONG_PRESS_DELAY_MS)
        fixture.controller.move(x = 82f, holdEligible = false)
        fixture.controller.release(releasedInside = false)

        assertEquals(listOf("cursor:on", "swipe:-1", "cursor:off"), fixture.events)
    }

    @Test
    fun `holding never emits a space on release`() {
        val fixture = Fixture()

        fixture.controller.press(startX = 100f)
        fixture.scheduler.advanceBy(SpaceGestureController.LONG_PRESS_DELAY_MS)
        fixture.controller.release(releasedInside = true)

        assertEquals(listOf("cursor:on", "cursor:off"), fixture.events)
    }

    @Test
    fun `cursor swipe cancels pending hold and only moves cursor`() {
        val fixture = Fixture()

        fixture.controller.press(startX = 100f)
        fixture.controller.move(x = 119f, holdEligible = true)
        fixture.scheduler.advanceBy(SpaceGestureController.LONG_PRESS_DELAY_MS)
        fixture.controller.release(releasedInside = true)

        assertEquals(listOf("swipe:1"), fixture.events)
    }

    @Test
    fun `vertical move outside space cancels pending hold and release`() {
        val fixture = Fixture()

        fixture.controller.press(startX = 100f)
        fixture.controller.move(x = 100f, holdEligible = false)
        fixture.scheduler.advanceBy(SpaceGestureController.LONG_PRESS_DELAY_MS)
        fixture.controller.release(releasedInside = false)

        assertEquals(emptyList<String>(), fixture.events)
    }

    @Test
    fun `cancel prevents hidden delayed hold callback`() {
        val fixture = Fixture()

        fixture.controller.press(startX = 100f)
        fixture.controller.cancel()
        fixture.scheduler.advanceBy(SpaceGestureController.LONG_PRESS_DELAY_MS)

        assertEquals(emptyList<String>(), fixture.events)
    }

    private class Fixture {
        val scheduler = FakeScheduler()
        val events = mutableListOf<String>()
        val controller = SpaceGestureController(
            holdController = HoldActionController(scheduler),
            cursorStepPx = 18f,
            onTap = { events += "space" },
            onSwipe = { events += "swipe:$it" },
            onCursorModeChanged = { events += if (it) "cursor:on" else "cursor:off" }
        )
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

        override fun schedule(delayMs: Long, action: () -> Unit): HoldActionController.Cancellable =
            Job(now + delayMs, action).also { jobs += it }

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
