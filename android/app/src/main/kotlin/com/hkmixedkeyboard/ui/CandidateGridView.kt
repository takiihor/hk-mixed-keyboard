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
import androidx.core.content.ContextCompat
import com.hkmixedkeyboard.R
import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate

class CandidateGridView(private val context: Context) {

    private var popup: PopupWindow? = null
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

    fun show(anchor: View, candidates: List<DecodeCandidate>, onTap: (DecodeCandidate) -> Unit) {
        dismiss()

        // Match the keyboard's dark theme so candidates stay legible and the panel
        // looks like part of the keyboard (was light cells with near-white text =
        // invisible normal candidates).
        val colorHk    = ContextCompat.getColor(context, R.color.cand_text_hk)
        val colorNorm  = ContextCompat.getColor(context, R.color.cand_text)
        val colorBg    = ContextCompat.getColor(context, R.color.keyboard_bg)
        val colorCell  = ContextCompat.getColor(context, R.color.key_bg)
        val padH = dp(12)
        val padV = dp(10)

        val grid = GridLayout(context).apply {
            columnCount = 4
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(colorBg)
        }

        candidates.forEach { cand ->
            val color = if (cand.isHkCore || cand.type == CandidateType.PHRASE ||
                cand.type == CandidateType.MIXED_PHRASE) colorHk else colorNorm
            val cell = TextView(context).apply {
                text = cand.text
                setTextColor(color)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setPadding(padH, padV, padH, padV)
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
                background = GradientDrawable().apply {
                    setColor(colorCell)
                    cornerRadius = dp(6).toFloat()
                }
                setOnClickListener {
                    selectionHaptic(this)
                    onTap(cand)
                    dismiss()
                }
            }
            grid.addView(cell)
        }

        val scroll = ScrollView(context).apply {
            addView(grid)
            setBackgroundColor(colorBg)
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
            setBackgroundDrawable(ColorDrawable(colorBg))
            elevation = dp(8).toFloat()
        }

        popup?.showAtLocation(keyboardRoot, Gravity.BOTTOM, 0, 0)
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }

    fun isShowing() = popup?.isShowing == true

    private fun dp(n: Int) = (n * context.resources.displayMetrics.density + 0.5f).toInt()
}
