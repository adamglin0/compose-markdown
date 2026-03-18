package dev.markstream.core.internal

import dev.markstream.core.model.LineRange
import dev.markstream.core.model.TextRange

internal class MutableLineIndex {
    private val newlineOffsets: MutableList<Int> = mutableListOf()

    fun reset() {
        newlineOffsets.clear()
    }

    fun append(chunk: String, startOffset: Int) {
        chunk.forEachIndexed { index, char ->
            if (char == '\n') {
                newlineOffsets += startOffset + index
            }
        }
    }

    fun lineCount(sourceLength: Int): Int {
        if (sourceLength == 0) {
            return 0
        }

        return newlineOffsets.size + 1
    }

    fun lineRangeOf(range: TextRange, sourceLength: Int): LineRange {
        if (sourceLength == 0) {
            return LineRange.Empty
        }

        if (range.isEmpty && range.start == sourceLength) {
            val count = lineCount(sourceLength)
            return LineRange(startLine = count - 1, endLineExclusive = count)
        }

        val startLine = lineOf(range.start)
        val endProbe = when {
            range.isEmpty -> range.start
            range.endExclusive == 0 -> 0
            else -> range.endExclusive - 1
        }
        val endLine = lineOf(endProbe) + 1

        return LineRange(startLine = startLine, endLineExclusive = endLine)
    }

    private fun lineOf(offset: Int): Int {
        var low = 0
        var high = newlineOffsets.size
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
}
