package com.hkmixedkeyboard.ime

class SimplifiedOutputToggleController(
    private val isConverterReady: () -> Boolean,
    private val apply: (Result) -> Unit,
    private val showLoading: () -> Unit
) {
    data class Result(val enabled: Boolean, val message: String)

    fun toggle(currentlyEnabled: Boolean) {
        when {
            currentlyEnabled -> apply(Result(enabled = false, message = "繁體輸出：開"))
            isConverterReady() -> apply(Result(enabled = true, message = "簡體輸出：開"))
            else -> showLoading()
        }
    }
}
