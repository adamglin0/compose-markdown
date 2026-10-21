package com.adamglin.compose.markdown.core.block

import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.inline.unescapeString
import com.adamglin.compose.markdown.core.internal.LinkReferenceDefinition
import com.adamglin.compose.markdown.core.internal.OpenBlockFrame
import com.adamglin.compose.markdown.core.model.BlockId
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.MathBlockDelimiter
import com.adamglin.compose.markdown.core.model.TableAlignment
import com.adamglin.compose.markdown.core.model.TaskState
import com.adamglin.compose.markdown.core.model.TextRange
import com.adamglin.compose.markdown.core.source.IndexedLine
import com.adamglin.compose.markdown.core.source.LineIndex
import com.adamglin.compose.markdown.core.source.SourceBuffer

/**
 * Block structure parser following CommonMark 0.31.2, appendix "A parsing strategy" (the commonmark.js
 * reference implementation): each line first walks the stack of open blocks to match their continuation
 * markers, then looks for new block starts, and finally adds the remaining text to the innermost block,
 * possibly as a lazy paragraph continuation. Tabs advance to the next multiple of four columns.
 *
 * GFM tables follow cmark-gfm's table extension; display math (`$$` / `\[`) is a fence-like leaf block.
 * The result is turned into immutable [BlockNode]s by
 * [BlockTreeBuilder]; inline content is left as a single text node for the inline parser.
 */
internal class BlockParser(
    private val sourceBuffer: SourceBuffer,
    private val lineIndex: LineIndex,
    private val dialect: MarkdownDialect,
    private val allocateBlockId: (kind: String, start: Int, discriminator: String) -> BlockId,
) {
    /**
     * Parses the lines of [range], which must start at a line boundary outside any container block.
     * Unless [isFinal], the result reports the blocks that later input can still change as
     * [BlockParseResult.openBlockStack], outermost first.
     */
    fun parse(
        isFinal: Boolean,
        range: TextRange = TextRange(start = 0, endExclusive = sourceBuffer.length),
    ): BlockParseResult {
        val source = sourceBuffer.snapshot()
        val lines = if (source.isEmpty() || range.isEmpty) {
            emptyList()
        } else {
            lineIndex.lines(source = source, startOffset = range.start, endExclusive = range.endExclusive)
        }
        val session = BlockParserSession(lines = lines, dialect = dialect)
        // An unterminated last line may still grow into a different block start, so blocks it closed stay open.
        val pendingLine = lines.lastOrNull()?.takeIf { !isFinal && !it.hasTrailingNewline }
        val completeLineCount = if (pendingLine != null) lines.size - 1 else lines.size
        for (index in 0 until completeLineCount) {
            session.incorporateLine(index)
        }
        val openBlockStack = when {
            isFinal -> emptyList()
            pendingLine == null -> session.openBlockFrames()
            else -> {
                val beforePendingLine = session.openBlockFrames()
                session.incorporateLine(lines.lastIndex)
                val afterPendingLine = session.openBlockFrames()
                val before = beforePendingLine.firstOrNull()?.startOffset
                val after = afterPendingLine.firstOrNull()?.startOffset
                if (before != null && (after == null || before < after)) beforePendingLine else afterPendingLine
            }
        }
        val document = session.finish()
        return BlockParseResult(
            blocks = BlockTreeBuilder(lines = lines, allocateBlockId = allocateBlockId).build(document),
            openBlockStack = openBlockStack,
            processedLineCount = lines.size,
            parsedDefinitions = session.definitions,
        )
    }
}

internal data class BlockParseResult(
    val blocks: List<BlockNode>,
    val openBlockStack: List<OpenBlockFrame>,
    val processedLineCount: Int,
    val parsedDefinitions: Map<String, LinkReferenceDefinition>,
)

/** One line of leaf content: [text] from the line's current offset, starting at source offset [sourceStart]. */
internal class ContentLine(
    val lineIndex: Int,
    val text: String,
    val sourceStart: Int,
)

