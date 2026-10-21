package com.adamglin.compose.markdown.core.model

@ConsistentCopyVisibility
public data class LineRange internal constructor(
    val startLine: Int,
    val endLineExclusive: Int,
) {
    init {
        require(startLine >= 0) { "startLine must be non-negative" }
        require(endLineExclusive >= startLine) { "endLineExclusive must be >= startLine" }
    }
}
