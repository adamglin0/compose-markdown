package dev.markstream.core.block

import dev.markstream.core.internal.OpenBlockFrame
import dev.markstream.core.model.BlockId
import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.InlineId
import dev.markstream.core.model.InlineNode
import dev.markstream.core.model.LineRange
import dev.markstream.core.model.ListStyle
import dev.markstream.core.model.TextRange
import dev.markstream.core.source.LineIndex
import dev.markstream.core.source.SourceBuffer

internal class BlockParser(
    private val sourceBuffer: SourceBuffer,
    private val lineIndex: LineIndex,
    private val allocateBlockId: (kind: String, start: Int, discriminator: String) -> BlockId,
) {
    fun parse(isFinal: Boolean): BlockParseResult {
        val source = sourceBuffer.snapshot()
        if (source.isEmpty()) {
            return BlockParseResult(blocks = emptyList(), openBlockStack = emptyList())
        }

        val lines = lineIndex.lines(source = source).map { indexedLine ->
            ParserLine(
                number = indexedLine.number,
                range = indexedLine.range,
                lineRange = lineIndex.lineRangeOf(range = indexedLine.range, sourceLength = source.length),
                content = indexedLine.content,
                contentRange = TextRange(
                    start = indexedLine.range.start,
                    endExclusive = indexedLine.range.start + indexedLine.content.length,
                ),
            )
        }
        val session = ParserSession(isFinal = isFinal)
        return BlockParseResult(
            blocks = session.parseBlocks(lines = lines),
            openBlockStack = session.openFrames.toList(),
        )
    }

    private inner class ParserSession(
        private val isFinal: Boolean,
    ) {
        private val frameStack: MutableList<OpenBlockFrame> = mutableListOf()
        val openFrames: MutableList<OpenBlockFrame> = mutableListOf()

        fun parseBlocks(lines: List<ParserLine>): List<BlockNode> {
            val blocks = mutableListOf<BlockNode>()
            var index = 0
            while (index < lines.size) {
                val line = lines[index]
                if (line.isBlank) {
                    index += 1
                    continue
                }

                val fencedCode = parseFencedCodeBlock(lines = lines, startIndex = index)
                if (fencedCode != null) {
                    blocks += fencedCode.block
                    index = fencedCode.nextIndex
                    continue
                }

                val blockQuote = parseBlockQuote(lines = lines, startIndex = index)
                if (blockQuote != null) {
                    blocks += blockQuote.block
                    index = blockQuote.nextIndex
                    continue
                }

                val listBlock = parseListBlock(lines = lines, startIndex = index)
                if (listBlock != null) {
                    blocks += listBlock.block
                    index = listBlock.nextIndex
                    continue
                }

                val trimmed = line.content.trimStart()
                if (isThematicBreak(content = trimmed)) {
                    blocks += BlockNode.ThematicBreak(
                        id = allocateBlockId("thematic-break", line.range.start, trimmed.filterNot(Char::isWhitespace)),
                        range = line.range,
                        lineRange = line.lineRange,
                        marker = trimmed,
                    )
                    index += 1
                    continue
                }

                val heading = matchAtxHeading(content = trimmed)
                if (heading != null) {
                    blocks += BlockNode.Heading(
                        id = allocateBlockId("heading", line.range.start, heading.level.toString()),
                        range = line.range,
                        lineRange = line.lineRange,
                        level = heading.level,
                        children = inlineTextNodes(
                            literal = heading.content,
                            range = heading.contentRange(line = line),
                        ),
                    )
                    index += 1
                    continue
                }

                val paragraphEnd = parseParagraphEnd(lines = lines, startIndex = index)
                val paragraphLines = lines.subList(index, paragraphEnd)
                val paragraphRange = paragraphLines.combinedRange()
                val paragraphLineRange = paragraphLines.combinedLineRange()
                withFrame(marker = "paragraph", startOffset = paragraphRange.start) {
                    if (!isFinal && paragraphEnd == lines.size) {
                        rememberOpenFrames()
                    }
                    blocks += BlockNode.Paragraph(
                        id = allocateBlockId("paragraph", paragraphRange.start, "paragraph"),
                        range = paragraphRange,
                        lineRange = paragraphLineRange,
                        children = inlineTextNodes(
                            literal = paragraphLines.joinToString(separator = "\n") { it.content },
                            range = paragraphLines.combinedContentRange(),
                        ),
                    )
                }
                index = paragraphEnd
            }
            return blocks
        }

        private fun parseFencedCodeBlock(lines: List<ParserLine>, startIndex: Int): ParsedBlock? {
            val opener = matchFence(content = lines[startIndex].content) ?: return null
            var index = startIndex + 1
            var isClosed = false
            val bodyLines = mutableListOf<ParserLine>()

            withFrame(marker = "fenced-code", startOffset = lines[startIndex].range.start) {
                while (index < lines.size) {
                    val line = lines[index]
                    if (isClosingFence(content = line.content, opener = opener)) {
                        index += 1
                        isClosed = true
                        break
                    }
                    bodyLines += line
                    index += 1
                }

                if (!isClosed && !isFinal) {
                    rememberOpenFrames()
                }
            }

            val consumed = lines.subList(startIndex, index)
            return ParsedBlock(
                block = BlockNode.FencedCodeBlock(
                    id = allocateBlockId("fenced-code", consumed.first().range.start, opener.infoString),
                    range = consumed.combinedRange(),
                    lineRange = consumed.combinedLineRange(),
                    infoString = opener.infoString.ifEmpty { null },
                    literal = bodyLines.joinToString(separator = "\n") { it.content },
                    isClosed = isClosed,
                ),
                nextIndex = index,
            )
        }

        private fun parseBlockQuote(lines: List<ParserLine>, startIndex: Int): ParsedBlock? {
            val first = stripBlockQuote(line = lines[startIndex]) ?: return null
            val quoteLines = mutableListOf(first)
            var index = startIndex + 1
            var children = emptyList<BlockNode>()

            withFrame(marker = "blockquote", startOffset = lines[startIndex].range.start) {
                while (index < lines.size) {
                    val current = lines[index]
                    val stripped = stripBlockQuote(line = current)
                    if (stripped != null) {
                        quoteLines += stripped
                        index += 1
                        continue
                    }
                    if (current.isBlank) {
                        quoteLines += current
                        index += 1
                        continue
                    }
                    break
                }

                children = parseBlocks(lines = quoteLines)
                if (!isFinal && index == lines.size) {
                    rememberOpenFrames()
                }
            }

            val consumed = lines.subList(startIndex, index)
            return ParsedBlock(
                block = BlockNode.BlockQuote(
                    id = allocateBlockId("blockquote", consumed.first().range.start, "container"),
                    range = consumed.combinedRange(),
                    lineRange = consumed.combinedLineRange(),
                    children = children,
                ),
                nextIndex = index,
            )
        }

        private fun parseListBlock(lines: List<ParserLine>, startIndex: Int): ParsedBlock? {
            val firstMarker = matchListMarker(content = lines[startIndex].content) ?: return null
            val items = mutableListOf<BlockNode.ListItem>()
            var index = startIndex
            var isLoose = false

            withFrame(marker = "list-${firstMarker.style.name.lowercase()}", startOffset = lines[startIndex].range.start) {
                while (index < lines.size) {
                    val marker = matchListMarker(content = lines[index].content) ?: break
                    if (marker.style != firstMarker.style) {
                        break
                    }

                    val itemStartLine = lines[index]
                    val itemLines = mutableListOf<ParserLine>()
                    itemLines += lines[index].copy(
                        content = marker.content,
                        contentRange = TextRange(
                            start = (lines[index].contentRange.start + marker.contentIndent).coerceAtMost(lines[index].contentRange.endExclusive),
                            endExclusive = lines[index].contentRange.endExclusive,
                        ),
                    )
                    index += 1

                    withFrame(marker = "list-item", startOffset = itemStartLine.range.start) {
                        var children = emptyList<BlockNode>()
                        while (index < lines.size) {
                            val current = lines[index]
                            if (current.isBlank) {
                                itemLines += current.copy(content = "", contentRange = TextRange(start = current.range.start, endExclusive = current.range.start))
                                isLoose = true
                                index += 1
                                continue
                            }

                            val nextMarker = matchListMarker(content = current.content)
                            if (nextMarker != null && nextMarker.style == firstMarker.style) {
                                break
                            }

                            if (current.leadingSpaces >= marker.contentIndent) {
                                itemLines += current.copy(
                                    content = current.content.drop(marker.contentIndent),
                                    contentRange = TextRange(
                                        start = (current.contentRange.start + marker.contentIndent).coerceAtMost(current.contentRange.endExclusive),
                                        endExclusive = current.contentRange.endExclusive,
                                    ),
                                )
                                index += 1
                                continue
                            }

                            break
                        }

                        val meaningfulLines = itemLines.trimTrailingBlankLines()
                        children = parseBlocks(lines = meaningfulLines)
                        if (!isFinal && index == lines.size) {
                            rememberOpenFrames()
                        }
                        items += BlockNode.ListItem(
                            id = allocateBlockId("list-item", itemStartLine.range.start, marker.marker),
                            range = meaningfulLines.combinedRange(),
                            lineRange = meaningfulLines.combinedLineRange(),
                            marker = marker.marker,
                            children = children,
                        )
                    }

                    while (index < lines.size && lines[index].isBlank) {
                        index += 1
                    }
                }

                if (!isFinal && index == lines.size) {
                    rememberOpenFrames()
                }
            }

            if (items.isEmpty()) {
                return null
            }
            val listRange = items.map { it.range }.reduce(TextRange::union)
            val listLineRange = items.map { it.lineRange }.reduce { left, right ->
                LineRange(
                    startLine = minOf(left.startLine, right.startLine),
                    endLineExclusive = maxOf(left.endLineExclusive, right.endLineExclusive),
                )
            }
            return ParsedBlock(
                block = BlockNode.ListBlock(
                    id = allocateBlockId("list-block", items.first().range.start, firstMarker.style.name),
                    range = listRange,
                    lineRange = listLineRange,
                    style = firstMarker.style,
                    items = items,
                    isLoose = isLoose,
                ),
                nextIndex = index,
            )
        }

        private fun parseParagraphEnd(lines: List<ParserLine>, startIndex: Int): Int {
            var index = startIndex
            while (index < lines.size) {
                val current = lines[index]
                if (current.isBlank) {
                    break
                }
                if (index > startIndex && startsBlock(content = current.content)) {
                    break
                }
                index += 1
            }
            return index
        }

        private fun startsBlock(content: String): Boolean {
            val trimmed = content.trimStart()
            return matchFence(content = trimmed) != null ||
                stripBlockQuote(line = ParserLine.empty(content = content)) != null ||
                matchListMarker(content = content) != null ||
                matchAtxHeading(content = trimmed) != null ||
                isThematicBreak(content = trimmed)
        }

        private fun inlineTextNodes(literal: String, range: TextRange): List<InlineNode> {
            if (literal.isEmpty()) {
                return emptyList()
            }
            return listOf(
                InlineNode.Text(
                    id = inlineId(range = range, salt = literal.hashCode().toLong()),
                    range = range,
                    literal = literal,
                ),
            )
        }

        private fun <T> withFrame(marker: String, startOffset: Int, block: () -> T): T {
            frameStack += OpenBlockFrame(marker = marker, startOffset = startOffset)
            try {
                return block()
            } finally {
                frameStack.removeAt(frameStack.lastIndex)
            }
        }

        private fun rememberOpenFrames() {
            if (frameStack.size >= openFrames.size) {
                openFrames.clear()
                openFrames += frameStack
            }
        }
    }

    private fun inlineId(range: TextRange, salt: Long): InlineId {
        val raw = (range.start.toLong() shl 32) xor range.endExclusive.toLong() xor salt
        return InlineId(raw = raw and Long.MAX_VALUE)
    }

    private fun stripBlockQuote(line: ParserLine): ParserLine? {
        val indent = line.content.takeWhile { it == ' ' }.length
        if (indent > 3) {
            return null
        }
        val remainder = line.content.drop(indent)
        if (!remainder.startsWith('>')) {
            return null
        }
        val markerWidth = if (remainder.startsWith("> ")) 2 else 1
        val contentStart = (line.contentRange.start + indent + markerWidth).coerceAtMost(line.contentRange.endExclusive)
        return line.copy(
            content = remainder.drop(markerWidth),
            contentRange = TextRange(start = contentStart, endExclusive = line.contentRange.endExclusive),
        )
    }

    private fun matchFence(content: String): FenceMatch? {
        val trimmed = content.trimStart()
        if (trimmed.length < 3) {
            return null
        }
        val marker = trimmed.first()
        if (marker != '`' && marker != '~') {
            return null
        }
        val fenceLength = trimmed.takeWhile { it == marker }.length
        if (fenceLength < 3) {
            return null
        }
        return FenceMatch(
            fenceChar = marker,
            fenceLength = fenceLength,
            infoString = trimmed.drop(fenceLength).trim(),
        )
    }

    private fun isClosingFence(content: String, opener: FenceMatch): Boolean {
        val trimmed = content.trimStart()
        if (trimmed.isEmpty() || trimmed.first() != opener.fenceChar) {
            return false
        }
        val fenceLength = trimmed.takeWhile { it == opener.fenceChar }.length
        if (fenceLength < opener.fenceLength) {
            return false
        }
        return trimmed.drop(fenceLength).isBlank()
    }

    private fun matchListMarker(content: String): ListMarkerMatch? {
        val indent = content.takeWhile { it == ' ' }.length
        if (indent > 3) {
            return null
        }
        val trimmed = content.drop(indent)
        if (trimmed.length < 2) {
            return null
        }

        if (trimmed[0] in charArrayOf('-', '*', '+') && trimmed.getOrNull(1) == ' ') {
            return ListMarkerMatch(
                style = ListStyle.Unordered,
                marker = trimmed.substring(0, 1),
                content = trimmed.drop(2),
                contentIndent = 2,
            )
        }

        var digitsEnd = 0
        while (digitsEnd < trimmed.length && trimmed[digitsEnd].isDigit()) {
            digitsEnd += 1
        }
        if (digitsEnd == 0 || digitsEnd >= trimmed.length) {
            return null
        }
        val delimiter = trimmed[digitsEnd]
        if (delimiter != '.' && delimiter != ')') {
            return null
        }
        if (trimmed.getOrNull(digitsEnd + 1) != ' ') {
            return null
        }
        return ListMarkerMatch(
            style = ListStyle.Ordered,
            marker = trimmed.substring(0, digitsEnd + 1),
            content = trimmed.drop(digitsEnd + 2),
            contentIndent = digitsEnd + 2,
        )
    }

    private fun matchAtxHeading(content: String): HeadingMatch? {
        val markerLength = content.takeWhile { it == '#' }.length
        if (markerLength !in 1..6 || content.getOrNull(markerLength) != ' ') {
            return null
        }
        return HeadingMatch(
            level = markerLength,
            content = content.drop(markerLength + 1).trimEnd().trimEnd('#').trimEnd(),
        )
    }

    private fun isThematicBreak(content: String): Boolean {
        val compact = content.filterNot(Char::isWhitespace)
        if (compact.length < 3) {
            return false
        }
        return compact.all { it == compact.first() } && compact.first() in charArrayOf('-', '*', '_')
    }
}

