package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.SymbolKeySpec
import com.hkmixedkeyboard.ui.SymbolLongPressController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolLongPressTest {

    @Test
    fun `normal release emits the base key exactly once`() {
        val scheduler = FakeScheduler()
        val controller = SymbolLongPressController(scheduler)
        val committed = mutableListOf<String>()

        controller.press(base(), onBase = { committed += it.commitText!! }, onAlternative = { committed += it.commitText!! })
        controller.release(releasedOnBase = true)

        assertEquals(listOf("，"), committed)
    }

    @Test
    fun `long press never commits base before a selected alternative`() {
        val scheduler = FakeScheduler()
        val controller = SymbolLongPressController(scheduler)
        val committed = mutableListOf<String>()
        val alternatives = listOf(key(","), key("、"))

        controller.press(base(alternatives), onBase = { committed += it.commitText!! }, onAlternative = { committed += it.commitText!! })
        scheduler.advanceBy(SymbolLongPressController.LONG_PRESS_DELAY_MS)

        assertTrue(controller.popupVisible)
        assertEquals(emptyList<String>(), committed)
        controller.selectAlternative(alternatives[1])
        controller.release(releasedOnBase = false)

        assertEquals(listOf("、"), committed)
        assertFalse(controller.popupVisible)
    }

    @Test
    fun `long press cancel or moving away commits nothing`() {
        val scheduler = FakeScheduler()
        val controller = SymbolLongPressController(scheduler)
        val committed = mutableListOf<String>()
        val alternatives = listOf(key(","))

        controller.press(base(alternatives), onBase = { committed += it.commitText!! }, onAlternative = { committed += it.commitText!! })
        scheduler.advanceBy(SymbolLongPressController.LONG_PRESS_DELAY_MS)
        controller.cancel()

        controller.press(base(alternatives), onBase = { committed += it.commitText!! }, onAlternative = { committed += it.commitText!! })
        controller.moveOutside()
        controller.release(releasedOnBase = false)

        assertEquals(emptyList<String>(), committed)
    }

    private fun base(alternatives: List<SymbolKeySpec> = emptyList()) = SymbolKeySpec(
        label = "，",
        commitText = "，",
        accessibilityLabel = "中文逗號",
        longPressAlternatives = alternatives
    )

    private fun key(text: String) = SymbolKeySpec(text, text, text)

    private class FakeScheduler : SymbolLongPressController.Scheduler {
        private data class Job(
            val at: Long,
            val action: () -> Unit,
            var cancelled: Boolean = false
        ) : SymbolLongPressController.Cancellable {
            override fun cancel() {
                cancelled = true
            }
        }

        private var now = 0L
        private val jobs = mutableListOf<Job>()

        override fun schedule(delayMs: Long, action: () -> Unit): SymbolLongPressController.Cancellable =
            Job(now + delayMs, action).also { jobs += it }

        fun advanceBy(ms: Long) {
            val target = now + ms
            while (true) {
                val next = jobs.filter { !it.cancelled && it.at <= target }.minByOrNull { it.at } ?: break
                jobs.remove(next)
                now = next.at
                next.action()
            }
            now = target
        }
    }
}
