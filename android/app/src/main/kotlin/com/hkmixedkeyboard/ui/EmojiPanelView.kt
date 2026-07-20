package com.hkmixedkeyboard.ui

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import com.hkmixedkeyboard.settings.KeyboardTheme

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
    private val categoryTabs = mutableListOf<TextView>()
    private val emojiGrids = mutableListOf<GridLayout>()
    private val emojiCells = mutableListOf<TextView>()
    private val functionButtons = mutableListOf<TextView>()
    private lateinit var topBar: LinearLayout
    private lateinit var scrollView: ScrollView

    private var panelState = EmojiPanelPresentationState()

    var themeColors: KeyboardThemeColors = KeyboardTheme.DARK.toColors()
        set(value) {
            field = value
            panelState = panelState.withTheme(value)
            applyTheme()
        }

    init {
        orientation = VERTICAL
        setBackgroundColor(themeColors.emojiPanelBackground)

        val scrollView = ScrollView(context).apply { isFillViewport = true }
        this.scrollView = scrollView
        val contentCol = LinearLayout(context).apply { orientation = VERTICAL }

        // ── Top bar: ABC (back) | category tabs | ⌫ ──────────────────────────
        val topBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(themeColors.emojiCategoryBarBackground)
        }
        this.topBar = topBar
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
            val tab = TextView(context).apply {
                text = cat.icon
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener {
                    panelState = panelState.copy(selectedCategory = idx)
                    applyTheme()
                    scrollView.smoothScrollTo(0, sectionViews.getOrNull(idx)?.top ?: 0)
                }
            }
            categoryTabs += tab
            tabRow.addView(tab)

            val grid = GridLayout(context).apply {
                columnCount = COLUMNS
                setPadding(dp(2), dp(2), dp(2), dp(2))
                setBackgroundColor(themeColors.emojiPanelBackground)
            }
            emojiGrids += grid
            cat.emojis.forEach { emoji ->
                val emojiCell = TextView(context).apply {
                    text = emoji
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    gravity = Gravity.CENTER
                    setPadding(dp(2), dp(7), dp(2), dp(7))
                    background = emojiCellBackground()
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(dp(1), dp(1), dp(1), dp(1))
                    }
                    setOnClickListener {
                        selectionHaptic(this)
                        onEmojiTap?.invoke(emoji)
                    }
                }
                emojiCells += emojiCell
                grid.addView(emojiCell)
            }
            contentCol.addView(grid)
            sectionViews.add(grid)
        }

        scrollView.addView(contentCol)
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            panelState = panelState.copy(scrollY = scrollY)
        }
        addView(scrollView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        applyTheme()
    }

    private fun navButton(label: String, onTap: () -> Unit) = TextView(context).apply {
        text = label
        setTextColor(themeColors.emojiFunctionIcon)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener {
            selectionHaptic(this)
            onTap()
        }
    }.also { functionButtons += it }

    private fun applyTheme() {
        if (!::topBar.isInitialized) return
        setBackgroundColor(themeColors.emojiPanelBackground)
        topBar.setBackgroundColor(themeColors.emojiCategoryBarBackground)
        functionButtons.forEach { it.setTextColor(themeColors.emojiFunctionIcon) }
        categoryTabs.forEachIndexed { index, tab ->
            tab.background = categoryTabBackground(index == panelState.selectedCategory)
            // Deliberately do not set text colour: these are native colour Emoji glyphs.
        }
        emojiGrids.forEach { it.setBackgroundColor(themeColors.emojiPanelBackground) }
        emojiCells.forEach { it.background = emojiCellBackground() }
    }

    private fun emojiCellBackground(): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed),
            GradientDrawable().apply { setColor(themeColors.emojiGridPressedBackground) })
        addState(intArrayOf(), GradientDrawable().apply { setColor(Color.TRANSPARENT) })
    }

    private fun categoryTabBackground(selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            val color = themeColors.emojiSelectionIndicator
            setColor(if (selected) Color.argb(48, Color.red(color), Color.green(color), Color.blue(color))
            else Color.TRANSPARENT)
            cornerRadius = dp(8).toFloat()
        }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
}
