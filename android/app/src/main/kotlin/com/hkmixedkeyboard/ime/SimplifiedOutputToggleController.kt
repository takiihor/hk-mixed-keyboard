package com.hkmixedkeyboard.ime

class SimplifiedOutputToggleController(
    private val isConverterReady: () -> Boolean,
    private val apply: (Result) -> Unit,
    private val showLoading: () -> Unit
) {
    data class Result(val enabled: Boolean)

    fun toggle(currentlyEnabled: Boolean) {
        when {
            currentlyEnabled -> apply(Result(enabled = false))
            isConverterReady() -> apply(Result(enabled = true))
            else -> showLoading()
        }
    }
}
