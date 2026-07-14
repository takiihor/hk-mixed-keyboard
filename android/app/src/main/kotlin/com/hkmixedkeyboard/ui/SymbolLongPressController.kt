package com.hkmixedkeyboard.ui

/**
 * Owns the tap-versus-long-press decision for a symbol key. It never emits the
 * base key until a short tap is released, so opening or cancelling a variants
 * popup cannot produce duplicate input.
 */
class SymbolLongPressController(private val scheduler: Scheduler) {

    fun interface Cancellable {
        fun cancel()
    }

    fun interface Scheduler {
        fun schedule(delayMs: Long, action: () -> Unit): Cancellable
    }

    private var pending: Cancellable? = null
    private var activeKey: SymbolKeySpec? = null
    private var baseAction: ((SymbolKeySpec) -> Unit)? = null
    private var alternativeAction: ((SymbolKeySpec) -> Unit)? = null
    private var selectedAlternative: SymbolKeySpec? = null
    private var movedOutside = false

    var popupVisible: Boolean = false
        private set
    var onPopupVisibilityChanged: ((Boolean) -> Unit)? = null

    fun press(
        key: SymbolKeySpec,
        onBase: (SymbolKeySpec) -> Unit,
        onAlternative: (SymbolKeySpec) -> Unit
    ) {
        clear()
        activeKey = key
        baseAction = onBase
        alternativeAction = onAlternative
        if (key.longPressAlternatives.isNotEmpty()) {
            pending = scheduler.schedule(LONG_PRESS_DELAY_MS) {
                pending = null
                if (!movedOutside && activeKey === key) setPopupVisible(true)
            }
        }
    }

    fun selectAlternative(alternative: SymbolKeySpec?) {
        selectedAlternative = if (popupVisible) alternative else null
    }

    fun moveOutside() {
        movedOutside = true
        if (!popupVisible) pending?.cancel()
        pending = null
    }

    fun release(releasedOnBase: Boolean) {
        pending?.cancel()
        pending = null
        val alternative = if (popupVisible) selectedAlternative else null
        when {
            alternative != null -> alternativeAction?.invoke(alternative)
            !popupVisible && !movedOutside && releasedOnBase -> activeKey?.let { baseAction?.invoke(it) }
        }
        clear()
    }

    fun cancel() = clear()

    private fun clear() {
        pending?.cancel()
        pending = null
        activeKey = null
        baseAction = null
        alternativeAction = null
        selectedAlternative = null
        movedOutside = false
        setPopupVisible(false)
    }

    private fun setPopupVisible(visible: Boolean) {
        if (popupVisible == visible) return
        popupVisible = visible
        onPopupVisibilityChanged?.invoke(visible)
    }

    companion object {
        const val LONG_PRESS_DELAY_MS = 450L
    }
}
