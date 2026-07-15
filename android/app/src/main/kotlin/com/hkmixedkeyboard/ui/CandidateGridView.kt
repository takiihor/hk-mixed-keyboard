package com.hkmixedkeyboard.ui

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.hkmixedkeyboard.decoder.DecodeCandidate

class CandidateGridView(private val context: Context) {

    private var popup: PopupWindow? = null
    private var grid: GridLayout? = null
    private val candidateCells = mutableListOf<Pair<TextView, DecodeCandidate>>()
    var vibrationEnabled: Boolean = true
    // Shared low-latency haptic engine, injected by the IME service.
    var haptics: TypingHapticEngine? = null
    var themeColors: KeyboardThemeColors = KeyboardThemeColors.from(context)
        set(value) {
            field = value
            applyTheme()
        }
    private val glyphPaint = android.graphics.Paint()

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

    fun show(anchor: View, candidates: List<DecodeCandidate>, onTap: (DecodeCandidate) -> Unit) {
        dismiss()
        candidateCells.clear()
        val padH = dp(12)
        val padV = dp(10)
        val layoutSpec = CandidateGridLayoutPolicy.layoutSpec()

        val grid = GridLayout(context).apply {
            columnCount = layoutSpec.columnCount
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(themeColors.candidateBackground)
        }
        this.grid = grid

        candidates.forEachIndexed { index, cand ->
            val label = CandidatePresentation.label(
                cand,
                glyphPaint::hasGlyph,
                context.getString(com.hkmixedkeyboard.R.string.chinese_to_english)
            )
            val cell = TextView(context).apply {
                text = label
                contentDescription = context.getString(
                    com.hkmixedkeyboard.R.string.candidate_position,
                    index + 1,
                    candidates.size,
                    cand.text
                ) + cand.annotation?.let {
                    context.getString(com.hkmixedkeyboard.R.string.candidate_reading, it)
                }.orEmpty()
                setTextColor(candidateTextColor(cand))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setPadding(padH, padV, padH, padV)
                gravity = Gravity.CENTER
                minimumHeight = dp(48)
                isFocusable = true
                isClickable = true
                layoutParams = GridLayout.LayoutParams().apply {
                    width = layoutSpec.cellBaseWidth
                    columnSpec = GridLayout.spec(
                        GridLayout.UNDEFINED,
                        layoutSpec.cellColumnWeight
                    )
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
                background = GradientDrawable().apply {
                    setColor(themeColors.candidateSelectedBackground)
                    cornerRadius = dp(6).toFloat()
                }
                setOnClickListener {
                    selectionHaptic(this)
                    onTap(cand)
                    dismiss()
                }
            }
            candidateCells += cell to cand
            grid.addView(cell)
        }

        val scroll = ScrollView(context).apply {
            addView(
                grid,
                ViewGroup.LayoutParams(
                    layoutSpec.gridWidthMode.toLayoutSize(),
                    layoutSpec.gridHeightMode.toLayoutSize()
                )
            )
            setBackgroundColor(themeColors.candidateBackground)
        }

        val width = if (anchor.width > 0) anchor.width else ViewGroup.LayoutParams.MATCH_PARENT

        // Keep the panel strictly within the keyboard's footprint. The candidate bar
        // is the top strip of the keyboard (its parent holds bar + keys), so the key
        // area is the keyboard height minus the bar height. We pin the panel to the
        // BOTTOM of the screen at exactly that height, so it covers the keys and can
        // never float up over the app's text. (showAsDropDown could flip the panel
        // above the anchor when it thought there was not enough room below, landing
        // it in the content area.)
        val keyboardRoot = (anchor.parent as? View) ?: anchor
        val height = (keyboardRoot.height - anchor.height).coerceAtLeast(dp(140))

        // NON-focusable: a focusable popup spawned from the IME steals input focus,
        // which makes the system hide the keyboard (HIDE_WINDOW_GAINED_FOCUS_WITHOUT
        // _EDITOR) and the popup vanishes with it. Non-focusable keeps the IME up
        // while candidate cells still receive taps.
        popup = PopupWindow(scroll, width, height, false).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(themeColors.candidateBackground))
            elevation = dp(8).toFloat()
        }

        popup?.showAtLocation(keyboardRoot, Gravity.BOTTOM, 0, 0)
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
        grid = null
        candidateCells.clear()
    }

    fun isShowing() = popup?.isShowing == true

    private fun applyTheme() {
        grid?.setBackgroundColor(themeColors.candidateBackground)
        candidateCells.forEach { (cell, candidate) ->
            cell.setTextColor(candidateTextColor(candidate))
            cell.background = GradientDrawable().apply {
                setColor(themeColors.candidateSelectedBackground)
                cornerRadius = dp(6).toFloat()
            }
        }
        popup?.setBackgroundDrawable(ColorDrawable(themeColors.candidateBackground))
    }

    private fun candidateTextColor(candidate: DecodeCandidate): Int =
        if (CandidateVisualPolicy.isPriority(candidate)) themeColors.candidatePriorityText
        else themeColors.candidateText

    private fun CandidateGridSizeMode.toLayoutSize(): Int = when (this) {
        CandidateGridSizeMode.MATCH_PARENT -> ViewGroup.LayoutParams.MATCH_PARENT
        CandidateGridSizeMode.WRAP_CONTENT -> ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private fun dp(n: Int) = (n * context.resources.displayMetrics.density + 0.5f).toInt()
}