/** Mutable block tree built while lines are incorporated; see [BlockTreeBuilder] for the public model. */
internal sealed class OpenBlock(val startLine: Int) {
    var parent: OpenBlock? = null
    val children: MutableList<OpenBlock> = mutableListOf()
    var isOpen: Boolean = true
    var endLine: Int = startLine

    /** Number of ancestors; the document is at depth 0. */
    var depth: Int = 0

    class Document : OpenBlock(startLine = 0)

    class BlockQuote(startLine: Int) : OpenBlock(startLine)

    class ListContainer(startLine: Int, val marker: ListMarker) : OpenBlock(startLine) {
        var isTight: Boolean = true
    }

    class Item(startLine: Int, val markerOffset: Int, val padding: Int) : OpenBlock(startLine) {
        var taskState: TaskState? = null
    }

    class Paragraph(startLine: Int) : OpenBlock(startLine) {
        val lines: MutableList<ContentLine> = mutableListOf()

        /** Leading lines that held link reference definitions; they are not paragraph content. */
        var definitionLineCount: Int = 0

        /** cmark-gfm only tries to turn a paragraph into a table header once. */
        var triedTableHeader: Boolean = false
    }

    class Heading(startLine: Int, val level: Int, val isSetext: Boolean, val lines: List<ContentLine>) : OpenBlock(startLine)

    class ThematicBreak(startLine: Int, val marker: String) : OpenBlock(startLine)

    class FencedCode(startLine: Int, val fence: FenceOpener, val fenceOffset: Int, val info: String) : OpenBlock(startLine) {
        val lines: MutableList<ContentLine> = mutableListOf()
        var isClosed: Boolean = false
    }

    class IndentedCode(startLine: Int) : OpenBlock(startLine) {
        val lines: MutableList<ContentLine> = mutableListOf()
    }

    /** Display math; a single-line block (`$$x$$`) is complete as soon as it starts. */
    class Math(startLine: Int, val delimiter: MathBlockDelimiter, val fenceOffset: Int, val isSingleLine: Boolean) : OpenBlock(startLine) {
        val lines: MutableList<ContentLine> = mutableListOf()
        var isClosed: Boolean = isSingleLine
    }

    /**
     * A table whose header was the last line of the paragraph starting at [paragraphStartLine]; reparsing
     * must restart there, because the header line alone may parse differently (as a paragraph's first line).
     */
    class Table(
        startLine: Int,
        val paragraphStartLine: Int,
        val alignments: List<TableAlignment>,
        val header: TableRowDraft,
    ) : OpenBlock(startLine) {
        val rows: MutableList<TableRowDraft> = mutableListOf()
    }

    fun canContain(child: OpenBlock): Boolean = when (this) {
        is Document, is BlockQuote, is Item -> child !is Item
        is ListContainer -> child is Item
        else -> false
    }

    /** Whether the final step of a line may add text (or a table row) to this block. */
    val acceptsLines: Boolean
        get() = this is Paragraph || this is FencedCode || this is IndentedCode || (this is Math && !isSingleLine) || this is Table

    /** Whether a later line can still change this block while it is open. */
    val canGrow: Boolean
        get() = this !is Heading && this !is ThematicBreak && !(this is Math && isSingleLine)
}

internal class TableRowDraft(
    val lineIndex: Int,
    val sourceStart: Int,
    val cells: List<TableCellSpan>,
)

private enum class Continuation {
    Matched,
    Failed,

    /** The line was fully handled (a closing fence); go to the next line. */
    LineConsumed,
}

private enum class BlockStart {
    None,

    /** The line is paragraph text; no other block start applies. */
    Text,

    /** A container started; keep looking for block starts inside it. */
    Container,

    /** A leaf that takes the rest of the line as content started. */
    Leaf,

    /** A leaf started and consumed the whole line. */
    LeafConsumedLine,
}

