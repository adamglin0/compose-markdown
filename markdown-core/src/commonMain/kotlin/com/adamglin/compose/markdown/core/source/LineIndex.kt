package com.adamglin.compose.markdown.core.source

import com.adamglin.compose.markdown.core.model.TextRange

/**
 * Line boundaries of the append-only source. Lines are numbered from 0; a line ends after its `\n`, and
 * the last line ends at the end of the source. A final line ending does not start another line for
 * [lines], but [lineCount] counts the empty line after it.
 */
internal class LineIndex {
    private var newlineOffsets = IntArray(INITIAL_CAPACITY)
    private var newlineCount = 0

    /** Length of the source indexed so far. */
    var length: Int = 0
        private set

    fun reset() {
        newlineOffsets = IntArray(INITIAL_CAPACITY)
        newlineCount = 0
        length = 0
    }

    fun append(chunk: String) {
        chunk.forEachIndexed { index, char ->
            if (char == '\n') {
                if (newlineCount == newlineOffsets.size) {
                    newlineOffsets = newlineOffsets.copyOf(newSize = newlineCount * 2)
                }
                newlineOffsets[newlineCount] = length + index
                newlineCount += 1
            }
        }
        length += chunk.length
    }

    fun lineCount(): Int = if (length == 0) 0 else newlineCount + 1

    /** Offset where the last line starts: just after the last line ending, or 0 without one. */
    fun lastLineStart(): Int = if (newlineCount == 0) 0 else newlineOffsets[newlineCount - 1] + 1

    fun lineStart(number: Int): Int = if (number == 0) 0 else newlineOffsets[number - 1] + 1

    /** End of line [number], after its line ending. */
    fun lineEnd(number: Int): Int = if (number < newlineCount) newlineOffsets[number] + 1 else length

    /** End of the content of line [number], before its line ending. */
    fun contentEnd(number: Int): Int = if (number < newlineCount) newlineOffsets[number] else length

    fun lineOf(offset: Int): Int {
        var low = 0
        var high = newlineCount
        while (low < high) {
            val mid = (low + high) ushr 1
            if (newlineOffsets[mid] < offset) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low
    }

    /** Lines of [source] that start within [startOffset, endExclusive); only these lines are copied. */
    fun lines(source: CharSequence, startOffset: Int, endExclusive: Int): List<IndexedLine> {
        val result = ArrayList<IndexedLine>()
        var number = lineOf(startOffset)
        var start = startOffset
        while (start < endExclusive) {
            val end = minOf(lineEnd(number), endExclusive)
            val contentEnd = minOf(contentEnd(number), end)
            result += IndexedLine(
                number = number,
                range = TextRange(start = start, endExclusive = end),
                content = source.substring(start, contentEnd),
            )
            start = end
            number += 1
        }
        return result
    }

    private companion object {
        const val INITIAL_CAPACITY = 64
    }
}

/** One source line; [content] excludes the line ending. */
internal class IndexedLine(
    val number: Int,
    val range: TextRange,
    val content: String,
)
