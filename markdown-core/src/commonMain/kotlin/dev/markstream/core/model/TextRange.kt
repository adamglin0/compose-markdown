package dev.markstream.core.model

data class TextRange(
    val start: Int,
    val endExclusive: Int,
) {
    init {
        require(start >= 0) { "start must be non-negative" }
        require(endExclusive >= start) { "endExclusive must be >= start" }
    }

    val length: Int
        get() = endExclusive - start

    val isEmpty: Boolean
        get() = start == endExclusive

    fun contains(offset: Int): Boolean = offset in start until endExclusive

    fun intersects(other: TextRange): Boolean = start < other.endExclusive && other.start < endExclusive

    fun union(other: TextRange): TextRange = TextRange(
        start = minOf(start, other.start),
        endExclusive = maxOf(endExclusive, other.endExclusive),
    )

    fun shift(delta: Int): TextRange = TextRange(
        start = start + delta,
        endExclusive = endExclusive + delta,
    )

    companion object {
        val Empty = TextRange(start = 0, endExclusive = 0)
    }
}
