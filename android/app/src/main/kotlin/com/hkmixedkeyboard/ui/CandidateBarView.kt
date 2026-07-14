package com.hkmixedkeyboard.ui

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
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

    var candidateListener: CandidateListener? = null
    var vibrationEnabled: Boolean = true
    // Shared low-latency haptic engine, injected by the IME service.
    var haptics: TypingHapticEngine? = null
    private var renderSnapshot: CandidateRenderSnapshot? = null
    private var displayedCandidates: List<DecodeCandidate> = emptyList()
    private var contentState = ContentState.EMPTY

    var themeColors: KeyboardThemeColors = KeyboardThemeColors.from(context)
        set(value) {
            field = value
            applyTheme()
        }

    private enum class ContentState { EMPTY, LOADING, SAFE_MODE, CANDIDATES }

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

    private val colorText get() = themeColors.candidateText
    private val colorHkText get() = themeColors.candidatePriorityText
    private val colorEnPill get() = themeColors.candidatePillBackground

    private val textSizeSp = 24f
    private val padH = dp(18)
    private val padV = dp(6)

    fun showSafeMode() {
        renderSnapshot = null
        displayedCandidates = emptyList()
        contentState = ContentState.SAFE_MODE
        row.removeAllViews()
        row.addView(makeLabel(context.getString(R.string.safe_mode_active),
            themeColors.safeModeText))
        setBackgroundColor(themeColors.safeModeBackground)
    }

    // Shown briefly on a cold start while the dictionary is still warming, so the
    // bar isn't blank when a code is typed before candidates are ready. The pending
    // decode replaces it as soon as the corpus is loaded.
    fun showLoading() {
        renderSnapshot = null
        displayedCandidates = emptyList()
        contentState = ContentState.LOADING
        row.removeAllViews()
        row.addView(makeLabel(context.getString(R.string.corpus_loading), colorText))
        setBackgroundColor(themeColors.candidateBackground)
    }

    fun setCandidates(candidates: List<DecodeCandidate>) {
        if (candidates.isEmpty()) {
            clear()
            return
        }
        val nextSnapshot = CandidateRenderSnapshot.from(candidates)
        if (nextSnapshot == renderSnapshot) return
        renderSnapshot = nextSnapshot
        displayedCandidates = candidates
        contentState = ContentState.CANDIDATES
        setBackgroundColor(themeColors.candidateBackground)

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
        displayedCandidates = emptyList()
        contentState = ContentState.EMPTY
        row.removeAllViews()
        setBackgroundColor(themeColors.candidateBackground)
    }

    private fun applyTheme() {
        if (contentState == ContentState.SAFE_MODE) {
            setBackgroundColor(themeColors.safeModeBackground)
            (row.getChildAt(0) as? TextView)?.setTextColor(themeColors.safeModeText)
            return
        }
        setBackgroundColor(themeColors.candidateBackground)
        when (contentState) {
            ContentState.CANDIDATES -> displayedCandidates.forEachIndexed { index, candidate ->
                (row.getChildAt(index) as? TextView)?.let { bindCandidateView(it, candidate) }
            }
            ContentState.LOADING -> (row.getChildAt(0) as? TextView)?.setTextColor(colorText)
            else -> Unit
        }
        if (contentState == ContentState.CANDIDATES) {
            (row.getChildAt(displayedCandidates.size) as? TextView)?.let(::bindExpandView)
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
