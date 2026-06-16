package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.TypingHapticBackend
import com.hkmixedkeyboard.ui.TypingHapticEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class TypingHapticEngineTest {

    @Test
    fun `repeated taps cancel previous vibration before each tick`() {
        val backend = FakeBackend()
        val engine = TypingHapticEngine(backend)

        engine.perform(enabled = true, viewFallback = { backend.events += "view" })
        engine.perform(enabled = true, viewFallback = { backend.events += "view" })

        assertEquals(listOf("cancel", "primitive", "cancel", "primitive"), backend.events)
    }

    @Test
    fun `cancel-before-tick can be disabled`() {
        val backend = FakeBackend()
        val engine = TypingHapticEngine(backend, cancelBeforeTick = false)

        engine.perform(enabled = true, viewFallback = { backend.events += "view" })
        engine.perform(enabled = true, viewFallback = { backend.events += "view" })

        assertEquals(listOf("primitive", "primitive"), backend.events)
    }

    @Test
    fun `predefined tick is used when primitive is unsupported`() {
        val backend = FakeBackend(primitiveTickSupported = false)
        val engine = TypingHapticEngine(backend)

        engine.perform(enabled = true, viewFallback = { backend.events += "view" })

        assertEquals(listOf("cancel", "predefined"), backend.events)
    }

    @Test
    fun `no vibrator uses view fallback`() {
        val backend = FakeBackend(vibratorAvailable = false)
        val engine = TypingHapticEngine(backend)

        engine.perform(enabled = true, viewFallback = { backend.events += "view" })

        assertEquals(listOf("view"), backend.events)
    }

    @Test
    fun `disabled feedback does nothing`() {
        val backend = FakeBackend()
        val engine = TypingHapticEngine(backend)

        engine.perform(enabled = false, viewFallback = { backend.events += "view" })

        assertEquals(emptyList<String>(), backend.events)
    }

    @Test
    fun `direct vibration failure falls back without escaping`() {
        val backend = FakeBackend(failPrimitiveTick = true)
        val engine = TypingHapticEngine(backend)

        engine.perform(enabled = true, viewFallback = { backend.events += "view" })

        assertEquals(listOf("cancel", "primitive", "view"), backend.events)
    }

    @Test
    fun `capability query failure falls back without escaping`() {
        val backend = FakeBackend(failCapabilityQuery = true)
        val engine = TypingHapticEngine(backend)

        engine.perform(enabled = true, viewFallback = { backend.events += "view" })

        assertEquals(listOf("view"), backend.events)
    }

    @Test
    fun `detach cancellation never propagates backend failure`() {
        val backend = FakeBackend(failCancel = true)
        val engine = TypingHapticEngine(backend)

        engine.cancel()

        assertEquals(listOf("cancel"), backend.events)
    }

    @Test
    fun `cancel failure during tick does not block the tick`() {
        val backend = FakeBackend(failCancel = true)
        val engine = TypingHapticEngine(backend)

        engine.perform(enabled = true, viewFallback = { backend.events += "view" })

        // cancel attempt + primitive tick both succeed, cancel failure swallowed
        assertEquals(listOf("cancel", "primitive"), backend.events)
    }

    private class FakeBackend(
        private val vibratorAvailable: Boolean = true,
        private val failCancel: Boolean = false,
        private val primitiveTickSupported: Boolean = true,
        private val predefinedTickSupported: Boolean = true,
        private val failPrimitiveTick: Boolean = false,
        private val failCapabilityQuery: Boolean = false
    ) : TypingHapticBackend {
        val events = mutableListOf<String>()

        override val hasVibrator: Boolean
            get() {
                if (failCapabilityQuery) throw SecurityException("query denied")
                return vibratorAvailable
            }

        override val supportsPrimitiveTick: Boolean
            get() = primitiveTickSupported

        override val supportsPredefinedTick: Boolean
            get() = predefinedTickSupported

        override fun cancel() {
            events += "cancel"
            if (failCancel) throw SecurityException("cancel denied")
        }

        override fun vibratePrimitiveTick() {
            events += "primitive"
            if (failPrimitiveTick) throw IllegalStateException("vibrator unavailable")
        }

        override fun vibratePredefinedTick() {
            events += "predefined"
        }
    }
}