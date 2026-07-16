package com.hkmixedkeyboard.ui

enum class KeyboardSurface {
    TEXT,
    NUMBER,
    SIGNED_DECIMAL_NUMBER,
    PHONE,
    EMAIL,
    URI
}

object KeyboardLayout {
    const val KEY_BACKSPACE = "⌫"
    const val KEY_SHIFT = "⇧"
    const val KEY_ENTER = "↵"
    const val KEY_SPACE = " "
    const val KEY_EMOJI = "😊"
    const val KEY_SYMBOL = "符"
    // Input-scheme switch (速成 → 粵拼 → 普通話拼音). Sentinel label; KeyboardView draws the live
    // scheme indicator (modeLabel) over it instead of this glyph.
    const val KEY_MODE = "⌨"
    const val KEY_SETTINGS = "⚙"
    const val KEY_NEXT_IME = "⌁"
    const val KEY_COMMA = "，"
    const val KEY_PERIOD = "。"
    // Combined punctuation key: shows ？！, commits ？ on tap (！ lives on the 符 page).
    const val KEY_QUESTION = "？！"
    const val KEY_EXCLAIM = "！"

    const val GRID_WIDTH_UNITS = 10f
    const val BASE_ROW_HEIGHT_DP = 52f

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
    private val alphabetRows: List<RowDef> = listOf(
        row(
            labels("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            startUnits = 0f
        ),
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
        )
    )

    val rows: List<RowDef> = textRows(showNextInputMethod = false)

    val totalHeightWeight: Float =
        rows.sumOf { it.heightWeight.toDouble() }.toFloat()

    fun totalHeightWeight(rows: List<RowDef>): Float =
        rows.sumOf { it.heightWeight.toDouble() }.toFloat()

    fun candidateBarHeightPx(density: Float): Int =
        CandidateBarLayoutPolicy.heightPx(density, CandidateBarDisplayState.CANDIDATES_OR_COMPOSING)

    fun keyboardHeightPx(density: Float): Int =
        (BASE_ROW_HEIGHT_DP * totalHeightWeight * density).toInt()

    fun inputViewMinHeightPx(density: Float): Int =
        keyboardHeightPx(density) + candidateBarHeightPx(density)

    fun rowsFor(
        surface: KeyboardSurface,
        showNextInputMethod: Boolean
    ): List<RowDef> = when (surface) {
        KeyboardSurface.TEXT -> textRows(showNextInputMethod)
        KeyboardSurface.EMAIL -> alphabetRows + emailBottomRow(showNextInputMethod)
        KeyboardSurface.URI -> alphabetRows + uriBottomRow(showNextInputMethod)
        KeyboardSurface.NUMBER -> numberRows(showNextInputMethod)
        KeyboardSurface.SIGNED_DECIMAL_NUMBER -> signedDecimalRows(showNextInputMethod)
        KeyboardSurface.PHONE -> phoneRows(showNextInputMethod)
    }

    fun buildCells(
        width: Float,
        height: Float,
        rows: List<RowDef> = this.rows
    ): List<KeyCell> {
        val unitWidth = width / GRID_WIDTH_UNITS
        val unitHeight = height / totalHeightWeight(rows)
        var y = 0f
        return buildList {
            for (row in rows) {
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

    private fun textRows(showNextInputMethod: Boolean): List<RowDef> =
        alphabetRows + actionRow(
            listOf(
                KEY_SETTINGS to 0.75f,
                KEY_SYMBOL to 1.2f,
                KEY_EMOJI to 0.8f,
                KEY_MODE to 0.8f,
                KEY_SPACE to if (showNextInputMethod) 2.75f else 3.5f,
                KEY_PERIOD to 0.8f,
                KEY_COMMA to 0.8f
            ),
            showNextInputMethod,
            nextWidth = 0.75f
        )

    private fun emailBottomRow(showNextInputMethod: Boolean): RowDef = actionRow(
        listOf(
            KEY_SETTINGS to 0.75f,
            "@" to 1f,
            "_" to 1f,
            KEY_MODE to 0.75f,
            KEY_SPACE to if (showNextInputMethod) 3.6f else 4.35f,
            "." to 0.8f
        ),
        showNextInputMethod,
        nextWidth = 0.75f
    )

    private fun uriBottomRow(showNextInputMethod: Boolean): RowDef = actionRow(
        listOf(
            KEY_SETTINGS to 0.75f,
            "/" to 0.8f,
            ":" to 0.8f,
            "-" to 0.8f,
            KEY_SPACE to if (showNextInputMethod) 3.95f else 4.7f,
            "." to 0.8f
        ),
        showNextInputMethod,
        nextWidth = 0.75f
    )

    private fun numberRows(showNextInputMethod: Boolean): List<RowDef> = listOf(
        numericRow("1", "2", "3"),
        numericRow("4", "5", "6"),
        numericRow("7", "8", "9"),
        row(listOf("0" to 5f, KEY_BACKSPACE to 5f)),
        compactActionRow(showNextInputMethod)
    )

    private fun signedDecimalRows(showNextInputMethod: Boolean): List<RowDef> = listOf(
        numericRow("1", "2", "3"),
        numericRow("4", "5", "6"),
        numericRow("7", "8", "9"),
        row(listOf("-" to 2.5f, "0" to 2.5f, "." to 2.5f, KEY_BACKSPACE to 2.5f)),
        compactActionRow(showNextInputMethod)
    )

    private fun phoneRows(showNextInputMethod: Boolean): List<RowDef> = listOf(
        numericRow("1", "2", "3"),
        numericRow("4", "5", "6"),
        numericRow("7", "8", "9"),
        row(listOf("*" to 2.5f, "0" to 2.5f, "#" to 2.5f, KEY_BACKSPACE to 2.5f)),
        compactActionRow(showNextInputMethod, leading = listOf("+" to 2f))
    )

    private fun numericRow(first: String, second: String, third: String): RowDef =
        row(listOf(first to 10f / 3f, second to 10f / 3f, third to 10f / 3f))

    private fun compactActionRow(
        showNextInputMethod: Boolean,
        leading: List<Pair<String, Float>> = emptyList()
    ): RowDef = actionRow(
        listOf(KEY_SETTINGS to 2f) + leading,
        showNextInputMethod,
        nextWidth = 2f
    )

    private fun actionRow(
        leading: List<Pair<String, Float>>,
        showNextInputMethod: Boolean,
        nextWidth: Float
    ): RowDef {
        val beforeEnter = leading +
            if (showNextInputMethod) listOf(KEY_NEXT_IME to nextWidth) else emptyList()
        val enterWidth = GRID_WIDTH_UNITS - beforeEnter.fold(0f) { total, (_, width) -> total + width }
        return row(beforeEnter + (KEY_ENTER to enterWidth))
    }
}
