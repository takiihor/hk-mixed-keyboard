package com.hkmixedkeyboard.ui

enum class KeyboardSurface {
    TEXT,
    NUMBER,
    NUMERIC_PASSWORD,
    SIGNED_DECIMAL_NUMBER,
    PHONE,
    EMAIL
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

    private const val PIN_GRID_START_UNITS = 1f
    private const val PIN_KEY_WIDTH_UNITS = 8f / 3f

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

    val rows: List<RowDef> = textRows()

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
        showNextInputMethod: Boolean,
        enterAction: SymbolEnterAction = SymbolEnterAction.RETURN
    ): List<RowDef> = when (surface) {
        KeyboardSurface.TEXT -> textRows()
        KeyboardSurface.EMAIL -> alphabetRows + emailBottomRow()
        KeyboardSurface.NUMBER -> numberRows()
        KeyboardSurface.NUMERIC_PASSWORD -> numericPasswordRows(enterAction)
        KeyboardSurface.SIGNED_DECIMAL_NUMBER -> signedDecimalRows()
        KeyboardSurface.PHONE -> phoneRows()
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

    private fun textRows(): List<RowDef> =
        alphabetRows + actionRow(
            listOf(
                KEY_SYMBOL to 1.2f,
                KEY_EMOJI to 0.8f,
                KEY_MODE to 1.05f,
                KEY_SPACE to 3.50f,
                KEY_PERIOD to 1.05f,
                KEY_COMMA to 1.05f
            )
        )

    private fun emailBottomRow(): RowDef = actionRow(
        listOf(
            "@" to 1f,
            "_" to 1f,
            KEY_MODE to 0.75f,
            KEY_SPACE to 5.1f,
            "." to 0.8f
        )
    )

    private fun numberRows(): List<RowDef> = listOf(
        numericRow("1", "2", "3"),
        numericRow("4", "5", "6"),
        numericRow("7", "8", "9"),
        row(listOf("0" to 5f, KEY_BACKSPACE to 5f)),
        compactActionRow()
    )

    private fun numericPasswordRows(enterAction: SymbolEnterAction): List<RowDef> {
        val bottomRow = if (enterAction == SymbolEnterAction.RETURN) {
            row(
                listOf(
                    "0" to PIN_KEY_WIDTH_UNITS,
                    KEY_BACKSPACE to PIN_KEY_WIDTH_UNITS
                ),
                startUnits = PIN_GRID_START_UNITS + PIN_KEY_WIDTH_UNITS
            )
        } else {
            pinRow(KEY_ENTER, "0", KEY_BACKSPACE)
        }
        return listOf(
            pinRow("1", "2", "3"),
            pinRow("4", "5", "6"),
            pinRow("7", "8", "9"),
            bottomRow
        )
    }

    private fun signedDecimalRows(): List<RowDef> = listOf(
        numericRow("1", "2", "3"),
        numericRow("4", "5", "6"),
        numericRow("7", "8", "9"),
        row(listOf("-" to 2.5f, "0" to 2.5f, "." to 2.5f, KEY_BACKSPACE to 2.5f)),
        compactActionRow()
    )

    private fun phoneRows(): List<RowDef> = listOf(
        numericRow("1", "2", "3"),
        numericRow("4", "5", "6"),
        numericRow("7", "8", "9"),
        row(listOf("*" to 2.5f, "0" to 2.5f, "#" to 2.5f, KEY_BACKSPACE to 2.5f)),
        compactActionRow(leading = listOf("+" to 2f))
    )

    private fun numericRow(first: String, second: String, third: String): RowDef =
        row(listOf(first to 10f / 3f, second to 10f / 3f, third to 10f / 3f))

    private fun pinRow(first: String, second: String, third: String): RowDef =
        row(
            listOf(
                first to PIN_KEY_WIDTH_UNITS,
                second to PIN_KEY_WIDTH_UNITS,
                third to PIN_KEY_WIDTH_UNITS
            ),
            startUnits = PIN_GRID_START_UNITS
        )

    private fun compactActionRow(
        leading: List<Pair<String, Float>> = emptyList()
    ): RowDef = actionRow(leading)

    private fun actionRow(leading: List<Pair<String, Float>>): RowDef {
        val enterWidth = GRID_WIDTH_UNITS - leading.fold(0f) { total, (_, width) -> total + width }
        return row(leading + (KEY_ENTER to enterWidth))
    }
}
