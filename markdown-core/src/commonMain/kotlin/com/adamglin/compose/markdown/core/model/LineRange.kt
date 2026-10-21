package com.adamglin.compose.markdown.core.model

public data class LineRange(
    val startLine: Int,
    val endLineExclusive: Int,
) {
    init {
        require(startLine >= 0) { "startLine must be non-negative" }
        require(endLineExclusive >= startLine) { "endLineExclusive must be >= startLine" }
    }

    public val lineCount: Int
        get() = endLineExclusive - startLine
}
