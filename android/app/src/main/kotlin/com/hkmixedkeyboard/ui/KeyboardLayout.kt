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
    const val BASE_ROW_HEIGHT_DP = 56f

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
    val rows: List<RowDef> = listOf(
        row(
            labels("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            startUnits = 0f,
            heightWeight = 0.85f
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
        ),
        row(
            // 符 (tap = symbols, long-press = emoji) sits next to a dedicated 😊 key
            // (tap = emoji) so emoji is discoverable without the hidden gesture. Space
            // trimmed 4.0 → 3.0 to make room; row still sums to 10 units.
            listOf(
                KEY_SYMBOL to 1.5f,
                KEY_EMOJI to 1.0f,
                KEY_MODE to 1f,
                KEY_SPACE to 3.0f,
                KEY_PERIOD to 1f,
                KEY_COMMA to 1f,
                KEY_ENTER to 1.5f
            ),
            startUnits = 0f
        )
    )

    val totalHeightWeight: Float =
        rows.sumOf { it.heightWeight.toDouble() }.toFloat()

    fun candidateBarHeightPx(density: Float): Int =
        (BASE_ROW_HEIGHT_DP * density).toInt()

    fun keyboardHeightPx(density: Float): Int =
        (BASE_ROW_HEIGHT_DP * totalHeightWeight * density).toInt()

    fun inputViewMinHeightPx(density: Float): Int =
        candidateBarHeightPx(density) + keyboardHeightPx(density)

    fun buildCells(width: Float, height: Float): List<KeyCell> {
        val unitWidth = width / GRID_WIDTH_UNITS
        val unitHeight = height / totalHeightWeight
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
}
