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

    fun lines(source: String): List<IndexedLine> {
        if (source.isEmpty()) {
            return emptyList()
        }

        val result = mutableListOf<IndexedLine>()
        var lineStart = 0
        var lineNumber = 0
        for (newlineOffset in _newlineOffsets) {
            result += IndexedLine(
                number = lineNumber,
                range = TextRange(start = lineStart, endExclusive = newlineOffset + 1),
                content = source.substring(lineStart, newlineOffset),
                hasTrailingNewline = true,
            )
            lineStart = newlineOffset + 1
            lineNumber += 1
        }

        if (lineStart < source.length || source.endsWith('\n')) {
            result += IndexedLine(
                number = lineNumber,
                range = TextRange(start = lineStart, endExclusive = source.length),
                content = source.substring(lineStart, source.length),
                hasTrailingNewline = false,
            )
        }

        return result
    }

    private fun lineOf(offset: Int): Int {
        var low = 0
        var high = _newlineOffsets.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (_newlineOffsets[mid] < offset) {
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
