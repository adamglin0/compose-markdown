package dev.markstream.core.block

import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.internal.LinkReferenceDefinition
import dev.markstream.core.internal.OpenBlockFrame
import dev.markstream.core.internal.BlockIdentity
import dev.markstream.core.internal.normalizeReferenceLabel
import dev.markstream.core.model.BlockId
import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.HeadingStyle
import dev.markstream.core.model.InlineId
import dev.markstream.core.model.InlineNode
import dev.markstream.core.model.LineRange
import dev.markstream.core.model.ListStyle
import dev.markstream.core.model.TableAlignment
import dev.markstream.core.model.TaskState
import dev.markstream.core.model.TextRange
import dev.markstream.core.source.LineIndex
import dev.markstream.core.source.SourceBuffer

internal class BlockParser(
    private val sourceBuffer: SourceBuffer,
    private val lineIndex: LineIndex,
    private val dialect: MarkdownDialect,
    private val allocateBlockId: (kind: String, start: Int, discriminator: String) -> BlockId,
) {
    fun parse(
        isFinal: Boolean,
        range: TextRange = TextRange(start = 0, endExclusive = sourceBuffer.length),
    ): BlockParseResult {
        val source = sourceBuffer.snapshot()
        if (source.isEmpty() || range.isEmpty) {
            return BlockParseResult(
                blocks = emptyList(),
                openBlockStack = emptyList(),
                processedLineCount = 0,
                parsedDefinitions = emptyMap(),
            )
        }

        val lines = lineIndex.lines(
            source = source,
            startOffset = range.start,
            endExclusive = range.endExclusive,
        ).map { indexedLine ->
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
        val blocks = session.parseBlocks(lines = lines)
        return BlockParseResult(
            blocks = blocks,
            openBlockStack = session.openFrames.toList(),
            processedLineCount = lines.size,
            parsedDefinitions = session.parsedDefinitions,
        )
    }

    private inner class ParserSession(
        private val isFinal: Boolean,
    ) {
        private val frameStack: MutableList<OpenBlockFrame> = mutableListOf()
        val openFrames: MutableList<OpenBlockFrame> = mutableListOf()
        val parsedDefinitions: MutableMap<String, LinkReferenceDefinition> = linkedMapOf()

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
                        id = allocateBlockId(
                            "heading",
                            line.range.start,
                            BlockIdentity.headingDiscriminator(style = HeadingStyle.Atx, level = heading.level),
                        ),
                        range = line.range,
                        lineRange = line.lineRange,
                        level = heading.level,
                        children = inlineTextNodes(
                            literal = heading.content,
                            range = heading.contentRange(line = line),
                        ),
                        style = HeadingStyle.Atx,
                    )
                    index += 1
                    continue
                }

                val definition = parseReferenceDefinition(line)
                if (definition != null) {
                    parsedDefinitions.putIfAbsent(definition.label, definition)
                    index += 1
                    continue
                }

                val tableBlock = parseTable(lines = lines, startIndex = index)
                if (tableBlock != null) {
                    blocks += tableBlock.block
                    index = tableBlock.nextIndex
                    continue
                }

                val setextHeading = parseSetextHeading(lines = lines, startIndex = index)
                if (setextHeading != null) {
                    blocks += setextHeading.block
                    index = setextHeading.nextIndex
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
            if (!dialect.blockFeatures.fencedCodeBlocks) {
                return null
            }
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
            val infoString = opener.infoString.ifEmpty { null }
            val literal = bodyLines.joinToString(separator = "\n") { it.content }
            return ParsedBlock(
                block = BlockNode.FencedCodeBlock(
                    id = allocateBlockId("fenced-code", consumed.first().range.start, opener.infoString),
                    range = consumed.combinedRange(),
                    lineRange = consumed.combinedLineRange(),
                    infoString = infoString,
                    languageHint = normalizeFenceLanguageHint(infoString),
                    literal = literal,
                    isClosed = isClosed,
                ),
                nextIndex = index,
            )
        }

        private fun parseBlockQuote(lines: List<ParserLine>, startIndex: Int): ParsedBlock? {
            if (!dialect.blockFeatures.blockQuotes) {
                return null
            }
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
            if (!dialect.blockFeatures.lists) {
                return null
            }
            val firstMarker = matchListMarker(content = lines[startIndex].content) ?: return null
            val items = mutableListOf<BlockNode.ListItem>()
            var index = startIndex
            var isLoose = false

            withFrame(marker = "list-${firstMarker.style.name.lowercase()}", startOffset = lines[startIndex].range.start) {
                while (index < lines.size) {
                    val marker = matchListMarker(content = lines[index].content) ?: break
                    if (marker.style != firstMarker.style || marker.indent != firstMarker.indent) {
                        break
                    }

                    val itemStartLine = lines[index]
                    val firstContent = lines[index].copy(
                        content = marker.content,
                        contentRange = TextRange(
                            start = (lines[index].contentRange.start + marker.contentIndent).coerceAtMost(lines[index].contentRange.endExclusive),
                            endExclusive = lines[index].contentRange.endExclusive,
                        ),
                    )
                    val taskMatch = matchTaskMarker(firstContent)
                    val normalizedFirstContent = taskMatch?.strippedLine ?: firstContent
                    index += 1

                    val itemLines = mutableListOf<ParserLine>()
                    itemLines += normalizedFirstContent

                    while (index < lines.size) {
                        val current = lines[index]
                        if (current.isBlank) {
                            itemLines += current.copy(content = "", contentRange = TextRange(start = current.range.start, endExclusive = current.range.start))
                            isLoose = true
                            index += 1
                            continue
                        }

                        val siblingMarker = matchListMarker(content = current.content)
                        if (siblingMarker != null && siblingMarker.style == firstMarker.style && siblingMarker.indent == firstMarker.indent) {
                            break
                        }

                        val continuationIndent = firstMarker.contentIndent + firstMarker.indent
                        if (current.leadingSpaces >= continuationIndent) {
                            itemLines += current.copy(
                                content = current.content.drop(continuationIndent),
                                contentRange = TextRange(
                                    start = (current.contentRange.start + continuationIndent).coerceAtMost(current.contentRange.endExclusive),
                                    endExclusive = current.contentRange.endExclusive,
                                ),
                            )
                            index += 1
                            continue
                        }

                        break
                    }

                    val meaningfulLines = itemLines.trimTrailingBlankLines()
                    val children = parseBlocks(lines = meaningfulLines)
                    if (!isFinal && index == lines.size) {
                        rememberOpenFrames()
                    }
                    items += BlockNode.ListItem(
                        id = allocateBlockId("list-item", itemStartLine.range.start, marker.marker),
                        range = meaningfulLines.combinedRange(),
                        lineRange = meaningfulLines.combinedLineRange(),
                        marker = marker.marker,
                        children = children,
                        taskState = taskMatch?.state,
                    )

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

        private fun parseTable(lines: List<ParserLine>, startIndex: Int): ParsedBlock? {
            if (!dialect.blockFeatures.tables || startIndex + 1 >= lines.size) {
                return null
            }
            val headerLine = lines[startIndex]
            val delimiterLine = lines[startIndex + 1]
            if (!looksLikeTableRow(headerLine.content) || !isTableDelimiterRow(delimiterLine.content)) {
                return null
            }

            val headerCells = splitTableCells(headerLine)
            val alignments = parseTableAlignments(delimiterLine.content)
            if (headerCells.isEmpty() || alignments.isEmpty()) {
                return null
            }
            val normalizedColumnCount = maxOf(headerCells.size, alignments.size)
            var index = startIndex + 2
            val rows = mutableListOf<BlockNode.TableRow>()
            while (index < lines.size) {
                val line = lines[index]
                if (line.isBlank || !looksLikeTableRow(line.content) || startsBlock(line.content)) {
                    break
                }
                val cells = splitTableCells(line).padTo(normalizedColumnCount, line)
                rows += createTableRow(line = line, cells = cells, isHeader = false)
                index += 1
            }

            val consumed = lines.subList(startIndex, index)
            return ParsedBlock(
                block = BlockNode.TableBlock(
                    id = allocateBlockId(
                        "table",
                        headerLine.range.start,
                        BlockIdentity.tableDiscriminator(alignments = alignments.padTo(normalizedColumnCount, TableAlignment.Default)),
                    ),
                    range = consumed.combinedRange(),
                    lineRange = consumed.combinedLineRange(),
                    header = createTableRow(
                        line = headerLine,
                        cells = headerCells.padTo(normalizedColumnCount, headerLine),
                        isHeader = true,
                    ),
                    alignments = alignments.padTo(normalizedColumnCount, TableAlignment.Default),
                    rows = rows,
                ),
                nextIndex = index,
            )
        }

        private fun parseSetextHeading(lines: List<ParserLine>, startIndex: Int): ParsedBlock? {
            if (!dialect.blockFeatures.setextHeadings || startIndex + 1 >= lines.size) {
                return null
            }
            var index = startIndex
            while (index + 1 < lines.size) {
                val line = lines[index]
                if (line.isBlank) {
                    return null
                }
                if (index > startIndex && startsBlock(line.content)) {
                    return null
                }
                if (
                    dialect.blockFeatures.tables &&
                    index == startIndex &&
                    looksLikeTableRow(lines[startIndex].content) &&
                    isTableDelimiterRow(lines[index + 1].content)
                ) {
                    return null
                }
                val underline = matchSetextUnderline(lines[index + 1].content)
                if (underline != null) {
                    val contentLines = lines.subList(startIndex, index + 1)
                    if (startsBlock(contentLines.first().content)) {
                        return null
                    }
                    val content = trimLiteralWithRange(
                        literal = contentLines.joinToString(separator = "\n") { it.content },
                        range = contentLines.combinedContentRange(),
                    )
                    val consumed = lines.subList(startIndex, index + 2)
                    return ParsedBlock(
                        block = BlockNode.Heading(
                            id = allocateBlockId(
                                "heading",
                                contentLines.first().range.start,
                                BlockIdentity.headingDiscriminator(style = HeadingStyle.Setext, level = underline.level),
                            ),
                            range = consumed.combinedRange(),
                            lineRange = consumed.combinedLineRange(),
                            level = underline.level,
                            children = inlineTextNodes(content.literal, content.range),
                            style = HeadingStyle.Setext,
                        ),
                        nextIndex = index + 2,
                    )
                }
                index += 1
            }
            return null
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
                if (
                    dialect.blockFeatures.tables &&
                    index == startIndex &&
                    index + 1 < lines.size &&
                    looksLikeTableRow(lines[startIndex].content) &&
                    isTableDelimiterRow(lines[index + 1].content)
                ) {
                    break
                }
                index += 1
            }
            return index
        }

        private fun startsBlock(content: String): Boolean {
            val trimmed = content.trimStart()
            return (dialect.blockFeatures.fencedCodeBlocks && matchFence(content = trimmed) != null) ||
                (dialect.blockFeatures.blockQuotes && stripBlockQuote(line = ParserLine.empty(content = content)) != null) ||
                (dialect.blockFeatures.lists && matchListMarker(content = content) != null) ||
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

        private fun createTableRow(line: ParserLine, cells: List<TableCellDraft>, isHeader: Boolean): BlockNode.TableRow {
            return BlockNode.TableRow(
                id = allocateBlockId(
                    "table-row",
                    line.range.start,
                    BlockIdentity.tableRowDiscriminator(isHeader = isHeader),
                ),
                range = line.range,
                lineRange = line.lineRange,
                cells = cells.mapIndexed { index, cell ->
                    BlockNode.TableCell(
                        id = allocateBlockId(
                            "table-cell",
                            cell.range.start,
                            BlockIdentity.tableCellDiscriminator(rowStart = line.range.start, index = index),
                        ),
                        range = cell.range,
                        lineRange = line.lineRange,
                        children = inlineTextNodes(cell.literal, cell.range),
                    )
                },
                isHeader = isHeader,
            )
        }

        private fun splitTableCells(line: ParserLine): List<TableCellDraft> {
            val content = line.content
            val contentStart = content.indexOfFirst { !it.isWhitespace() }
            if (contentStart == -1) {
                return emptyList()
            }
            val contentEndExclusive = content.indexOfLast { !it.isWhitespace() } + 1
            if (content.indexOf('|', startIndex = contentStart) !in contentStart until contentEndExclusive) {
                return emptyList()
            }
            val rawStart = contentStart + if (content[contentStart] == '|') 1 else 0
            val rawEndExclusive = contentEndExclusive - if (content[contentEndExclusive - 1] == '|') 1 else 0
            if (rawEndExclusive < rawStart) {
                return emptyList()
            }
            val cells = mutableListOf<TableCellDraft>()
            var cellStart = rawStart
            var cursor = rawStart
            while (cursor <= rawEndExclusive) {
                if (cursor == rawEndExclusive || content[cursor] == '|') {
                    var literalStart = cellStart
                    while (literalStart < cursor && content[literalStart].isWhitespace()) {
                        literalStart += 1
                    }
                    var literalEndExclusive = cursor
                    while (literalEndExclusive > literalStart && content[literalEndExclusive - 1].isWhitespace()) {
                        literalEndExclusive -= 1
                    }
                    val start = (line.contentRange.start + literalStart).coerceAtMost(line.contentRange.endExclusive)
                    val end = (line.contentRange.start + literalEndExclusive).coerceIn(start, line.contentRange.endExclusive)
                    cells += TableCellDraft(
                        literal = if (literalStart >= literalEndExclusive) "" else content.substring(literalStart, literalEndExclusive),
                        range = TextRange(start = start, endExclusive = end),
                    )
                    cellStart = cursor + 1
                }
                cursor += 1
            }
            return cells
        }

        private fun looksLikeTableRow(content: String): Boolean = content.contains('|') && !content.trim().startsWith('>')

        private fun isTableDelimiterRow(content: String): Boolean {
            val compact = content.trim().removePrefix("|").removeSuffix("|")
            if (!compact.contains('|') && compact.count { it == '-' || it == ':' || it.isWhitespace() } == compact.length) {
                return compact.trim().length >= 3
            }
            val parts = compact.split('|')
            return parts.isNotEmpty() && parts.all { part ->
                val trimmed = part.trim()
                trimmed.length >= 3 && trimmed.all { it == '-' || it == ':' }
            }
        }

        private fun parseTableAlignments(content: String): List<TableAlignment> {
            val compact = content.trim().removePrefix("|").removeSuffix("|")
            val parts = compact.split('|')
            return parts.mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.length < 3 || trimmed.any { it != '-' && it != ':' }) {
                    null
                } else {
                    when {
                        trimmed.startsWith(':') && trimmed.endsWith(':') -> TableAlignment.Center
                        trimmed.startsWith(':') -> TableAlignment.Left
                        trimmed.endsWith(':') -> TableAlignment.Right
                        else -> TableAlignment.Default
                    }
                }
            }
        }

        private fun parseReferenceDefinition(line: ParserLine): LinkReferenceDefinition? {
            if (!dialect.inlineFeatures.referenceLinks) {
                return null
            }
            val trimmed = line.content.trimStart()
            if (!trimmed.startsWith('[')) {
                return null
            }
            val labelEnd = trimmed.indexOf(']')
            if (labelEnd <= 1 || trimmed.getOrNull(labelEnd + 1) != ':' ) {
                return null
            }
            val label = normalizeReferenceLabel(trimmed.substring(1, labelEnd)) ?: return null
            val remainder = trimmed.substring(labelEnd + 2).trim()
            if (remainder.isEmpty()) {
                return null
            }
            val destination = remainder.substringBefore(' ').trim().trim('<', '>')
            if (destination.isEmpty()) {
                return null
            }
            val title = remainder.removePrefix(destination).trim().trim().takeIf { it.isNotEmpty() }?.trim('"', '\'')
            return LinkReferenceDefinition(
                label = label,
                destination = destination,
                title = title,
                range = line.range,
            )
        }

        private fun matchSetextUnderline(content: String): SetextUnderlineMatch? {
            val trimmed = content.trim()
            if (trimmed.length < 3) {
                return null
            }
            return when {
                trimmed.all { it == '=' } -> SetextUnderlineMatch(level = 1)
                trimmed.all { it == '-' } -> SetextUnderlineMatch(level = 2)
                else -> null
            }
        }

        private fun matchTaskMarker(line: ParserLine): TaskMarkerMatch? {
            if (!dialect.blockFeatures.taskListItems) {
                return null
            }
            val content = line.content
            if (content.length < 4 || content[0] != '[' || content[2] != ']' || content[3] != ' ') {
                return null
            }
            val state = when (content[1]) {
                ' ', '\t' -> TaskState.Unchecked
                'x', 'X' -> TaskState.Checked
                else -> return null
            }
            val contentStart = (line.contentRange.start + 4).coerceAtMost(line.contentRange.endExclusive)
            return TaskMarkerMatch(
                state = state,
                strippedLine = line.copy(
                    content = content.drop(4),
                    contentRange = TextRange(start = contentStart, endExclusive = line.contentRange.endExclusive),
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
                indent = indent,
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
            indent = indent,
        )
    }

    private fun matchAtxHeading(content: String): HeadingMatch? {
        if (!dialect.blockFeatures.atxHeadings) {
            return null
        }
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
    val processedLineCount: Int,
    val parsedDefinitions: Map<String, LinkReferenceDefinition>,
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
    val indent: Int,
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

private data class SetextUnderlineMatch(
    val level: Int,
)

private data class TaskMarkerMatch(
    val state: TaskState,
    val strippedLine: ParserLine,
)

private data class TableCellDraft(
    val literal: String,
    val range: TextRange,
)

private data class LiteralWithRange(
    val literal: String,
    val range: TextRange,
)

private fun normalizeFenceLanguageHint(infoString: String?): String? {
    val raw = infoString
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    val separatorIndex = raw.indexOfFirst { it.isWhitespace() || it == '{' }
    val firstToken = raw
        .let { if (separatorIndex == -1) it else it.substring(0, separatorIndex) }
        .removePrefix("language-")
        .removePrefix("lang-")
        .trim()
        .lowercase()
    if (firstToken.isEmpty()) {
        return null
    }
    return when (firstToken) {
        "kt", "kts" -> "kotlin"
        "js", "mjs", "cjs" -> "javascript"
        "ts" -> "typescript"
        "py" -> "python"
        "rb" -> "ruby"
        "sh", "bash", "zsh" -> "shell"
        "c++", "cc", "cxx" -> "cpp"
        "cs", "c#" -> "csharp"
        "rs" -> "rust"
        "golang" -> "go"
        "coffee" -> "coffeescript"
        else -> firstToken
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

private fun trimLiteralWithRange(literal: String, range: TextRange): LiteralWithRange {
    if (literal.isEmpty()) {
        return LiteralWithRange(literal = literal, range = range)
    }
    val firstContentIndex = literal.indexOfFirst { !it.isWhitespace() }
    if (firstContentIndex == -1) {
        return LiteralWithRange(
            literal = "",
            range = TextRange(start = range.endExclusive, endExclusive = range.endExclusive),
        )
    }
    val lastContentIndex = literal.indexOfLast { !it.isWhitespace() }
    return LiteralWithRange(
        literal = literal.substring(firstContentIndex, lastContentIndex + 1),
        range = TextRange(
            start = range.start + firstContentIndex,
            endExclusive = range.start + lastContentIndex + 1,
        ),
    )
}

private fun List<TableCellDraft>.padTo(size: Int, line: ParserLine): List<TableCellDraft> {
    if (this.size >= size) {
        return this
    }
    return this + List(size - this.size) {
        TableCellDraft(
            literal = "",
            range = TextRange(start = line.contentRange.endExclusive, endExclusive = line.contentRange.endExclusive),
        )
    }
}

private fun <T> List<T>.padTo(size: Int, element: T): List<T> {
    if (this.size >= size) {
        return this
    }
    return this + List(size - this.size) { element }
}
