package com.hkmixedkeyboard.ime

class SimplifiedOutputSettingsCoordinator(
    private val loadConverter: ((Boolean) -> Unit) -> Unit,
    private val apply: (Boolean) -> Unit
) {
    private val lock = Any()
    private var generation = 0L

    fun onSetting(enabled: Boolean) {
        if (!enabled) {
            synchronized(lock) {
                ++generation
                apply(false)
            }
            return
        }

        val requestGeneration = synchronized(lock) { ++generation }
        loadConverter { loaded ->
            if (!loaded) return@loadConverter
            synchronized(lock) {
                if (generation == requestGeneration) apply(true)
            }
        }
    }
}
