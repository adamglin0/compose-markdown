package dev.markstream.core.source

import dev.markstream.core.model.LineRange
import dev.markstream.core.model.TextRange

internal class LineIndex {
    private val _newlineOffsets: MutableList<Int> = mutableListOf()

    fun reset() {
        _newlineOffsets.clear()
    }

    fun append(chunk: String, startOffset: Int) {
        chunk.forEachIndexed { index, char ->
            if (char == '\n') {
                _newlineOffsets += startOffset + index
            }
        }
    }

    fun lineCount(sourceLength: Int): Int {
        if (sourceLength == 0) {
            return 0
        }

        return _newlineOffsets.size + 1
    }

    fun lineRangeOf(range: TextRange, sourceLength: Int): LineRange {
        if (sourceLength == 0) {
            return LineRange.Empty
        }

        if (range.isEmpty && range.start == sourceLength) {
            val count = lineCount(sourceLength = sourceLength)
            return LineRange(startLine = count - 1, endLineExclusive = count)
        }

        val startLine = lineOf(offset = range.start)
        val endProbe = when {
            range.isEmpty -> range.start
            range.endExclusive == 0 -> 0
            else -> range.endExclusive - 1
        }
        val endLineExclusive = lineOf(offset = endProbe) + 1
        return LineRange(startLine = startLine, endLineExclusive = endLineExclusive)
    }

    fun lines(
        source: String,
        startOffset: Int = 0,
        endExclusive: Int = source.length,
    ): List<IndexedLine> {
        if (source.isEmpty() || startOffset >= endExclusive) {
            return emptyList()
        }

        val result = mutableListOf<IndexedLine>()
        var lineStart = startOffset
        var lineNumber = lineOf(offset = startOffset)
        var newlineIndex = lowerBound(value = startOffset)
        while (newlineIndex < _newlineOffsets.size) {
            val newlineOffset = _newlineOffsets[newlineIndex]
            if (newlineOffset >= endExclusive) {
                break
            }
            result += IndexedLine(
                number = lineNumber,
                range = TextRange(start = lineStart, endExclusive = newlineOffset + 1),
                content = source.substring(lineStart, newlineOffset),
                hasTrailingNewline = true,
            )
            lineStart = newlineOffset + 1
            lineNumber += 1
            newlineIndex += 1
        }

        if (lineStart < endExclusive || (endExclusive == source.length && source.endsWith('\n') && lineStart == endExclusive)) {
            result += IndexedLine(
                number = lineNumber,
                range = TextRange(start = lineStart, endExclusive = endExclusive),
                content = source.substring(lineStart, endExclusive),
                hasTrailingNewline = false,
            )
        }

        return result
    }

    fun lineOf(offset: Int): Int = lowerBound(value = offset)

    private fun lowerBound(value: Int): Int {
        var low = 0
        var high = _newlineOffsets.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (_newlineOffsets[mid] < value) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low
    }
}

internal data class IndexedLine(
    val number: Int,
    val range: TextRange,
    val content: String,
    val hasTrailingNewline: Boolean,
) {
    val isBlank: Boolean
        get() = content.isBlank()
}
