package com.adamglin.compose.markdown.core.block

/**
 * Lines joined by `\n`, appended in place so that a growing code block costs no rejoining. A [fork] shares
 * the buffer: it appends past this text's length and never writes below it, and this text drops whatever
 * the fork appended before it grows again. A fork must not be used after its origin grows.
 */
internal class JoinedLines private constructor(
    private val buffer: StringBuilder,
    private var length: Int,
    lineCount: Int,
) {
    constructor() : this(StringBuilder(), length = 0, lineCount = 0)

    var lineCount: Int = lineCount
        private set

    fun append(line: String) {
        buffer.setLength(length)
        if (lineCount > 0) {
            buffer.append('\n')
        }
        buffer.append(line)
        length = buffer.length
        lineCount += 1
    }

    fun fork(): JoinedLines = JoinedLines(buffer, length, lineCount)

    /** The text from [startIndex] on. */
    fun substring(startIndex: Int): String = buffer.substring(startIndex, length)

    override fun toString(): String = buffer.substring(0, length)
}
