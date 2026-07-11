package com.hkmixedkeyboard.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Symbol page shown when the 符 key is pressed. Replaces the keyboard while staying
 * open (tap several symbols in a row), and keeps a standard bottom function row so
 * the user can space / delete / Enter / return to letters without leaving the page.
 *
 * Layout: 40 curated symbols in 4 rows of 10. The first row follows the common
 * Gboard-style priority of half-width ASCII punctuation (so "." is directly
 * available), while the second row keeps Chinese full-width punctuation close by
 * for HK text. Digits are omitted; the main keyboard already has a number row.
 */
class SymbolPageView(context: Context) : LinearLayout(context) {

    var onSymbolTap: ((String) -> Unit)? = null
    var onSpace: (() -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onEnter: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var vibrationEnabled: Boolean = true
    // Shared low-latency haptic engine, injected by the IME service.
    var haptics: TypingHapticEngine? = null

    private fun selectionHaptic(view: View) {
        val engine = haptics
        if (engine != null) {
            engine.perform(enabled = vibrationEnabled) {
                HapticFeedbackPolicy.performSelection(view, enabled = true)
            }
        } else {
            HapticFeedbackPolicy.performSelection(view, vibrationEnabled)
        }
    }

    companion object {
        const val RETURN_LABEL = "ABC"

        val SYMBOL_ROWS = listOf(
            listOf(".", ",", "?", "!", "'", "\"", ":", ";", "/", "\\"),
            listOf("。", "、", "·", "「", "」", "『", "』", "《", "》", "…"),
            listOf("@", "#", "$", "%", "&", "*", "-", "+", "=", "_"),
            listOf("(", ")", "[", "]", "{", "}", "<", ">", "~", "|")
        )
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF202124.toInt())
        setPadding(dp(3), dp(3), dp(3), dp(3))

        // 4 symbol rows, each filling an equal share of the height so the cells
        // match the alpha-key size.
        SYMBOL_ROWS.forEach { row -> addView(buildSymbolRow(row), rowParams(weight = 1f)) }

        // Function row, same height as a symbol row.
        addView(buildFunctionRow(), rowParams(weight = 1f))
    }

    private fun rowParams(weight: Float) =
        LayoutParams(LayoutParams.MATCH_PARENT, 0, weight)

    private fun buildSymbolRow(symbols: List<String>): View = LinearLayout(context).apply {
        orientation = HORIZONTAL
        symbols.forEach { sym -> addView(symbolCell(sym), cellParams(weight = 1f)) }
    }

    private fun cellParams(weight: Float) =
        LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }

    private fun symbolCell(sym: String) = TextView(context).apply {
        text = sym
        setTextColor(0xFFE8EAED.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        gravity = Gravity.CENTER
        background = keyBackground(0xFF3C4043.toInt())
        setOnClickListener {
            selectionHaptic(this)
            onSymbolTap?.invoke(sym)
        }
    }

    // Bottom function row: 返回 (back to letters) | 空格 | ⌫ | ↵, mirroring the main
    // keyboard's bottom row so the symbol page is usable on its own.
    private fun buildFunctionRow(): View = LinearLayout(context).apply {
        orientation = HORIZONTAL
        addView(functionKey(RETURN_LABEL) { onClose?.invoke() }, cellParams(weight = 2f))
        addView(functionKey("空格") { onSpace?.invoke() }, cellParams(weight = 4.5f))
        addView(functionKey("⌫") { onBackspace?.invoke() }, cellParams(weight = 1.75f))
        addView(functionKey("↵") { onEnter?.invoke() }, cellParams(weight = 1.75f))
    }

    private fun functionKey(label: String, onTap: () -> Unit) = TextView(context).apply {
        text = label
        setTextColor(0xFFE8EAED.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        gravity = Gravity.CENTER
        background = keyBackground(0xFF2A2C2E.toInt())
        setOnClickListener {
            selectionHaptic(this)
            onTap()
        }
    }

    private fun keyBackground(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(8).toFloat()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
}
