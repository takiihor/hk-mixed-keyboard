package com.hkmixedkeyboard.ui

enum class TypingHapticStrategy {
    NONE,
    PRIMITIVE_TICK,
    PREDEFINED_TICK,
    VIEW_FALLBACK
}

object HapticCapabilityPolicy {
    fun select(
        enabled: Boolean,
        hasVibrator: Boolean,
        supportsPrimitiveTick: Boolean,
        supportsPredefinedTick: Boolean
    ): TypingHapticStrategy = when {
        !enabled -> TypingHapticStrategy.NONE
        hasVibrator && supportsPrimitiveTick -> TypingHapticStrategy.PRIMITIVE_TICK
        hasVibrator && supportsPredefinedTick -> TypingHapticStrategy.PREDEFINED_TICK
        else -> TypingHapticStrategy.VIEW_FALLBACK
    }
}
