package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.TypingHapticBackend
import com.hkmixedkeyboard.ui.TypingHapticEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TypingHapticEngineTest {

    @Test
    fun `test builds use device-tuned haptic effects`() {
        assertFalse(BuildConfig.HAPTIC_STRONG_DEBUG)
    }

    @Test
    fun `repeated taps do not cancel device-tuned haptics`() {
        val backend = FakeBackend()
        val engine = TypingHapticEngine(backend)

        engine.perform(enabled = true, viewFallback = { backend.events += "view" })
        engine.perform(enabled = true, viewFallback = { backend.events += "view" })

        assertEquals(listOf("primitiveClick", "primitiveClick"), backend.events)
    }

    @Test
    fun `cancel-before-tick remains available when explicitly enabled`() {
        val backend = FakeBackend()
        val engine = TypingHapticEngine(backend, cancelBeforeTick = true)

        engine.perform(enabled = true, viewFallback = { backend.events += "view" })
        engine.perform(enabled = true, viewFallback = { backend.events += "view" })

        assertEquals(listOf("cancel", "primitiveClick", "cancel", "primitiveClick"), backend.events)
    }

    @Test
    fun `predefined click is used when primitive is unsupported`() {
        val backend = FakeBackend(primitiveClickSupported = false)
        val engine = TypingHapticEngine(backend)

        engine.perform(enabled = true, viewFallback = { backend.events += "view" })

        assertEquals(listOf("predefinedClick"), backend.events)
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
        val backend = FakeBackend(failPrimitiveClick = true)
        val engine = TypingHapticEngine(backend)

        engine.perform(enabled = true, viewFallback = { backend.events += "view" })

        assertEquals(listOf("primitiveClick", "view"), backend.events)
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
    fun `cancel failure during click does not block the click`() {
        val backend = FakeBackend(failCancel = true)
        val engine = TypingHapticEngine(backend, cancelBeforeTick = true)

        engine.perform(enabled = true, viewFallback = { backend.events += "view" })

        // cancel attempt + primitive click both succeed, cancel failure swallowed
        assertEquals(listOf("cancel", "primitiveClick"), backend.events)
    }

    private class FakeBackend(
        private val vibratorAvailable: Boolean = true,
        private val failCancel: Boolean = false,
        private val primitiveClickSupported: Boolean = true,
        private val predefinedClickSupported: Boolean = true,
        private val failPrimitiveClick: Boolean = false,
        private val failCapabilityQuery: Boolean = false
    ) : TypingHapticBackend {
        val events = mutableListOf<String>()

        override val hasVibrator: Boolean
            get() {
                if (failCapabilityQuery) throw SecurityException("query denied")
                return vibratorAvailable
            }

        override val supportsPrimitiveClick: Boolean
            get() = primitiveClickSupported

        override val supportsPredefinedClick: Boolean
            get() = predefinedClickSupported

        override fun cancel() {
            events += "cancel"
            if (failCancel) throw SecurityException("cancel denied")
        }

        override fun vibratePrimitiveClick() {
            events += "primitiveClick"
            if (failPrimitiveClick) throw IllegalStateException("vibrator unavailable")
        }

        override fun vibratePredefinedClick() {
            events += "predefinedClick"
        }
    }
}
