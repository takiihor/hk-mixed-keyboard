package com.hkmixedkeyboard.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Telegram-style emoji panel: a compact category tab strip on top, then one
 * continuous, densely packed grid with no section-header lines. Stays open so the
 * user can tap several emoji in a row. ABC returns to the keyboard; ⌫ deletes.
 */
class EmojiPanelView(context: Context) : LinearLayout(context) {

    var onEmojiTap: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
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

    private val COLUMNS = 9

    private val CATEGORIES = listOf(
        Category("🕒", listOf("😊", "😂", "🙏", "❤️", "👍", "😭", "😍", "🎉", "🙈", "💪")),
        Category("😀", listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
            "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
            "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥳",
            "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "😣", "😖", "😫",
            "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "😳", "🥵",
            "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🤭", "😶", "😬",
            "🙄", "😯", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮"
        )),
        Category("👋", listOf(
            "👋", "🤚", "✋", "🖐", "👌", "🤌", "🤏", "✌️", "🤞", "🤟",
            "🤙", "👈", "👉", "👆", "👇", "👍", "👎", "✊", "👊", "🤛",
            "🤜", "👏", "🙌", "👐", "🤲", "🙏", "💅", "💪", "🦾", "✍️"
        )),
        Category("🐱", listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
            "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🐤", "🦄",
            "🐝", "🦋", "🐌", "🐞", "🐢", "🐙", "🐠", "🐬", "🐳", "🦖"
        )),
        Category("🍜", listOf(
            "🍎", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍑", "🥭", "🍍",
            "🍜", "🍣", "🍱", "🍛", "🍚", "🥟", "🍤", "🍙", "🍰", "🎂",
            "🍦", "🍩", "🍪", "🍫", "🍬", "☕", "🍵", "🧋", "🍺", "🍷"
        )),
        Category("🚇", listOf(
            "🚇", "🚌", "🚕", "🚗", "🚙", "🚒", "🚑", "🚓", "✈️", "🚢",
            "🚲", "🛵", "🏍️", "🚁", "🚀", "⛵", "🚤", "🛳️", "🚂", "🚆"
        )),
        Category("⚽", listOf(
            "⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏉", "🎱", "🏓", "🏸",
            "🥅", "🏒", "🏑", "🥍", "🏏", "⛳", "🎯", "🎮", "🎲", "🎸"
        )),
        Category("❤️", listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "❣️",
            "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💯", "🔥", "⭐",
            "🌟", "✨", "⚡", "💥", "💫", "🎉", "🎊", "🔔", "🎵", "✅"
        ))
    )

    data class Category(val icon: String, val emojis: List<String>)

    private val sectionViews = mutableListOf<View>()

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF202124.toInt())

        val scrollView = ScrollView(context).apply { isFillViewport = true }
        val contentCol = LinearLayout(context).apply { orientation = VERTICAL }

        // ── Top bar: ABC (back) | category tabs | ⌫ ──────────────────────────
        val topBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF26282A.toInt())
        }
        topBar.addView(navButton("ABC") { onClose?.invoke() })

        val tabScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        val tabRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        tabScroll.addView(tabRow)
        topBar.addView(tabScroll, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        topBar.addView(navButton("⌫") { onBackspace?.invoke() })
        addView(topBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // ── Tabs + dense grids (no section headers) ──────────────────────────
        CATEGORIES.forEachIndexed { idx, cat ->
            tabRow.addView(TextView(context).apply {
                text = cat.icon
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener {
                    scrollView.smoothScrollTo(0, sectionViews.getOrNull(idx)?.top ?: 0)
                }
            })

            val grid = GridLayout(context).apply {
                columnCount = COLUMNS
                setPadding(dp(2), dp(2), dp(2), dp(2))
            }
            cat.emojis.forEach { emoji ->
                grid.addView(TextView(context).apply {
                    text = emoji
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    gravity = Gravity.CENTER
                    setPadding(dp(2), dp(7), dp(2), dp(7))
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(dp(1), dp(1), dp(1), dp(1))
                    }
                    setOnClickListener {
                        selectionHaptic(this)
                        onEmojiTap?.invoke(emoji)
                    }
                })
            }
            contentCol.addView(grid)
            sectionViews.add(grid)
        }

        scrollView.addView(contentCol)
        addView(scrollView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun navButton(label: String, onTap: () -> Unit) = TextView(context).apply {
        text = label
        setTextColor(0xFFE8EAED.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener {
            selectionHaptic(this)
            onTap()
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
}
