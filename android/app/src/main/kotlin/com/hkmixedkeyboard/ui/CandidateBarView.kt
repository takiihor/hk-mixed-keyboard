package com.hkmixedkeyboard.ui

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.hkmixedkeyboard.R
import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate

class CandidateBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {

    interface CandidateListener {
        fun onCandidateTap(candidate: DecodeCandidate)
        fun onExpandTap()
    }

    /**
     * Optional action chip shown when the bar is otherwise idle (no candidates,
     * no predictions): the 繁▸簡 output toggle. Set once by the IME; [clear]
     * renders it. Assign before the first [clear] and re-assign (then [clear])
     * to refresh the label after the state changes.
     */
    data class IdleAction(val label: String, val onTap: () -> Unit)

    var idleAction: IdleAction? = null
    var candidateListener: CandidateListener? = null
    var vibrationEnabled: Boolean = true
    // Shared low-latency haptic engine, injected by the IME service.
    var haptics: TypingHapticEngine? = null
    private var renderSnapshot: CandidateRenderSnapshot? = null

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

    private val row = LinearLayout(context).also {
        it.orientation = LinearLayout.HORIZONTAL
        it.gravity = Gravity.CENTER_VERTICAL
        addView(it, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    private val colorText    = ContextCompat.getColor(context, R.color.cand_text)
    private val colorHkText  = ContextCompat.getColor(context, R.color.cand_text_hk)
    private val colorEnPill  = ContextCompat.getColor(context, R.color.key_bg)

    private val textSizeSp = 24f
    private val padH = dp(18)
    private val padV = dp(6)

    fun showSafeMode() {
        renderSnapshot = null
        row.removeAllViews()
        row.addView(makeLabel(context.getString(R.string.safe_mode_active),
            ContextCompat.getColor(context, R.color.safe_mode_text)))
        setBackgroundColor(ContextCompat.getColor(context, R.color.safe_mode_bg))
    }

    // Shown briefly on a cold start while the dictionary is still warming, so the
    // bar isn't blank when a code is typed before candidates are ready. The pending
    // decode replaces it as soon as the corpus is loaded.
    fun showLoading() {
        renderSnapshot = null
        row.removeAllViews()
        row.addView(makeLabel(context.getString(R.string.corpus_loading), colorText))
        setBackgroundColor(ContextCompat.getColor(context, R.color.cand_bg))
    }

    fun setCandidates(candidates: List<DecodeCandidate>) {
        if (candidates.isEmpty()) {
            clear() // idle: also renders the idleAction chip
            return
        }
        val nextSnapshot = CandidateRenderSnapshot.from(candidates)
        if (nextSnapshot == renderSnapshot) return
        renderSnapshot = nextSnapshot
        setBackgroundColor(ContextCompat.getColor(context, R.color.cand_bg))

        val desiredCount = candidates.size + 1
        while (row.childCount < desiredCount) {
            row.addView(TextView(context))
        }
        while (row.childCount > desiredCount) {
            row.removeViewAt(row.childCount - 1)
        }
        candidates.forEachIndexed { index, cand ->
            bindCandidateView(row.getChildAt(index) as TextView, cand)
        }
        bindExpandView(row.getChildAt(candidates.size) as TextView)
        scrollTo(0, 0)
    }

    fun clear() {
        renderSnapshot = null
        row.removeAllViews()
        setBackgroundColor(ContextCompat.getColor(context, R.color.cand_bg))
        idleAction?.let { action ->
            row.addView(TextView(context).apply {
                text = action.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(colorText)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(colorEnPill)
                }
                setPadding(dp(14), dp(4), dp(14), dp(4))
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = false
                layoutParams = candidateLayoutParams(dp(8))
                setOnClickListener {
                    selectionHaptic(this)
                    action.onTap()
                }
            })
        }
    }

    private fun bindCandidateView(tv: TextView, cand: DecodeCandidate) {
        bindLabel(tv, cand.text)
        if (cand.type == CandidateType.EN_LITERAL) {
            tv.setTextColor(colorText)
            tv.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(colorEnPill)
            }
            tv.setPadding(dp(14), dp(4), dp(14), dp(4))
            tv.layoutParams = candidateLayoutParams(dp(4))
        } else {
            tv.background = null
            tv.setTextColor(
                if (CandidateVisualPolicy.isPriority(cand)) colorHkText
                else colorText
            )
            tv.setPadding(padH, padV, padH, padV)
            tv.layoutParams = candidateLayoutParams(0)
        }
        tv.setOnClickListener {
            selectionHaptic(tv)
            candidateListener?.onCandidateTap(cand)
        }
    }

    private fun bindExpandView(tv: TextView) {
        bindLabel(tv, "▾")
        tv.setTextColor(colorText)
        tv.background = null
        tv.setPadding(padH, padV, padH, padV)
        tv.layoutParams = candidateLayoutParams(0)
        tv.setOnClickListener {
            selectionHaptic(tv)
            candidateListener?.onExpandTap()
        }
    }

    private fun bindLabel(tv: TextView, value: String) {
        tv.text = value
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        tv.gravity = Gravity.CENTER_VERTICAL
        tv.isFocusable = false
    }

    private fun candidateLayoutParams(margin: Int) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
            setMargins(margin, 0, margin, 0)
        }

    private fun makeLabel(text: String, color: Int) = TextView(context).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        setPadding(padH, padV, padH, padV)
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = false
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
}
