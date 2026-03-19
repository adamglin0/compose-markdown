package com.adamglin.compose.markdown.core.model

data class LineRange(
    val startLine: Int,
    val endLineExclusive: Int,
) {
    init {
        require(startLine >= 0) { "startLine must be non-negative" }
        require(endLineExclusive >= startLine) { "endLineExclusive must be >= startLine" }
    }

    val lineCount: Int
        get() = endLineExclusive - startLine

    fun contains(line: Int): Boolean = line in startLine until endLineExclusive

    companion object {
        val Empty = LineRange(startLine = 0, endLineExclusive = 0)
    }
}
