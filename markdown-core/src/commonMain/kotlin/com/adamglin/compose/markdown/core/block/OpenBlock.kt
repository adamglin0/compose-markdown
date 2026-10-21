package com.adamglin.compose.markdown.core.block

import com.adamglin.compose.markdown.core.internal.SharedArray
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.MathBlockDelimiter
import com.adamglin.compose.markdown.core.model.TableAlignment
import com.adamglin.compose.markdown.core.model.TaskState

/** One line of leaf content: [text] from the line's current offset, starting at source offset [sourceStart]. */
internal class ContentLine(
    val lineIndex: Int,
    val text: String,
    val sourceStart: Int,
)

/**
 * Mutable block tree built while lines are incorporated; see [BlockTreeBuilder] for the public model.
 * Line numbers ([startLine], [endLine], [ContentLine.lineIndex]) are absolute source line numbers.
 */
internal sealed class OpenBlock(val startLine: Int) {
    var parent: OpenBlock? = null
    var children: MutableList<OpenBlock> = ArrayList()
    var isOpen: Boolean = true
    var endLine: Int = startLine

    /** Number of ancestors; the document is at depth 0. */
    var depth: Int = 0

    /** Created or copied by a fork: its content may still change, so it is never cached. */
    var isSpeculative: Boolean = false

    // Build cache, kept by BlockTreeBuilder for closed, non-speculative blocks.

    /** The built node; stale (but still holding the ids) when [isBuilt] is false. */
    var built: BlockNode? = null
    var isBuilt: Boolean = false

    /** Reference labels the built node's inline content uses, including its descendants'. */
    var labels: Set<String> = emptySet()

    /** Index of [built] in the parent's [childCache], or -1. */
    var cacheIndex: Int = -1

    /** Built nodes of the leading closed children; shared with forks of this block. */
    var childCache: ChildCache = ChildCache()
        private set

    class Document : OpenBlock(startLine = 0)

    class BlockQuote(startLine: Int) : OpenBlock(startLine)

    class ListContainer(startLine: Int, val marker: ListMarker) : OpenBlock(startLine) {
        /** Whether a blank line separates two items, or two children of an item that is not the last. */
        var hasGap: Boolean = false
        var isTight: Boolean = true
    }

    class Item(startLine: Int, val markerOffset: Int, val padding: Int) : OpenBlock(startLine) {
        var taskState: TaskState? = null

        /** Whether a blank line separates two of its children. */
        var hasGapBetweenChildren: Boolean = false
    }

    class Paragraph(startLine: Int) : OpenBlock(startLine) {
        var lines: MutableList<ContentLine> = ArrayList()

        /** The text of [lines], joined as they arrive (until lines are removed when the paragraph turns into something else). */
        var text: JoinedLines = JoinedLines()

        /** Leading lines that held link reference definitions; they are not paragraph content. */
        var definitionLineCount: Int = 0

        /** cmark-gfm only tries to turn a paragraph into a table header once. */
        var triedTableHeader: Boolean = false
    }

    class Heading(startLine: Int, val level: Int, val isSetext: Boolean, val lines: List<ContentLine>) : OpenBlock(startLine)

    class ThematicBreak(startLine: Int, val marker: String) : OpenBlock(startLine)

    class FencedCode(startLine: Int, val fence: FenceOpener, val fenceOffset: Int, val info: String) : OpenBlock(startLine) {
        var text: JoinedLines = JoinedLines()
        var isClosed: Boolean = false
    }

    class IndentedCode(startLine: Int) : OpenBlock(startLine) {
        var lines: MutableList<ContentLine> = ArrayList()
    }

    /** Display math; a single-line block (`$$x$$`) is complete as soon as it starts. */
    class Math(startLine: Int, val delimiter: MathBlockDelimiter, val fenceOffset: Int, val isSingleLine: Boolean) : OpenBlock(startLine) {
        var text: JoinedLines = JoinedLines()
        var isClosed: Boolean = isSingleLine
    }

    class Table(startLine: Int, val alignments: List<TableAlignment>, val header: TableRowDraft) : OpenBlock(startLine) {
        var rows: MutableList<TableRowDraft> = ArrayList()

        /** Built nodes of the leading non-speculative rows; shared with forks of this table. */
        var rowCache: SharedArray<BlockNode.TableRow> = SharedArray()
            private set

        fun forkRows(from: Table) {
            rows = ForkedList(from.rows)
            rowCache = from.rowCache
        }
    }

    fun canContain(child: OpenBlock): Boolean = when (this) {
        is Document, is BlockQuote, is Item -> child !is Item
        is ListContainer -> child is Item
        else -> false
    }

    /** Whether the final step of a line may add text (or a table row) to this block. */
    val acceptsLines: Boolean
        get() = this is Paragraph || this is FencedCode || this is IndentedCode || (this is Math && !isSingleLine) || this is Table

    /**
     * A speculative copy of this open block under [parent]: same state, with forked child and line lists
     * and a shared child cache. Writing to the copy never changes this block.
     */
    fun fork(parent: OpenBlock?): OpenBlock {
        val copy = when (this) {
            is Document -> Document()
            is BlockQuote -> BlockQuote(startLine)
            is ListContainer -> ListContainer(startLine, marker).also {
                it.hasGap = hasGap
                it.isTight = isTight
            }
            is Item -> Item(startLine, markerOffset, padding).also {
                it.taskState = taskState
                it.hasGapBetweenChildren = hasGapBetweenChildren
            }
            is Paragraph -> Paragraph(startLine).also {
                it.lines = ForkedList(lines)
                it.text = text.fork()
                it.definitionLineCount = definitionLineCount
                it.triedTableHeader = triedTableHeader
            }
            is FencedCode -> FencedCode(startLine, fence, fenceOffset, info).also {
                it.text = text.fork()
                it.isClosed = isClosed
            }
            is IndentedCode -> IndentedCode(startLine).also { it.lines = ForkedList(lines) }
            is Math -> Math(startLine, delimiter, fenceOffset, isSingleLine).also {
                it.text = text.fork()
                it.isClosed = isClosed
            }
            is Table -> Table(startLine, alignments, header).also { it.forkRows(from = this) }
            is Heading, is ThematicBreak -> error("${this::class.simpleName} is never open after its line")
        }
        copy.parent = parent
        copy.children = ForkedList(children)
        copy.isOpen = isOpen
        copy.endLine = endLine
        copy.depth = depth
        copy.isSpeculative = true
        copy.childCache = childCache
        return copy
    }
}

/** Built nodes of a block's leading closed, non-speculative children, in order. */
internal class ChildCache {
    val nodes: SharedArray<BlockNode> = SharedArray()

    /** Number of leading children whose nodes are in [nodes]; a child may build to no node. */
    var consumed: Int = 0
}

internal class TableRowDraft(
    val lineIndex: Int,
    val sourceStart: Int,
    val cells: List<TableCellSpan>,
    val isSpeculative: Boolean,
) {
    var labels: Set<String> = emptySet()
}
