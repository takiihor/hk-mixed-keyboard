package com.hkmixedkeyboard.ui

import android.content.Context
import android.text.TextUtils
import android.graphics.Canvas
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

    data class AuxiliaryAction(
        val label: String,
        val contentDescription: String,
        val onClick: () -> Unit
    )

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
    private var systemMessageStyle: SystemMessageStyle? = null
    private val glyphPaint = android.graphics.Paint()
    private var candidateRenderMetricPending = false

    var displayState: CandidateBarDisplayState = CandidateBarDisplayState.EMPTY
        private set

    var themeColors: KeyboardThemeColors = KeyboardThemeColors.from(context)
        set(value) {
            field = value
            applyTheme()
        }

    var showJyutpingCandidateReadings: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (displayState == CandidateBarDisplayState.CANDIDATES_OR_COMPOSING) {
                displayedCandidates.forEachIndexed { index, candidate ->
                    (row.getChildAt(index) as? TextView)?.let {
                        bindCandidateView(it, candidate, index)
                    }
                }
            }
        }

    private enum class SystemMessageStyle { DEFAULT, SAFE_MODE }

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

    private val textSizeSp = CandidateBarLayoutPolicy.TEXT_SIZE_SP
    private val padH = dp(18)
    private val padV = dp(CandidateBarLayoutPolicy.VERTICAL_PADDING_DP)

    fun showSafeMode(auxiliaryAction: AuxiliaryAction? = null) {
        showSystemMessage(
            text = context.getString(R.string.safe_mode_active),
            style = SystemMessageStyle.SAFE_MODE,
            auxiliaryAction = auxiliaryAction
        )
    }

    // Shown briefly on a cold start while the dictionary is still warming, so the
    // bar isn't blank when a code is typed before candidates are ready. The pending
    // decode replaces it as soon as the corpus is loaded.
    fun showLoading() {
        showSystemMessage(
            text = context.getString(R.string.corpus_loading),
            style = SystemMessageStyle.DEFAULT
        )
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
        systemMessageStyle = null
        setRowWidth(LayoutParams.WRAP_CONTENT)
        updateDisplayState(CandidateBarDisplayState.CANDIDATES_OR_COMPOSING)
        setBackgroundColor(themeColors.candidateBackground)

        val desiredCount = candidates.size + 1
        while (row.childCount < desiredCount) {
            row.addView(TextView(context))
        }
        while (row.childCount > desiredCount) {
            row.removeViewAt(row.childCount - 1)
        }
        candidates.forEachIndexed { index, cand ->
            bindCandidateView(row.getChildAt(index) as TextView, cand, index)
        }
        bindExpandView(row.getChildAt(candidates.size) as TextView)
        scrollTo(0, 0)
        candidateRenderMetricPending = true
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (candidateRenderMetricPending) {
            candidateRenderMetricPending = false
            com.hkmixedkeyboard.performance.LatencyLogger.firstCandidateRender()
        }
    }

    fun clear() {
        if (CandidateBarDisplayStatePolicy.afterClear(displayState) == CandidateBarDisplayState.SYSTEM_MESSAGE) {
            return
        }
        renderSnapshot = null
        displayedCandidates = emptyList()
        systemMessageStyle = null
        row.removeAllViews()
        setRowWidth(LayoutParams.WRAP_CONTENT)
        updateDisplayState(CandidateBarDisplayState.EMPTY)
        setBackgroundColor(themeColors.candidateBackground)
    }

    /** Clears a stale safety/loading/error message when its owner resolves it. */
    fun clearSystemMessage() {
        if (displayState != CandidateBarDisplayState.SYSTEM_MESSAGE) {
            clear()
            return
        }
        renderSnapshot = null
        displayedCandidates = emptyList()
        systemMessageStyle = null
        row.removeAllViews()
        setRowWidth(LayoutParams.WRAP_CONTENT)
        updateDisplayState(CandidateBarDisplayState.EMPTY)
        setBackgroundColor(themeColors.candidateBackground)
    }

    private fun applyTheme() {
        if (displayState == CandidateBarDisplayState.SYSTEM_MESSAGE) {
            bindSystemMessageTheme()
            return
        }
        setBackgroundColor(themeColors.candidateBackground)
        when (displayState) {
            CandidateBarDisplayState.CANDIDATES_OR_COMPOSING -> displayedCandidates.forEachIndexed { index, candidate ->
                (row.getChildAt(index) as? TextView)?.let { bindCandidateView(it, candidate, index) }
            }
            else -> Unit
        }
        if (displayState == CandidateBarDisplayState.CANDIDATES_OR_COMPOSING) {
            (row.getChildAt(displayedCandidates.size) as? TextView)?.let(::bindExpandView)
        }
    }

    private fun bindCandidateView(tv: TextView, cand: DecodeCandidate, index: Int) {
        val visibleLabel = CandidatePresentation.label(
            cand,
            glyphPaint::hasGlyph,
            context.getString(R.string.chinese_to_english),
            showJyutpingCandidateReadings
        )
        bindLabel(tv, visibleLabel)
        tv.contentDescription = buildString {
            append(context.getString(
                R.string.candidate_position,
                index + 1,
                displayedCandidates.size,
                cand.text
            ))
            cand.annotation?.takeIf { it.isNotBlank() }?.let {
                append(context.getString(R.string.candidate_reading, it))
            }
            if (index == 0) append(context.getString(R.string.candidate_first))
        }
        tv.isSelected = index == 0
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
        tv.contentDescription = context.getString(R.string.candidate_expand)
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
        tv.typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            android.graphics.Typeface.NORMAL
        )
        tv.gravity = Gravity.CENTER_VERTICAL
        tv.setSingleLine(true)
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.isFocusable = true
        tv.isClickable = true
        tv.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun candidateLayoutParams(margin: Int) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
            setMargins(margin, 0, margin, 0)
        }

    private fun showSystemMessage(
        text: String,
        style: SystemMessageStyle,
        auxiliaryAction: AuxiliaryAction? = null
    ) {
        renderSnapshot = null
        displayedCandidates = emptyList()
        systemMessageStyle = style
        row.removeAllViews()
        setRowWidth(LayoutParams.MATCH_PARENT)
        val message = makeSystemMessageLabel(text)
        if (auxiliaryAction != null) {
            message.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { gravity = Gravity.CENTER_VERTICAL }
        }
        row.addView(message)
        auxiliaryAction?.let { row.addView(makeAuxiliaryActionLabel(it)) }
        updateDisplayState(CandidateBarDisplayState.SYSTEM_MESSAGE)
        bindSystemMessageTheme()
    }

    private fun makeSystemMessageLabel(text: String) = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            android.graphics.Typeface.NORMAL
        )
        setPadding(padH, padV, padH, padV)
        gravity = Gravity.CENTER_VERTICAL
        maxLines = CandidateBarLayoutPolicy.SYSTEM_MESSAGE_MAX_LINES
        ellipsize = TextUtils.TruncateAt.END
        isFocusable = true
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_VERTICAL }
    }

    private fun makeAuxiliaryActionLabel(action: AuxiliaryAction) = TextView(context).apply {
        bindLabel(this, action.label)
        contentDescription = action.contentDescription
        setPadding(padH, padV, padH, padV)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_VERTICAL }
        setOnClickListener {
            selectionHaptic(this)
            action.onClick()
        }
    }

    private fun bindSystemMessageTheme() {
        val isSafeMode = systemMessageStyle == SystemMessageStyle.SAFE_MODE
        setBackgroundColor(if (isSafeMode) themeColors.safeModeBackground else themeColors.candidateBackground)
        val textColor = if (isSafeMode) themeColors.safeModeText else colorText
        (row.getChildAt(0) as? TextView)?.setTextColor(textColor)
        (row.getChildAt(1) as? TextView)?.setTextColor(textColor)
    }

    private fun setRowWidth(width: Int) {
        row.layoutParams = LayoutParams(width, LayoutParams.MATCH_PARENT)
    }

    private fun updateDisplayState(next: CandidateBarDisplayState) {
        if (displayState == next) return
        displayState = next
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
}
