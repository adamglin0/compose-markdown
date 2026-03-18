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

    companion object {
        val Empty = TextRange(start = 0, endExclusive = 0)
    }
}
