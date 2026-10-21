package com.adamglin.compose.markdown.core.block

import com.adamglin.compose.markdown.core.internal.BlockIdentity
import com.adamglin.compose.markdown.core.model.BlockId
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.HeadingStyle
import com.adamglin.compose.markdown.core.model.InlineId
import com.adamglin.compose.markdown.core.model.InlineNode
import com.adamglin.compose.markdown.core.model.LineRange
import com.adamglin.compose.markdown.core.model.ListType
import com.adamglin.compose.markdown.core.model.TableAlignment
import com.adamglin.compose.markdown.core.model.TextRange
import com.adamglin.compose.markdown.core.source.IndexedLine

/**
 * Turns a finished [OpenBlock] tree into immutable [BlockNode]s. Every block spans whole source lines;
 * paragraph, heading and table cell content becomes a single [InlineNode.Text] for the inline parser.
 * Block IDs are allocated parent first, in document order.
 */
internal class BlockTreeBuilder(
    private val lines: List<IndexedLine>,
    private val allocateBlockId: (kind: String, start: Int, discriminator: String) -> BlockId,
) {
    fun build(document: OpenBlock.Document): List<BlockNode> = buildChildren(document)

    private fun buildChildren(block: OpenBlock): List<BlockNode> = block.children.mapNotNull(::build)

    private fun build(block: OpenBlock): BlockNode? = when (block) {
        is OpenBlock.Document -> error("The document is not a child block")
        is OpenBlock.BlockQuote -> BlockNode.BlockQuote(
            id = allocate("blockquote", block.startLine, "container"),
            range = rangeOf(block.startLine, block.endLine),
            lineRange = lineRangeOf(block.startLine, block.endLine),
            children = buildChildren(block),
        )

        is OpenBlock.ListContainer -> {
            val type = block.marker.toListType()
            BlockNode.ListBlock(
                id = allocate("list-block", block.startLine, BlockIdentity.listDiscriminator(type)),
                range = rangeOf(block.startLine, block.endLine),
                lineRange = lineRangeOf(block.startLine, block.endLine),
                type = type,
                items = block.children.map { item -> build(item) as BlockNode.ListItem },
                isLoose = !block.isTight,
            )
        }

        is OpenBlock.Item -> BlockNode.ListItem(
            id = allocate("list-item", block.startLine, BlockIdentity.LIST_ITEM_DISCRIMINATOR),
            range = rangeOf(block.startLine, block.endLine),
            lineRange = lineRangeOf(block.startLine, block.endLine),
            children = buildChildren(block),
            taskState = block.taskState,
        )

        is OpenBlock.Paragraph -> buildParagraph(block)
        is OpenBlock.Heading -> {
            val style = if (block.isSetext) HeadingStyle.Setext else HeadingStyle.Atx
            BlockNode.Heading(
                id = allocate("heading", block.startLine, BlockIdentity.headingDiscriminator(style = style, level = block.level)),
                range = rangeOf(block.startLine, block.endLine),
                lineRange = lineRangeOf(block.startLine, block.endLine),
                level = block.level,
                children = inlineText(block.lines),
                style = style,
            )
        }

        is OpenBlock.ThematicBreak -> BlockNode.ThematicBreak(
            id = allocate("thematic-break", block.startLine, block.marker.filterNot(Char::isWhitespace)),
            range = rangeOf(block.startLine, block.endLine),
            lineRange = lineRangeOf(block.startLine, block.endLine),
            marker = block.marker,
        )

        is OpenBlock.FencedCode -> {
            val infoString = block.info.ifEmpty { null }
            BlockNode.FencedCodeBlock(
                id = allocate("fenced-code", block.startLine, block.info),
                range = rangeOf(block.startLine, block.endLine),
                lineRange = lineRangeOf(block.startLine, block.endLine),
                infoString = infoString,
                languageHint = normalizeFenceLanguageHint(infoString),
                literal = block.lines.joinToString(separator = "\n") { it.text },
                isClosed = block.isClosed,
            )
        }

        is OpenBlock.IndentedCode -> BlockNode.IndentedCodeBlock(
            id = allocate("indented-code", block.startLine, "indented"),
            range = rangeOf(block.startLine, block.endLine),
            lineRange = lineRangeOf(block.startLine, block.endLine),
            literal = block.lines.joinToString(separator = "\n") { it.text },
        )

        is OpenBlock.Math -> BlockNode.MathBlock(
            id = allocate("math-block", block.startLine, block.delimiter.name),
            range = rangeOf(block.startLine, block.endLine),
            lineRange = lineRangeOf(block.startLine, block.endLine),
            latex = block.lines.joinToString(separator = "\n") { it.text }.trim(),
            isClosed = block.isClosed,
            delimiter = block.delimiter,
        )

        is OpenBlock.Table -> buildTable(block)
    }

    private fun buildParagraph(block: OpenBlock.Paragraph): BlockNode.Paragraph? {
        val contentLines = block.lines.drop(block.definitionLineCount)
        if (contentLines.isEmpty()) {
            return null
        }
        val startLine = contentLines.first().lineIndex
        val endLine = contentLines.last().lineIndex
        return BlockNode.Paragraph(
            id = allocate("paragraph", startLine, "paragraph"),
            range = rangeOf(startLine, endLine),
            lineRange = lineRangeOf(startLine, endLine),
            children = inlineText(contentLines),
        )
    }

    private fun buildTable(block: OpenBlock.Table): BlockNode.TableBlock {
        val columnCount = block.alignments.size
        return BlockNode.TableBlock(
            id = allocate("table", block.startLine, BlockIdentity.tableDiscriminator(alignments = block.alignments)),
            range = rangeOf(block.startLine, block.endLine),
            lineRange = lineRangeOf(block.startLine, block.endLine),
            header = buildTableRow(block.header, columnCount, isHeader = true),
            alignments = block.alignments,
            rows = block.rows.map { row -> buildTableRow(row, columnCount, isHeader = false) },
        )
    }

    /** Rows keep exactly [columnCount] cells: extra cells are dropped and missing ones are empty. */
    private fun buildTableRow(row: TableRowDraft, columnCount: Int, isHeader: Boolean): BlockNode.TableRow {
        val line = lines[row.lineIndex]
        val rowStart = line.range.start
        val lineRange = lineRangeOf(row.lineIndex, row.lineIndex)
        val rowId = allocateBlockId("table-row", rowStart, BlockIdentity.tableRowDiscriminator(isHeader = isHeader))
        val contentEnd = line.range.start + line.content.length
        return BlockNode.TableRow(
            id = rowId,
            range = line.range,
            lineRange = lineRange,
            cells = List(columnCount) { index ->
                val cell = row.cells.getOrNull(index)
                val range = if (cell == null) {
                    TextRange(start = contentEnd, endExclusive = contentEnd)
                } else {
                    TextRange(start = row.sourceStart + cell.start, endExclusive = row.sourceStart + cell.endExclusive)
                }
                BlockNode.TableCell(
                    id = allocateBlockId("table-cell", range.start, BlockIdentity.tableCellDiscriminator(rowStart = rowStart, index = index)),
                    range = range,
                    lineRange = lineRange,
                    children = cell?.let { textNode(it.text, range) }.orEmpty(),
                )
            },
            isHeader = isHeader,
        )
    }

    /** Joins content lines and drops trailing spaces and tabs, which never reach the rendered text. */
    private fun inlineText(contentLines: List<ContentLine>): List<InlineNode> {
        if (contentLines.isEmpty()) {
            return emptyList()
        }
        val literal = contentLines.joinToString(separator = "\n") { it.text }.trimEnd(' ', '\t')
        val last = contentLines.last()
        val lastContentEnd = lines[last.lineIndex].let { it.range.start + it.content.length }
        val start = contentLines.first().sourceStart
        return textNode(literal, TextRange(start = start, endExclusive = maxOf(start, lastContentEnd)))
    }

    private fun textNode(literal: String, range: TextRange): List<InlineNode> {
        if (literal.isEmpty()) {
            return emptyList()
        }
        val raw = (range.start.toLong() shl 32) xor range.endExclusive.toLong() xor literal.hashCode().toLong()
        return listOf(InlineNode.Text(id = InlineId(raw = raw and Long.MAX_VALUE), range = range, literal = literal))
    }

    private fun allocate(kind: String, startLine: Int, discriminator: String): BlockId =
        allocateBlockId(kind, lines[startLine].range.start, discriminator)

    private fun rangeOf(startLine: Int, endLine: Int): TextRange =
        TextRange(start = lines[startLine].range.start, endExclusive = lines[maxOf(startLine, endLine)].range.endExclusive)

    private fun lineRangeOf(startLine: Int, endLine: Int): LineRange =
        LineRange(startLine = lines[startLine].number, endLineExclusive = lines[maxOf(startLine, endLine)].number + 1)
}

private fun ListMarker.toListType(): ListType =
    if (bulletChar != null) ListType.Bullet(bulletChar = bulletChar) else ListType.Ordered(startNumber = startNumber, delimiter = delimiter!!)

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