internal data class BlockParseResult(
    val blocks: List<BlockNode>,
    val openBlockStack: List<OpenBlockFrame>,
)

internal data class ParserLine(
    val number: Int,
    val range: TextRange,
    val lineRange: LineRange,
    val content: String,
    val contentRange: TextRange,
) {
    val isBlank: Boolean
        get() = content.isBlank()

    val leadingSpaces: Int
        get() = content.takeWhile { it == ' ' }.length

    companion object {
        fun empty(content: String): ParserLine = ParserLine(
            number = 0,
            range = TextRange.Empty,
            lineRange = LineRange.Empty,
            content = content,
            contentRange = TextRange.Empty,
        )
    }
}

private data class ParsedBlock(
    val block: BlockNode,
    val nextIndex: Int,
)

private data class FenceMatch(
    val fenceChar: Char,
    val fenceLength: Int,
    val infoString: String,
)

private data class ListMarkerMatch(
    val style: ListStyle,
    val marker: String,
    val content: String,
    val contentIndent: Int,
)

private data class HeadingMatch(
    val level: Int,
    val content: String,
) {
    fun contentRange(line: ParserLine): TextRange {
        val indent = line.content.length - line.content.trimStart().length
        val contentStart = (line.contentRange.start + indent + level + 1).coerceAtMost(line.contentRange.endExclusive)
        val contentEnd = (contentStart + content.length).coerceAtMost(line.contentRange.endExclusive)
        return TextRange(start = contentStart, endExclusive = contentEnd)
    }
}

private fun List<ParserLine>.trimTrailingBlankLines(): List<ParserLine> {
    var endExclusive = size
    while (endExclusive > 0 && this[endExclusive - 1].isBlank) {
        endExclusive -= 1
    }
    return if (endExclusive == 0) listOf(first()) else subList(0, endExclusive)
}

private fun List<ParserLine>.combinedRange(): TextRange = TextRange(
    start = first().range.start,
    endExclusive = last().range.endExclusive,
)

private fun List<ParserLine>.combinedLineRange(): LineRange = LineRange(
    startLine = first().lineRange.startLine,
    endLineExclusive = last().lineRange.endLineExclusive,
)

private fun List<ParserLine>.combinedContentRange(): TextRange = TextRange(
    start = first().contentRange.start,
    endExclusive = last().contentRange.endExclusive,
)
