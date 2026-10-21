package com.adamglin.compose.markdown.core.model

@ConsistentCopyVisibility
public data class TextRange internal constructor(
    val start: Int,
    val endExclusive: Int,
) {
    init {
        require(start >= 0) { "start must be non-negative" }
        require(endExclusive >= start) { "endExclusive must be >= start" }
    }
}
