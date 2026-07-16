package com.hkmixedkeyboard.ui

enum class TypingHapticStrategy {
    NONE,
    PRIMITIVE_CLICK,
    PREDEFINED_CLICK,
    VIEW_FALLBACK
}

object HapticCapabilityPolicy {
    fun select(
        enabled: Boolean,
        hasVibrator: Boolean,
        supportsPrimitiveClick: Boolean,
        supportsPredefinedClick: Boolean
    ): TypingHapticStrategy = when {
        !enabled -> TypingHapticStrategy.NONE
        hasVibrator && supportsPrimitiveClick -> TypingHapticStrategy.PRIMITIVE_CLICK
        hasVibrator && supportsPredefinedClick -> TypingHapticStrategy.PREDEFINED_CLICK
        else -> TypingHapticStrategy.VIEW_FALLBACK
    }
}