internal class BlockParserSession(
    private val lines: List<IndexedLine>,
    private val dialect: MarkdownDialect,
) {
    private val document = OpenBlock.Document()
    private var tip: OpenBlock = document
    private var oldTip: OpenBlock = document
    private var lastMatchedContainer: OpenBlock = document
    private var allClosed = true

    val definitions: MutableMap<String, LinkReferenceDefinition> = linkedMapOf()

    // State of the line being incorporated, as in commonmark.js.
    private var lineNumber = 0
    private var line = ""
    private var lineStart = 0
    private var offset = 0
    private var column = 0
    private var nextNonspace = 0
    private var nextNonspaceColumn = 0
    private var indent = 0
    private var indented = false
    private var blank = false
    private var partiallyConsumedTab = false

    fun incorporateLine(index: Int) {
        lineNumber = index
        line = lines[index].content.replace('\u0000', '\uFFFD')
        lineStart = lines[index].range.start
        offset = 0
        column = 0
        blank = false
        partiallyConsumedTab = false
        oldTip = tip

        // Match the continuation markers of the open blocks; `container` ends at the last matched one.
        var container: OpenBlock = document
        matching@ while (true) {
            val lastChild = container.children.lastOrNull()?.takeIf { it.isOpen } ?: break
            findNextNonspace()
            when (continueBlock(lastChild)) {
                Continuation.Matched -> container = lastChild
                Continuation.Failed -> break@matching
                Continuation.LineConsumed -> return
            }
        }

        allClosed = container === oldTip
        lastMatchedContainer = container

        // Unless a leaf that takes raw lines matched, look for new block starts inside the container.
        var matchedLeaf = container !is OpenBlock.Paragraph && container.acceptsLines && container !is OpenBlock.Table
        while (!matchedLeaf) {
            findNextNonspace()
            if (container is OpenBlock.Item && dialect.blockFeatures.taskListItems && startsTaskMarker(container)) {
                advanceNextNonspace()
                break
            }
            when (startBlock(container)) {
                BlockStart.None, BlockStart.Text -> {
                    advanceNextNonspace()
                    break
                }

                BlockStart.Container -> container = tip
                BlockStart.Leaf -> {
                    container = tip
                    matchedLeaf = true
                }

                BlockStart.LeafConsumedLine -> return
            }
        }

        // The rest of the line is text: a lazy paragraph continuation, content of the container, or a new paragraph.
        if (!allClosed && !blank && tip is OpenBlock.Paragraph) {
            addLine()
            return
        }
        closeUnmatchedBlocks()
        when {
            container is OpenBlock.Table -> addTableRow(container)
            container.acceptsLines -> addLine()
            offset < line.length && !blank -> {
                addChild(OpenBlock.Paragraph(startLine = lineNumber))
                advanceNextNonspace()
                addLine()
            }
        }
    }

    /** Frames of the open blocks that later lines can still change, outermost first. */
    fun openBlockFrames(): List<OpenBlockFrame> = buildList {
        var block = document.children.lastOrNull()
        while (block != null && block.isOpen && block.canGrow) {
            val startLine = if (block is OpenBlock.Table) block.paragraphStartLine else block.startLine
            add(OpenBlockFrame(marker = block.frameMarker(), startOffset = lines[startLine].range.start))
            block = block.children.lastOrNull()
        }
    }

    /** Closes every open block and returns the finished tree. */
    fun finish(): OpenBlock.Document {
        while (tip !== document) {
            finalize(tip, lines.lastIndex)
        }
        document.isOpen = false
        return document
    }

    // region Continuation

    private fun continueBlock(block: OpenBlock): Continuation = when (block) {
        is OpenBlock.Document, is OpenBlock.ListContainer -> Continuation.Matched
        is OpenBlock.BlockQuote -> if (!indented && charAt(nextNonspace) == '>') {
            advanceNextNonspace()
            advanceOffset(1, columns = false)
            if (charAt(offset)?.isSpaceOrTab() == true) {
                advanceOffset(1, columns = true)
            }
            Continuation.Matched
        } else {
            Continuation.Failed
        }

        is OpenBlock.Item -> when {
            // A blank line continues an item, except right after an empty first line.
            blank -> if (block.children.isNotEmpty()) {
                advanceNextNonspace()
                Continuation.Matched
            } else {
                Continuation.Failed
            }

            indent >= block.markerOffset + block.padding -> {
                advanceOffset(block.markerOffset + block.padding, columns = true)
                Continuation.Matched
            }

            else -> Continuation.Failed
        }

        is OpenBlock.Heading, is OpenBlock.ThematicBreak -> Continuation.Failed
        is OpenBlock.Paragraph -> if (blank) Continuation.Failed else Continuation.Matched
        // A line without cells (such as a lone `|`) ends the table, as in cmark-gfm.
        is OpenBlock.Table -> if (blank || scanTableRow(line, nextNonspace) == null) Continuation.Failed else Continuation.Matched
        is OpenBlock.IndentedCode -> when {
            indent >= CODE_INDENT -> {
                advanceOffset(CODE_INDENT, columns = true)
                Continuation.Matched
            }

            blank -> {
                advanceNextNonspace()
                Continuation.Matched
            }

            else -> Continuation.Failed
        }

        is OpenBlock.FencedCode -> if (
            indent <= 3 &&
            charAt(nextNonspace) == block.fence.fenceChar &&
            isClosingFence(line, nextNonspace, block.fence.fenceChar, block.fence.fenceLength)
        ) {
            block.isClosed = true
            finalize(block, lineNumber)
            Continuation.LineConsumed
        } else {
            skipFenceOffset(block.fenceOffset)
            Continuation.Matched
        }

        is OpenBlock.Math -> continueMath(block)
    }

    private fun continueMath(block: OpenBlock.Math): Continuation {
        if (block.isSingleLine) {
            return Continuation.Failed
        }
        val closer = block.delimiter.closer
        val contentEnd = trimmedEnd(line, nextNonspace) - closer.length
        if (contentEnd >= nextNonspace && line.startsWith(closer, contentEnd)) {
            if (!isBlankFrom(line.substring(0, contentEnd), nextNonspace)) {
                block.lines += ContentLine(
                    lineIndex = lineNumber,
                    text = line.substring(nextNonspace, contentEnd),
                    sourceStart = lineStart + nextNonspace,
                )
            }
            block.isClosed = true
            finalize(block, lineNumber)
            return Continuation.LineConsumed
        }
        skipFenceOffset(block.fenceOffset)
        return Continuation.Matched
    }

    private fun skipFenceOffset(fenceOffset: Int) {
        var remaining = fenceOffset
        while (remaining > 0 && charAt(offset)?.isSpaceOrTab() == true) {
            advanceOffset(1, columns = true)
            remaining -= 1
        }
    }

    // endregion

    // region Block starts

    private fun startBlock(container: OpenBlock): BlockStart {
        if (indented) {
            return startIndentedCode()
        }
        return startBlockQuote(container)
            .orElse { startAtxHeading() }
            .orElse { startFencedCode() }
            .orElse { startMath() }
            .orElse { startSetextHeading(container) }
            .orElse { startThematicBreak() }
            .orElse { startListItem(container) }
            .orElse { startTable(container) }
    }

    private inline fun BlockStart.orElse(next: () -> BlockStart): BlockStart = if (this == BlockStart.None) next() else this

    private fun startBlockQuote(container: OpenBlock): BlockStart {
        if (charAt(nextNonspace) != '>' || container.depth >= MAX_CONTAINER_DEPTH) {
            return BlockStart.None
        }
        advanceNextNonspace()
        advanceOffset(1, columns = false)
        if (charAt(offset)?.isSpaceOrTab() == true) {
            advanceOffset(1, columns = true)
        }
        closeUnmatchedBlocks()
        addChild(OpenBlock.BlockQuote(startLine = lineNumber))
        return BlockStart.Container
    }

    private fun startAtxHeading(): BlockStart {
        val level = atxHeadingMarkerLength(line, nextNonspace) ?: return BlockStart.None
        val content = atxHeadingContent(line, nextNonspace + level)
        closeUnmatchedBlocks()
        addChild(
            OpenBlock.Heading(
                startLine = lineNumber,
                level = level,
                isSetext = false,
                lines = listOf(
                    ContentLine(
                        lineIndex = lineNumber,
                        text = line.substring(content.first, content.last + 1),
                        sourceStart = lineStart + content.first,
                    ),
                ),
            ),
        )
        return BlockStart.LeafConsumedLine
    }

    private fun startFencedCode(): BlockStart {
        val fence = scanFenceOpener(line, nextNonspace) ?: return BlockStart.None
        closeUnmatchedBlocks()
        addChild(
            OpenBlock.FencedCode(
                startLine = lineNumber,
                fence = fence,
                fenceOffset = indent,
                info = unescapeString(line, nextNonspace + fence.fenceLength, line.length).trim(),
            ),
        )
        return BlockStart.LeafConsumedLine
    }

    /**
     * Display math: a line that is exactly `$$` or `\[` opens a block closed by a line ending in `$$` or `\]`;
     * a line that starts with the opener and whose first closer ends the line is a single-line block. Any
     * other line stays paragraph text, where inline math applies.
     */
    private fun startMath(): BlockStart {
        if (!dialect.blockFeatures.mathBlocks) {
            return BlockStart.None
        }
        val delimiter = MathBlockDelimiter.entries.firstOrNull { line.startsWith(it.opener, nextNonspace) } ?: return BlockStart.None
        val end = trimmedEnd(line, nextNonspace)
        val contentStart = nextNonspace + delimiter.opener.length
        val isMultiLine = end == contentStart
        val closerIndex = line.indexOf(delimiter.closer, contentStart)
        val isSingleLine = closerIndex >= 0 && closerIndex == end - delimiter.closer.length
        if (!isMultiLine && !isSingleLine) {
            return BlockStart.None
        }
        val fenceOffset = indent
        closeUnmatchedBlocks()
        val math = OpenBlock.Math(
            startLine = lineNumber,
            delimiter = delimiter,
            fenceOffset = fenceOffset,
            isSingleLine = isSingleLine,
        )
        if (isSingleLine) {
            math.lines += ContentLine(lineIndex = lineNumber, text = line.substring(contentStart, closerIndex), sourceStart = lineStart + contentStart)
        }
        addChild(math)
        return BlockStart.LeafConsumedLine
    }

    private fun startSetextHeading(container: OpenBlock): BlockStart {
        if (container !is OpenBlock.Paragraph) {
            return BlockStart.None
        }
        val level = setextUnderlineLevel(line, nextNonspace) ?: return BlockStart.None
        closeUnmatchedBlocks()
        val definitionLines = leadingDefinitionLineCount(container.lines)
        // As in cmark, an underline below link reference definitions alone is paragraph text, never a
        // thematic break, list item or table delimiter row.
        if (definitionLines == container.lines.size) {
            return BlockStart.Text
        }
        val parent = container.parent!!
        val heading = OpenBlock.Heading(
            startLine = container.lines[definitionLines].lineIndex,
            level = level,
            isSetext = true,
            lines = container.lines.drop(definitionLines),
        )
        if (definitionLines > 0) {
            repeat(container.lines.size - definitionLines) { container.lines.removeAt(container.lines.lastIndex) }
            finalize(container, container.lines.last().lineIndex)
        } else {
            parent.children.removeAt(parent.children.lastIndex)
        }
        heading.parent = parent
        parent.children += heading
        tip = heading
        return BlockStart.LeafConsumedLine
    }

    private fun startThematicBreak(): BlockStart {
        if (!isThematicBreak(line, nextNonspace)) {
            return BlockStart.None
        }
        closeUnmatchedBlocks()
        addChild(OpenBlock.ThematicBreak(startLine = lineNumber, marker = line.substring(nextNonspace, trimmedEnd(line, nextNonspace))))
        return BlockStart.LeafConsumedLine
    }

    private fun startListItem(container: OpenBlock): BlockStart {
        if (container.depth >= MAX_CONTAINER_DEPTH) {
            return BlockStart.None
        }
        val marker = scanListMarker(line, nextNonspace) ?: return BlockStart.None
        val afterMarker = nextNonspace + marker.length
        if (charAt(afterMarker)?.isSpaceOrTab() == false) {
            return BlockStart.None
        }
        // An item interrupting a paragraph must have content, and an ordered one must start at 1.
        if (container is OpenBlock.Paragraph && (isBlankFrom(line, afterMarker) || (marker.bulletChar == null && marker.startNumber != 1))) {
            return BlockStart.None
        }
        val markerOffset = indent
        advanceNextNonspace()
        advanceOffset(marker.length, columns = true)
        val spacesStartColumn = column
        val spacesStartOffset = offset
        do {
            advanceOffset(1, columns = true)
        } while (column - spacesStartColumn < 5 && charAt(offset)?.isSpaceOrTab() == true)
        val isBlankItem = offset >= line.length
        val spacesAfterMarker = column - spacesStartColumn
        val padding = if (spacesAfterMarker >= 5 || spacesAfterMarker < 1 || isBlankItem) {
            // Content starts one space after the marker; further spaces belong to the content (indented code).
            column = spacesStartColumn
            offset = spacesStartOffset
            if (charAt(offset)?.isSpaceOrTab() == true) {
                advanceOffset(1, columns = true)
            }
            marker.length + 1
        } else {
            marker.length + spacesAfterMarker
        }

        closeUnmatchedBlocks()
        val currentList = tip as? OpenBlock.ListContainer
        if (currentList == null || !currentList.marker.continuesListOf(marker)) {
            addChild(OpenBlock.ListContainer(startLine = lineNumber, marker = marker))
        }
        addChild(OpenBlock.Item(startLine = lineNumber, markerOffset = markerOffset, padding = padding))
        return BlockStart.Container
    }

    private fun ListMarker.continuesListOf(item: ListMarker): Boolean = bulletChar == item.bulletChar && delimiter == item.delimiter

    /** GFM task list item: `[ ]`, `[x]` or `[X]` followed by a space, tab or line end, first on the item's line. */
    private fun startsTaskMarker(item: OpenBlock.Item): Boolean {
        if (item.startLine != lineNumber || item.children.isNotEmpty() || item.taskState != null || indented) {
            return false
        }
        if (charAt(nextNonspace) != '[' || charAt(nextNonspace + 2) != ']' || charAt(nextNonspace + 3)?.isSpaceOrTab() == false) {
            return false
        }
        item.taskState = when (charAt(nextNonspace + 1)) {
            ' ' -> TaskState.Unchecked
            'x', 'X' -> TaskState.Checked
            else -> return false
        }
        advanceNextNonspace()
        advanceOffset(3, columns = false)
        findNextNonspace()
        return true
    }

    private fun startIndentedCode(): BlockStart {
        if (tip is OpenBlock.Paragraph || blank) {
            return BlockStart.None
        }
        advanceOffset(CODE_INDENT, columns = true)
        closeUnmatchedBlocks()
        addChild(OpenBlock.IndentedCode(startLine = lineNumber))
        return BlockStart.Leaf
    }

    /** cmark-gfm: a delimiter row turns the last line of the paragraph above into a table header. */
    private fun startTable(container: OpenBlock): BlockStart {
        if (!dialect.blockFeatures.tables || container !is OpenBlock.Paragraph || container.triedTableHeader) {
            return BlockStart.None
        }
        val alignments = scanTableDelimiterRow(line, nextNonspace) ?: return BlockStart.None
        val headerLine = container.lines.last()
        val headerCells = scanTableRow(headerLine.text, 0)
        if (headerCells == null || headerCells.size != alignments.size) {
            container.triedTableHeader = true
            return BlockStart.None
        }
        closeUnmatchedBlocks()
        val parent = container.parent!!
        val table = OpenBlock.Table(
            startLine = headerLine.lineIndex,
            paragraphStartLine = container.startLine,
            alignments = alignments,
            header = TableRowDraft(lineIndex = headerLine.lineIndex, sourceStart = headerLine.sourceStart, cells = headerCells),
        )
        if (container.lines.size > 1) {
            container.lines.removeAt(container.lines.lastIndex)
            finalize(container, container.lines.last().lineIndex)
        } else {
            parent.children.removeAt(parent.children.lastIndex)
        }
        table.parent = parent
        parent.children += table
        tip = table
        return BlockStart.LeafConsumedLine
    }

    // endregion

    // region Tree operations

    private fun addChild(block: OpenBlock) {
        while (!tip.canContain(block)) {
            finalize(tip, lineNumber - 1)
        }
        block.parent = tip
        block.depth = tip.depth + 1
        tip.children += block
        tip = block
    }

    private fun closeUnmatchedBlocks() {
        if (allClosed) {
            return
        }
        while (oldTip !== lastMatchedContainer) {
            val parent = oldTip.parent!!
            finalize(oldTip, lineNumber - 1)
            oldTip = parent
        }
        allClosed = true
    }

    private fun finalize(block: OpenBlock, endLine: Int) {
        block.isOpen = false
        block.endLine = endLine
        when (block) {
            is OpenBlock.Paragraph -> finalizeParagraph(block)
            is OpenBlock.Item -> block.endLine = block.children.lastOrNull()?.endLine ?: block.startLine
            is OpenBlock.ListContainer -> finalizeList(block)
            is OpenBlock.IndentedCode -> {
                while (block.lines.isNotEmpty() && isBlankFrom(block.lines.last().text, 0)) {
                    block.lines.removeAt(block.lines.lastIndex)
                }
                block.endLine = block.lines.lastOrNull()?.lineIndex ?: block.startLine
            }

            else -> Unit
        }
        tip = block.parent ?: document
    }

    /** Leading link reference definitions become [definitions] (the first one per label wins); they are not paragraph content. */
    private fun finalizeParagraph(paragraph: OpenBlock.Paragraph) {
        var consumedLines = 0
        scanLeadingDefinitions(paragraph.lines).forEach { (definition, lineCount) ->
            val firstLine = paragraph.lines[consumedLines]
            val lastLine = paragraph.lines[consumedLines + lineCount - 1]
            if (definition.label !in definitions) {
                definitions[definition.label] = LinkReferenceDefinition(
                    label = definition.label,
                    destination = definition.destination,
                    title = definition.title,
                    range = TextRange(
                        start = lines[firstLine.lineIndex].range.start,
                        endExclusive = lines[lastLine.lineIndex].range.endExclusive,
                    ),
                )
            }
            consumedLines += lineCount
        }
        paragraph.definitionLineCount = consumedLines
    }

    /** Number of leading paragraph lines that hold link reference definitions. */
    private fun leadingDefinitionLineCount(paragraphLines: List<ContentLine>): Int =
        scanLeadingDefinitions(paragraphLines).sumOf { (_, lineCount) -> lineCount }

    /** Link reference definitions at the start of a paragraph, each with the number of lines it spans. */
    private fun scanLeadingDefinitions(paragraphLines: List<ContentLine>): List<Pair<ScannedDefinition, Int>> {
        if (!dialect.inlineFeatures.referenceLinks) {
            return emptyList()
        }
        val text = paragraphLines.joinToString(separator = "\n") { it.text }
        val scanned = mutableListOf<Pair<ScannedDefinition, Int>>()
        var position = 0
        while (position < text.length && text[position] == '[') {
            val definition = scanLinkReferenceDefinition(text, position) ?: break
            scanned += definition to text.lineCountBetween(position, definition.endExclusive)
            position = definition.endExclusive
        }
        return scanned
    }

    /**
     * A list is loose when an item is followed by a blank line before the next item, or when an item
     * holds two children separated by a blank line (commonmark.js compares source line positions).
     */
    private fun finalizeList(list: OpenBlock.ListContainer) {
        list.endLine = list.children.last().endLine
        list.isTight = list.children.withIndex().none { (index, item) ->
            val nextItem = list.children.getOrNull(index + 1)
            (nextItem != null && nextItem.startLine != item.endLine + 1) ||
                item.children.zipWithNext().any { (child, nextChild) -> nextChild.startLine != child.endLine + 1 }
        }
    }

    // endregion

    // region Line content

    private fun addLine() {
        val block = tip
        var padding = ""
        if (partiallyConsumedTab) {
            offset += 1
            padding = " ".repeat(4 - column % 4)
        }
        val contentLine = ContentLine(lineIndex = lineNumber, text = padding + line.substring(offset), sourceStart = lineStart + offset)
        when (block) {
            is OpenBlock.Paragraph -> block.lines += contentLine
            is OpenBlock.FencedCode -> block.lines += contentLine
            is OpenBlock.IndentedCode -> block.lines += contentLine
            is OpenBlock.Math -> block.lines += contentLine
            else -> error("${block::class.simpleName} does not accept lines")
        }
    }

    private fun addTableRow(table: OpenBlock.Table) {
        val cells = checkNotNull(scanTableRow(line, offset)) { "A table only continues on a line with cells" }
        table.rows += TableRowDraft(lineIndex = lineNumber, sourceStart = lineStart, cells = cells)
    }

    private fun charAt(index: Int): Char? = line.getOrNull(index)

    private fun findNextNonspace() {
        var index = offset
        var columns = column
        while (index < line.length) {
            when (line[index]) {
                ' ' -> columns += 1
                '\t' -> columns += 4 - columns % 4
                else -> break
            }
            index += 1
        }
        blank = index == line.length
        nextNonspace = index
        nextNonspaceColumn = columns
        indent = nextNonspaceColumn - column
        indented = indent >= CODE_INDENT
    }

    private fun advanceNextNonspace() {
        offset = nextNonspace
        column = nextNonspaceColumn
        partiallyConsumedTab = false
    }

    /** Advances [count] characters, or [count] columns when [columns] is set (possibly splitting a tab). */
    private fun advanceOffset(count: Int, columns: Boolean) {
        var remaining = count
        while (remaining > 0 && offset < line.length) {
            if (line[offset] == '\t') {
                val charsToTab = 4 - column % 4
                if (columns) {
                    partiallyConsumedTab = charsToTab > remaining
                    val charsToAdvance = minOf(charsToTab, remaining)
                    column += charsToAdvance
                    offset += if (partiallyConsumedTab) 0 else 1
                    remaining -= charsToAdvance
                } else {
                    partiallyConsumedTab = false
                    column += charsToTab
                    offset += 1
                    remaining -= 1
                }
            } else {
                partiallyConsumedTab = false
                offset += 1
                column += 1
                remaining -= 1
            }
        }
    }

    // endregion

    private fun OpenBlock.frameMarker(): String = when (this) {
        is OpenBlock.Document -> "document"
        is OpenBlock.BlockQuote -> "blockquote"
        is OpenBlock.ListContainer -> "list"
        is OpenBlock.Item -> "list-item"
        is OpenBlock.Paragraph -> "paragraph"
        is OpenBlock.Heading -> "heading"
        is OpenBlock.ThematicBreak -> "thematic-break"
        is OpenBlock.FencedCode -> "fenced-code"
        is OpenBlock.IndentedCode -> "indented-code"
        is OpenBlock.Math -> "math-block"
        is OpenBlock.Table -> "table"
    }

    private companion object {
        const val CODE_INDENT = 4

        /**
         * Block quotes and list items do not open below this depth (a list item counts twice, with its list);
         * deeper markers stay paragraph text. This bounds the work per line and the depth of the block tree,
         * which every consumer walks recursively.
         */
        const val MAX_CONTAINER_DEPTH = 64
    }
}

private val MathBlockDelimiter.opener: String
    get() = when (this) {
        MathBlockDelimiter.Dollar -> "$$"
        MathBlockDelimiter.Bracket -> "\\["
    }

private val MathBlockDelimiter.closer: String
    get() = when (this) {
        MathBlockDelimiter.Dollar -> "$$"
        MathBlockDelimiter.Bracket -> "\\]"
    }

/** Lines spanned by `text[start, endExclusive)`, where a final `\n` ends the last line. */
private fun String.lineCountBetween(start: Int, endExclusive: Int): Int {
    var count = 1
    for (index in start until endExclusive) {
        if (this[index] == '\n' && index != endExclusive - 1) {
            count += 1
        }
    }
    return count
}
