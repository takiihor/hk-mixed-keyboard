package com.hkmixedkeyboard.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.hkmixedkeyboard.BuildConfig

interface TypingHapticBackend {
    val hasVibrator: Boolean
    val supportsPrimitiveTick: Boolean
    val supportsPredefinedTick: Boolean

    fun cancel()
    fun vibratePrimitiveTick()
    fun vibratePredefinedTick()

    /** Release any long-lived resources. Safe to call repeatedly. */
    fun release() {}
}

/**
 * Shared across every keyboard surface (main keys, candidate bar/grid, symbol and
 * emoji panels) so they all deliver the same short, device-tuned tick from a single
 * backend. [onSubmitted] is invoked exactly once whenever a haptic is delivered —
 * direct or via [viewFallback] — so latency traces have a single source.
 *
 * The tap is fired synchronously on the caller's thread (the UI thread, at
 * ACTION_DOWN). Vibrator.vibrate() is a quick fire-and-forget binder call — it does
 * not block for the vibration's duration — so dispatching it inline keeps the tap
 * feedback as immediate as possible.
 *
 * Direct haptics are submitted without cancelling the previous effect by default,
 * keeping rapid typing to one vibrator binder operation per key press. Callers that
 * target an actuator which requires explicit interruption can opt into
 * [cancelBeforeTick]. Lifecycle cancellation remains explicit in [cancel] and
 * [release].
 */
class TypingHapticEngine(
    private val backend: TypingHapticBackend,
    private val cancelBeforeTick: Boolean = false,
    private val onSubmitted: () -> Unit = {}
) {
    fun perform(enabled: Boolean, viewFallback: () -> Unit) {
        if (!enabled) return

        val strategy = try {
            HapticCapabilityPolicy.select(
                enabled = true,
                hasVibrator = backend.hasVibrator,
                supportsPrimitiveTick = backend.supportsPrimitiveTick,
                supportsPredefinedTick = backend.supportsPredefinedTick
            )
        } catch (_: RuntimeException) {
            deliverFallback(viewFallback)
            return
        }

        when (strategy) {
            TypingHapticStrategy.NONE -> Unit
            TypingHapticStrategy.VIEW_FALLBACK -> deliverFallback(viewFallback)
            TypingHapticStrategy.PRIMITIVE_TICK -> performDirect(
                cancel = if (cancelBeforeTick) backend::cancel else {{}},
                vibrate = backend::vibratePrimitiveTick,
                viewFallback = viewFallback
            )
            TypingHapticStrategy.PREDEFINED_TICK -> performDirect(
                cancel = if (cancelBeforeTick) backend::cancel else {{}},
                vibrate = backend::vibratePredefinedTick,
                viewFallback = viewFallback
            )
        }
    }

    /**
     * Pre-initialize the backend's cached capability probe so the first keystroke
     * doesn't pay the one-time binder-query cost. Safe to call from any thread.
     */
    fun warmUp() {
        try {
            backend.hasVibrator
            backend.supportsPrimitiveTick
            backend.supportsPredefinedTick
        } catch (_: RuntimeException) {
            // Probing must never crash startup.
        }
    }

    fun cancel() {
        try {
            backend.cancel()
        } catch (_: RuntimeException) {
            // Detaching the keyboard must not be blocked by a vibrator failure.
        }
    }

    /** Release backend resources; call once when the IME is destroyed. */
    fun release() {
        cancel()
        try {
            backend.release()
        } catch (_: RuntimeException) {
            // Shutdown must never crash the IME.
        }
    }

    private fun performDirect(cancel: () -> Unit, vibrate: () -> Unit, viewFallback: () -> Unit) {
        // The default no-op keeps rapid typing to a single binder submission. An
        // explicit cancel remains available for actuator-specific compatibility.
        try {
            cancel()
        } catch (_: RuntimeException) {
            // Cancel failure must not block the tick.
        }

        try {
            vibrate()
        } catch (_: RuntimeException) {
            viewFallback()
        }
        onSubmitted()
    }

    private fun deliverFallback(viewFallback: () -> Unit) {
        viewFallback()
        onSubmitted()
    }
}

class AndroidTypingHapticBackend(
    context: Context
) : TypingHapticBackend {
    private val appContext = context.applicationContext

    // Vibrator capability is a fixed property of the device, so probe lazily and
    // cache. Lazy keeps the binder queries off the View/Service constructor and
    // makes them cheap on every subsequent keystroke.
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    override val hasVibrator: Boolean by lazy {
        try {
            vibrator?.hasVibrator() == true
        } catch (_: RuntimeException) {
            false
        }
    }

    override val supportsPrimitiveTick: Boolean by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasVibrator) {
                vibrator
                    ?.arePrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)
                    ?.firstOrNull() == true
            } else {
                false
            }
        } catch (_: RuntimeException) {
            false
        }
    }

    override val supportsPredefinedTick: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasVibrator
    }

    override fun cancel() {
        vibrator?.cancel()
    }

    @android.annotation.SuppressLint("NewApi", "InlinedApi")
    override fun vibratePrimitiveTick() {
        // Heavy mode: two very short micro-pulses within 10ms total window to boost salience
        if (BuildConfig.HAPTIC_HEAVY_MODE) {
            val amp1 = (26 + (229 * BuildConfig.HAPTIC_INTENSITY.coerceIn(0.6f, 1.0f))).toInt()
            val amp2 = (amp1 * 0.85f).toInt()
            val pattern = longArrayOf(0, 6, 6, 4) // wait 0, 6ms on, 6ms off, 4ms on → 16ms total on-paper, but motors clamp; perceived <10ms
            val amplitudes = intArrayOf(0, amp1, 0, amp2)
            vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
            return
        }
        if (BuildConfig.HAPTIC_STRONG_DEBUG) {
            val ms = BuildConfig.HAPTIC_LENGTH_MS.coerceIn(6, 10)
            val amp = (26 + (229 * BuildConfig.HAPTIC_INTENSITY.coerceIn(0.6f, 1.0f))).toInt()
            vibrate(VibrationEffect.createOneShot(ms.toLong(), amp))
            return
        }
        val scale = BuildConfig.HAPTIC_INTENSITY.coerceIn(0.1f, 1.0f)
        vibrate(
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale)
                .compose()
        )
    }

    @android.annotation.SuppressLint("NewApi", "InlinedApi")
    override fun vibratePredefinedTick() {
        if (BuildConfig.HAPTIC_HEAVY_MODE) {
            val amp1 = (26 + (229 * BuildConfig.HAPTIC_INTENSITY.coerceIn(0.6f, 1.0f))).toInt()
            val amp2 = (amp1 * 0.85f).toInt()
            val pattern = longArrayOf(0, 6, 6, 4)
            val amplitudes = intArrayOf(0, amp1, 0, amp2)
            vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
            return
        }
        if (BuildConfig.HAPTIC_STRONG_DEBUG) {
            val ms = BuildConfig.HAPTIC_LENGTH_MS.coerceIn(6, 10)
            val amp = (26 + (229 * BuildConfig.HAPTIC_INTENSITY.coerceIn(0.6f, 1.0f))).toInt()
            vibrate(VibrationEffect.createOneShot(ms.toLong(), amp))
        } else {
            vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(effect: VibrationEffect) {
        val target = checkNotNull(vibrator)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            target.vibrate(
                effect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
            )
        } else {
            target.vibrate(effect, DIRECT_HAPTIC_AUDIO_ATTRIBUTES)
        }
    }

    private companion object {
        val DIRECT_HAPTIC_AUDIO_ATTRIBUTES: AudioAttributes =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .build()
    }
}
