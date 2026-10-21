package com.adamglin.compose.markdown.core.block

import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.inline.unescapeString
import com.adamglin.compose.markdown.core.internal.LinkReferenceDefinition
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.MathBlockDelimiter
import com.adamglin.compose.markdown.core.model.TaskState
import com.adamglin.compose.markdown.core.model.TextRange
import com.adamglin.compose.markdown.core.source.IndexedLine
import com.adamglin.compose.markdown.core.source.LineIndex

/**
 * Streaming block structure parser following CommonMark 0.31.2, appendix "A parsing strategy" (the
 * commonmark.js reference implementation): each line first walks the stack of open blocks to match their
 * continuation markers, then looks for new block starts, and finally adds the remaining text to the
 * innermost block, possibly as a lazy paragraph continuation. Tabs advance to the next multiple of four
 * columns. GFM tables follow cmark-gfm's table extension; display math (`$$` / `\[`) is a fence-like leaf
 * block.
 *
 * Lines are incorporated one at a time and for good: a later line only changes the open blocks, which
 * always form the rightmost path of the tree, so closed blocks are final. An unterminated last line may
 * still grow into something else; it is parsed in a [fork], which copies only the open path and shares
 * every closed block, and then [finish]ed there. [BlockTreeBuilder] turns the trees into [BlockNode]s.
 */
