package com.hkmixedkeyboard.ui

object KeyboardLayout {
    const val KEY_BACKSPACE = "⌫"
    const val KEY_SHIFT = "⇧"
    const val KEY_ENTER = "↵"
    const val KEY_SPACE = " "
    const val KEY_EMOJI = "😊"
    const val KEY_SYMBOL = "符"
    // Input-scheme switch (速成 ↔ 粵拼). Sentinel label; KeyboardView draws the live
    // scheme indicator (modeLabel) over it instead of this glyph.
    const val KEY_MODE = "⌨"
    const val KEY_COMMA = "，"
    const val KEY_PERIOD = "。"
    // Combined punctuation key: shows ？！, commits ？ on tap (！ lives on the 符 page).
    const val KEY_QUESTION = "？！"
    const val KEY_EXCLAIM = "！"

    const val GRID_WIDTH_UNITS = 10f
    const val BASE_ROW_HEIGHT_DP = 48f

    data class KeyDef(
        val label: String,
        val startUnits: Float,
        val widthUnits: Float = 1f
    )

    data class RowDef(
        val keys: List<KeyDef>,
        val heightWeight: Float = 1f
    )

    data class Bounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val centerX: Float get() = (left + right) / 2f
    }

    data class KeyCell(val key: KeyDef, val bounds: Bounds)

    private fun row(
        labelsAndWidths: List<Pair<String, Float>>,
        startUnits: Float = 0f,
        heightWeight: Float = 1f
    ): RowDef {
        var x = startUnits
        return RowDef(
            keys = labelsAndWidths.map { (label, width) ->
                KeyDef(label, x, width).also { x += width }
            },
            heightWeight = heightWeight
        )
    }

    private fun labels(vararg labels: String): List<Pair<String, Float>> =
        labels.map { it to 1f }

    // The number, QWERTY, ZXCV and bottom rows fill the full width (0 → 10) so the
    // far-left and far-right space is used. The home row is the QWERTY stagger:
    // inset half a key each side (0.5 → 9.5) so A lands centred between Q and W.
    private val numberRow: RowDef = row(
        labels("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        startUnits = 0f
    )

    /**
     * Rows for the current preference.
     *
     * iOS has no number row at all — digits live behind `123` — so a permanently
     * visible one is both the fastest way to type a digit and the first thing that
     * looks wrong to someone arriving from an iPhone. It costs a full 48dp row, so
     * it is a preference rather than a fixed part of the design.
     */
    fun rowsFor(showNumberRow: Boolean): List<RowDef> =
        if (showNumberRow) listOf(numberRow) + letterRows else letterRows

    fun totalHeightWeightFor(showNumberRow: Boolean): Float =
        rowsFor(showNumberRow).sumOf { it.heightWeight.toDouble() }.toFloat()

    private val letterRows: List<RowDef> = listOf(
        row(
            labels("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            startUnits = 0f
        ),
        row(
            labels("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            startUnits = 0.5f
        ),
        row(
            listOf(KEY_SHIFT to 1.5f) +
                labels("Z", "X", "C", "V", "B", "N", "M") +
                listOf(KEY_BACKSPACE to 1.5f),
            startUnits = 0f
        ),
        row(
            // 符 (tap = symbols, long-press = emoji) sits next to a dedicated 😊 key
            // (tap = emoji) so emoji is discoverable without the hidden gesture.
            //
            // The dedicated ，key is gone and its unit went to space, which was
            // 3.0 against roughly 4.75 on iOS — the narrowest element of the design
            // on the key people hit most. ，remains one tap away on the 符 page,
            // which is also where Apple's own Chinese keyboards keep it. The freed
            // unit is what an iOS port needs for its mandatory 🌐 key.
            listOf(
                KEY_SYMBOL to 1.5f,
                KEY_EMOJI to 1.0f,
                KEY_MODE to 1f,
                KEY_SPACE to 4.0f,
                KEY_PERIOD to 1f,
                KEY_ENTER to 1.5f
            ),
            startUnits = 0f
        )
    )

    /** Default layout, still including the number row. */
    val rows: List<RowDef> get() = rowsFor(showNumberRow = true)

    val totalHeightWeight: Float
        get() = totalHeightWeightFor(showNumberRow = true)

    fun candidateBarHeightPx(density: Float, showsReadingHint: Boolean = false): Int =
        CandidateBarLayoutPolicy.heightPx(
            density,
            CandidateBarDisplayState.CANDIDATES_OR_COMPOSING,
            showsReadingHint
        )

    fun keyboardHeightPx(density: Float, showNumberRow: Boolean = true): Int =
        (BASE_ROW_HEIGHT_DP * totalHeightWeightFor(showNumberRow) * density).toInt()

    fun inputViewMinHeightPx(
        density: Float,
        showsReadingHint: Boolean = false,
        showNumberRow: Boolean = true
    ): Int = keyboardHeightPx(density, showNumberRow) +
        candidateBarHeightPx(density, showsReadingHint)

    fun buildCells(
        width: Float,
        height: Float,
        showNumberRow: Boolean = true
    ): List<KeyCell> {
        val unitWidth = width / GRID_WIDTH_UNITS
        val unitHeight = height / totalHeightWeightFor(showNumberRow)
        var y = 0f
        return buildList {
            for (row in rowsFor(showNumberRow)) {
                val rowHeight = unitHeight * row.heightWeight
                for (key in row.keys) {
                    add(
                        KeyCell(
                            key,
                            Bounds(
                                left = key.startUnits * unitWidth,
                                top = y,
                                right = (key.startUnits + key.widthUnits) * unitWidth,
                                bottom = y + rowHeight
                            )
                        )
                    )
                }
                y += rowHeight
            }
        }
    }
}
