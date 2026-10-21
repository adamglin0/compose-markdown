package com.adamglin.compose.markdown.core.model

public data class TextRange(
    val start: Int,
    val endExclusive: Int,
) {
    init {
        require(start >= 0) { "start must be non-negative" }
        require(endExclusive >= start) { "endExclusive must be >= start" }
    }

    public val length: Int
        get() = endExclusive - start

    public val isEmpty: Boolean
        get() = start == endExclusive

    public fun contains(offset: Int): Boolean = offset in start until endExclusive

    internal companion object {
        val Empty = TextRange(start = 0, endExclusive = 0)
    }
}