internal class BlockParser private constructor(
    private val dialect: MarkdownDialect,
    private val lineIndex: LineIndex,
    private val document: OpenBlock.Document,
    /** Whether this parser is a speculative copy; blocks it creates or copies are marked speculative. */
    private val isFork: Boolean,
) {
    constructor(dialect: MarkdownDialect, lineIndex: LineIndex) : this(dialect, lineIndex, OpenBlock.Document(), isFork = false)

    private var tip: OpenBlock = document
    private var oldTip: OpenBlock = document
    private var lastMatchedContainer: OpenBlock = document
    private var allClosed = true

    /** Labels defined by paragraphs this parser finalized; the first definition of a label wins. */
    private val definedLabels = HashSet<String>()
    private val newDefinitions = ArrayList<LinkReferenceDefinition>()

    // State of the line being incorporated, as in commonmark.js.
    private var lineNumber = -1
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

    /** Incorporates the next source line into the open blocks. */
    fun incorporate(line: IndexedLine) {
        incorporateLine(line)
        // Headings, thematic breaks and single-line math never take another line; closing them right away
        // keeps every open block changeable, which is what the open path of a fork relies on.
        val finished = tip
        if (finished is OpenBlock.Heading || finished is OpenBlock.ThematicBreak || (finished is OpenBlock.Math && finished.isSingleLine)) {
            finalize(finished, lineNumber)
        }
    }

    /** Closes every open block. */
    fun finish() {
        while (tip !== document) {
            finalize(tip, lineNumber)
        }
    }

    /**
     * Top-level blocks closed since the last call, in document order. They are final; the parser forgets
     * them. At most one top-level block, the last, stays open.
     */
    fun takeClosedBlocks(): List<OpenBlock> {
        val children = document.children
        val closedCount = if (children.lastOrNull()?.isOpen == true) children.size - 1 else children.size
        if (closedCount == 0) {
            return emptyList()
        }
        val closed = children.subList(0, closedCount)
        val result = closed.toList()
        closed.clear()
        return result
    }

    /** Link reference definitions of the paragraphs finalized since the last call, in document order. */
    fun takeDefinitions(): List<LinkReferenceDefinition> {
        if (newDefinitions.isEmpty()) {
            return emptyList()
        }
        val result = newDefinitions.toList()
        newDefinitions.clear()
        return result
    }

    /** The top-level blocks; after [finish] they are all closed. */
    fun blocks(): List<OpenBlock> = document.children

    /** The open top-level block, if any: the only top-level block that later lines can still change. */
    fun openBlock(): OpenBlock? = document.children.lastOrNull()?.takeIf { it.isOpen }

    /**
     * A speculative copy for parsing a line that may still change. Only the open blocks are copied, with
     * forked child and line lists; closed blocks are shared, and the copy never writes to anything shared.
     */
    fun fork(): BlockParser {
        check(!isFork) { "a fork cannot be forked" }
        val copy = BlockParser(dialect = dialect, lineIndex = lineIndex, document = document.fork(parent = null) as OpenBlock.Document, isFork = true)
        var original: OpenBlock = document
        var copied: OpenBlock = copy.document
        while (true) {
            val child = original.children.lastOrNull()?.takeIf { it.isOpen } ?: break
            val childCopy = child.fork(parent = copied)
            copied.children[copied.children.lastIndex] = childCopy
            original = child
            copied = childCopy
        }
        check(original === tip) { "the open blocks must end at the tip" }
        copy.tip = copied
        copy.oldTip = copied
        copy.lastMatchedContainer = copied
        copy.lineNumber = lineNumber
        return copy
    }

    private fun incorporateLine(source: IndexedLine) {
        lineNumber = source.number
        line = source.content.replace('\u0000', '\uFFFD')
        lineStart = source.range.start
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
                block.text.append(line.substring(nextNonspace, contentEnd))
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
            math.text.append(line.substring(contentStart, closerIndex))
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
        replaceTip(parent, heading)
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
        addChild(OpenBlock.Item(startLine = lineNumber, marker = marker, markerOffset = markerOffset, padding = padding))
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
            alignments = alignments,
            header = TableRowDraft(
                lineIndex = headerLine.lineIndex,
                sourceStart = headerLine.sourceStart,
                cells = headerCells,
                isSpeculative = isFork,
            ),
        )
        if (container.lines.size > 1) {
            container.lines.removeAt(container.lines.lastIndex)
            finalize(container, container.lines.last().lineIndex)
        } else {
            parent.children.removeAt(parent.children.lastIndex)
        }
        replaceTip(parent, table)
        return BlockStart.LeafConsumedLine
    }

    // endregion

    // region Tree operations

    private fun addChild(block: OpenBlock) {
        while (!tip.canContain(block)) {
            finalize(tip, lineNumber - 1)
        }
        // The tip's children are all closed, so their end lines are final: record the blank lines that
        // make a list loose as children arrive instead of rescanning every item when the list closes.
        val parent = tip
        val previous = parent.children.lastOrNull()
        if (previous != null) {
            val gap = block.startLine != previous.endLine + 1
            if (parent is OpenBlock.Item && gap) {
                parent.hasGapBetweenChildren = true
            }
            if (parent is OpenBlock.ListContainer && (gap || (previous as OpenBlock.Item).hasGapBetweenChildren)) {
                parent.hasGap = true
            }
        }
        block.parent = parent
        block.depth = parent.depth + 1
        block.isSpeculative = isFork
        parent.children += block
        tip = block
    }

    /** Adds the setext heading or table that replaces the paragraph that was the last child of [parent]. */
    private fun replaceTip(parent: OpenBlock, block: OpenBlock) {
        block.parent = parent
        block.depth = parent.depth + 1
        block.isSpeculative = isFork
        parent.children += block
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

    /** Leading link reference definitions are not paragraph content; the first one per label wins. */
    private fun finalizeParagraph(paragraph: OpenBlock.Paragraph) {
        var consumedLines = 0
        scanLeadingDefinitions(paragraph.lines).forEach { (definition, lineCount) ->
            val firstLine = paragraph.lines[consumedLines]
            val lastLine = paragraph.lines[consumedLines + lineCount - 1]
            if (definedLabels.add(definition.label)) {
                newDefinitions += LinkReferenceDefinition(
                    label = definition.label,
                    destination = definition.destination,
                    title = definition.title,
                    range = TextRange(
                        start = lineIndex.lineStart(firstLine.lineIndex),
                        endExclusive = lineIndex.lineEnd(lastLine.lineIndex),
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
        // Paragraph lines start at a non-space character, so only a paragraph starting with `[` can
        // hold definitions; checking first keeps finalizing a long open paragraph cheap.
        if (!dialect.inlineFeatures.referenceLinks || paragraphLines.firstOrNull()?.text?.startsWith('[') != true) {
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
        val lastItem = list.children.last() as OpenBlock.Item
        list.endLine = lastItem.endLine
        list.isTight = !list.hasGap && !lastItem.hasGapBetweenChildren
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
            is OpenBlock.Paragraph -> {
                block.lines += contentLine
                block.text.append(contentLine.text)
            }
            is OpenBlock.FencedCode -> block.text.append(contentLine.text)
            is OpenBlock.IndentedCode -> block.lines += contentLine
            is OpenBlock.Math -> block.text.append(contentLine.text)
            else -> error("${block::class.simpleName} does not accept lines")
        }
    }

    private fun addTableRow(table: OpenBlock.Table) {
        val cells = checkNotNull(scanTableRow(line, offset)) { "A table only continues on a line with cells" }
        table.rows += TableRowDraft(lineIndex = lineNumber, sourceStart = lineStart, cells = cells, isSpeculative = isFork)
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
