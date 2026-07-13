package com.hkmixedkeyboard.ui

enum class CandidateGridSizeMode {
    MATCH_PARENT,
    WRAP_CONTENT
}

data class CandidateGridLayoutSpec(
    val columnCount: Int,
    val cellBaseWidth: Int,
    val cellColumnWeight: Float,
    val gridWidthMode: CandidateGridSizeMode,
    val gridHeightMode: CandidateGridSizeMode
)

object CandidateGridLayoutPolicy {
    fun layoutSpec() = CandidateGridLayoutSpec(
        columnCount = 4,
        cellBaseWidth = 0,
        cellColumnWeight = 1f,
        gridWidthMode = CandidateGridSizeMode.MATCH_PARENT,
        gridHeightMode = CandidateGridSizeMode.WRAP_CONTENT
    )
}
