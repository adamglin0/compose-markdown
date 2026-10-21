package com.adamglin.compose.markdown.core.engine

/**
 * Turns streamed chunks into source text that is independent of where the chunks were split:
 * `\r\n` and `\r` become `\n` (also when `\r` ends one chunk and `\n` starts the next), and a high
 * surrogate that ends a chunk is held back until its low surrogate arrives, so no snapshot ever ends in
 * half a code point.
 */
internal class ChunkNormalizer {
    private var skipLeadingLineFeed = false
    private var pendingHighSurrogate: Char? = null

    fun normalize(chunk: String): String {
        val startIndex = if (skipLeadingLineFeed && chunk.startsWith('\n')) 1 else 0
        if (chunk.isNotEmpty()) {
            skipLeadingLineFeed = chunk.endsWith('\r')
        }
        val text = buildString(chunk.length - startIndex + 1) {
            pendingHighSurrogate?.let(::append)
            var index = startIndex
            while (index < chunk.length) {
                val char = chunk[index]
                if (char == '\r') {
                    append('\n')
                    if (chunk.getOrNull(index + 1) == '\n') {
                        index += 1
                    }
                } else {
                    append(char)
                }
                index += 1
            }
        }
        if (text.isNotEmpty() && text.last().isHighSurrogate()) {
            pendingHighSurrogate = text.last()
            return text.dropLast(1)
        }
        pendingHighSurrogate = null
        return text
    }

    /** Releases a held-back high surrogate at end of input, where it stays unpaired. */
    fun flush(): String {
        val pending = pendingHighSurrogate ?: return ""
        pendingHighSurrogate = null
        return pending.toString()
    }

    fun reset() {
        skipLeadingLineFeed = false
        pendingHighSurrogate = null
    }
}
