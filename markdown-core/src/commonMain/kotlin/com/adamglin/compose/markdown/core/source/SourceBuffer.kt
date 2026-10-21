package com.adamglin.compose.markdown.core.source

/** The append-only source text of a streaming session, after line ending normalization. */
internal class SourceBuffer {
    private val builder = StringBuilder()

    /**
     * Live view of the source. Readers copy only the ranges they need (see [LineIndex.lines]); copying the
     * whole text per append would make streaming quadratic.
     */
    val content: CharSequence
        get() = builder

    val length: Int
        get() = builder.length

    fun append(chunk: String) {
        builder.append(chunk)
    }

    fun clear() {
        builder.clear()
    }
}
