package com.hkmixedkeyboard.ui

data class SymbolRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

data class SymbolKeyGeometry(
    val visualRect: SymbolRect,
    val hitRect: SymbolRect
)

data class SymbolKeyboardGeometry(
    val symbolRows: List<List<SymbolKeyGeometry>>,
    val functionRow: List<SymbolKeyGeometry>,
    val horizontalGapPx: Float,
    val verticalGapPx: Float,
    val symbolTextScale: Float
) {
    companion object {
        private const val SYMBOL_COLUMNS = 10
        private const val TOTAL_ROWS = 5

        fun layout(widthPx: Float, heightPx: Float, density: Float): SymbolKeyboardGeometry {
            val outerPadding = minOf(6f * density, widthPx / 20f, heightPx / 20f)
            val contentWidth = (widthPx - outerPadding * 2f).coerceAtLeast(0f)
            val contentHeight = (heightPx - outerPadding * 2f).coerceAtLeast(0f)
            val symbolSlotWidth = contentWidth / SYMBOL_COLUMNS
            val rowSlotHeight = contentHeight / TOTAL_ROWS
            val horizontalGap = adaptiveGap(symbolSlotWidth, density)
            val verticalGap = adaptiveGap(rowSlotHeight, density)
            val symbolRows = (0 until 4).map { row ->
                (0 until SYMBOL_COLUMNS).map { column ->
                    val hit = SymbolRect(
                        left = outerPadding + column * symbolSlotWidth,
                        top = outerPadding + row * rowSlotHeight,
                        right = outerPadding + (column + 1) * symbolSlotWidth,
                        bottom = outerPadding + (row + 1) * rowSlotHeight
                    )
                    SymbolKeyGeometry(hit.inset(horizontalGap / 2f, verticalGap / 2f), hit)
                }
            }

            val functionTop = outerPadding + 4 * rowSlotHeight
            var left = outerPadding
            val totalWeight = SymbolKeyboardSpec.bottomKeyWeights.sum()
            val functionRow = SymbolKeyboardSpec.bottomKeyWeights.map { weight ->
                val right = left + contentWidth * weight / totalWeight
                val hit = SymbolRect(left, functionTop, right, functionTop + rowSlotHeight)
                left = right
                SymbolKeyGeometry(hit.inset(horizontalGap / 2f, verticalGap / 2f), hit)
            }

            return SymbolKeyboardGeometry(
                symbolRows = symbolRows,
                functionRow = functionRow,
                horizontalGapPx = horizontalGap,
                verticalGapPx = verticalGap,
                symbolTextScale = if (symbolSlotWidth >= 18f * density) 1f else symbolSlotWidth / (18f * density)
            )
        }

        private fun adaptiveGap(slotSize: Float, density: Float): Float {
            val preferred = 4f * density
            val minimum = 1f * density
            return minOf(preferred, slotSize * 0.17f).coerceAtLeast(minimum)
        }

        private fun SymbolRect.inset(horizontal: Float, vertical: Float): SymbolRect = SymbolRect(
            left + horizontal,
            top + vertical,
            right - horizontal,
            bottom - vertical
        )
    }
}
