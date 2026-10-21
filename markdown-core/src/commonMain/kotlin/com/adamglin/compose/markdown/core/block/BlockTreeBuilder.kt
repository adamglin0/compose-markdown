package com.adamglin.compose.markdown.core.block

import com.adamglin.compose.markdown.core.internal.BlockIdentity
import com.adamglin.compose.markdown.core.model.BlockId
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.HeadingStyle
import com.adamglin.compose.markdown.core.model.InlineNode
import com.adamglin.compose.markdown.core.model.LineRange
import com.adamglin.compose.markdown.core.model.ListType
import com.adamglin.compose.markdown.core.model.TextRange
import com.adamglin.compose.markdown.core.source.LineIndex

/** Inline content of one leaf and the reference labels it uses. */
internal class ResolvedInline(
    val nodes: List<InlineNode>,
    val labels: Set<String>,
)

/** Parses the raw inline text of the leaf with [id]; see the engine's inline cache. */
internal fun interface InlineResolver {
    fun resolve(id: BlockId, literal: String, range: TextRange): ResolvedInline
}

/** Hands out a block id for an identity key; [isFinal] ids are never requested again. */
internal fun interface BlockIdSource {
    fun allocate(kind: String, start: Int, depth: Int, discriminator: String, isFinal: Boolean): BlockId
}

/**
 * Turns [OpenBlock] trees into immutable [BlockNode]s with resolved inline content. Every block spans
 * whole source lines.
 *
 * A closed block that is not speculative is final, so it is built once and cached on the block; an open
 * container reuses the cached nodes of its closed children (and an open table those of its rows) through
 * a shared array, so rebuilding it costs time proportional to its open part, not to its size. Ids of
 * final blocks are kept when they are rebuilt for a changed link reference definition ([invalidate]).
 */
internal class BlockTreeBuilder(
    private val lineIndex: LineIndex,
    private val ids: BlockIdSource,
    private val inline: InlineResolver,
) {
    /** Blocks (at any depth) built so far, not counting cache hits. */
    var builtCount: Int = 0
        private set

    /** Builds [block]; returns null for a paragraph that holds only link reference definitions. */
    fun build(block: OpenBlock): BlockNode? = build(block, labels = null)

    /**
     * Marks the cached nodes in [block] (closed or open) and its descendants that use any of [labels] as
     * stale and rebuilds the cached children among them, so that the next [build] resolves their inline
     * content again.
     */
    fun invalidate(block: OpenBlock, labels: Set<String>) {
        if (block.isBuilt) {
            if (block.labels.none(labels::contains)) {
                return
            }
            block.isBuilt = false
        }
        val cache = block.childCache
        for (index in 0 until cache.consumed) {
            val child = block.children[index]
            if (child.labels.any(labels::contains)) {
                invalidate(child, labels)
                val node = build(child, labels = null)
                if (child.cacheIndex >= 0) {
                    cache.nodes[child.cacheIndex] = checkNotNull(node) { "a block that used labels still builds to a node" }
                }
            }
        }
        if (block is OpenBlock.Table) {
            val rowCache = block.rowCache
            for (index in 0 until rowCache.size) {
                val row = block.rows[index]
                if (row.labels.any(labels::contains)) {
                    rowCache[index] = buildRow(block, row, isHeader = false, isFinal = true, previous = rowCache[index], labels = null)
                }
            }
        }
        // Children not cached here yet (the open one, and any that closed since this block was last built)
        // may still hold cached nodes of their own.
        for (index in cache.consumed until block.children.size) {
            invalidate(block.children[index], labels)
        }
    }

    private fun build(block: OpenBlock, labels: MutableSet<String>?): BlockNode? {
        if (block.isOpen || block.isSpeculative) {
            return buildNode(block, isFinal = false, labels = labels)
        }
        if (!block.isBuilt) {
            val own = HashSet<String>()
            block.built = buildNode(block, isFinal = true, labels = own)
            block.labels = own
            block.isBuilt = true
        }
        labels?.addAll(block.labels)
        return block.built
    }

    private fun buildNode(block: OpenBlock, isFinal: Boolean, labels: MutableSet<String>?): BlockNode? {
        builtCount += 1
        return when (block) {
            is OpenBlock.Document -> error("The document is not a child block")
            is OpenBlock.BlockQuote -> BlockNode.BlockQuote(
                id = idOf(block, "blockquote", "container", isFinal),
                range = rangeOf(block.startLine, block.endLine),
                lineRange = lineRangeOf(block.startLine, block.endLine),
                children = buildChildren(block, labels),
            )

            is OpenBlock.ListContainer -> {
                val type = block.marker.toListType()
                BlockNode.ListBlock(
                    id = idOf(block, "list-block", BlockIdentity.listDiscriminator(type), isFinal),
                    range = rangeOf(block.startLine, block.endLine),
                    lineRange = lineRangeOf(block.startLine, block.endLine),
                    type = type,
                    items = buildChildren(block, labels).asItems(),
                    isLoose = !block.isTight,
                )
            }

            is OpenBlock.Item -> BlockNode.ListItem(
                id = idOf(block, "list-item", BlockIdentity.LIST_ITEM_DISCRIMINATOR, isFinal),
                range = rangeOf(block.startLine, block.endLine),
                lineRange = lineRangeOf(block.startLine, block.endLine),
                children = buildChildren(block, labels),
                taskState = block.taskState,
            )

            is OpenBlock.Paragraph -> buildParagraph(block, isFinal, labels)
            is OpenBlock.Heading -> {
                val style = if (block.isSetext) HeadingStyle.Setext else HeadingStyle.Atx
                val id = idOf(block, "heading", BlockIdentity.headingDiscriminator(style = style, level = block.level), isFinal)
                BlockNode.Heading(
                    id = id,
                    range = rangeOf(block.startLine, block.endLine),
                    lineRange = lineRangeOf(block.startLine, block.endLine),
                    level = block.level,
                    children = inlineContent(id, block.lines, labels),
                    style = style,
                )
            }

            is OpenBlock.ThematicBreak -> BlockNode.ThematicBreak(
                id = idOf(block, "thematic-break", block.marker.filterNot(Char::isWhitespace), isFinal),
                range = rangeOf(block.startLine, block.endLine),
                lineRange = lineRangeOf(block.startLine, block.endLine),
                marker = block.marker,
            )

            is OpenBlock.FencedCode -> {
                val infoString = block.info.ifEmpty { null }
                BlockNode.FencedCodeBlock(
                    id = idOf(block, "fenced-code", block.info, isFinal),
                    range = rangeOf(block.startLine, block.endLine),
                    lineRange = lineRangeOf(block.startLine, block.endLine),
                    infoString = infoString,
                    languageHint = normalizeFenceLanguageHint(infoString),
                    literal = block.text.toString(),
                    isClosed = block.isClosed,
                )
            }

            is OpenBlock.IndentedCode -> BlockNode.IndentedCodeBlock(
                id = idOf(block, "indented-code", "indented", isFinal),
                range = rangeOf(block.startLine, block.endLine),
                lineRange = lineRangeOf(block.startLine, block.endLine),
                literal = block.lines.joinToString(separator = "\n") { it.text },
            )

            is OpenBlock.Math -> BlockNode.MathBlock(
                id = idOf(block, "math-block", block.delimiter.name, isFinal),
                range = rangeOf(block.startLine, block.endLine),
                lineRange = lineRangeOf(block.startLine, block.endLine),
                latex = block.text.toString().trim(),
                isClosed = block.isClosed,
                delimiter = block.delimiter,
            )

            is OpenBlock.Table -> buildTable(block, isFinal, labels)
        }
    }

    /** Cached nodes of the leading closed children, followed by the others built now. */
    private fun buildChildren(block: OpenBlock, labels: MutableSet<String>?): List<BlockNode> {
        val cache = block.childCache
        val children = block.children
        while (cache.consumed < children.size) {
            val child = children[cache.consumed]
            if (child.isOpen || child.isSpeculative) {
                break
            }
            build(child, labels = null)?.let { node ->
                child.cacheIndex = cache.nodes.size
                cache.nodes.add(node)
            }
            cache.consumed += 1
        }
        if (labels != null) {
            for (index in 0 until cache.consumed) {
                labels += children[index].labels
            }
        }
        val rest = ArrayList<BlockNode>(children.size - cache.consumed)
        for (index in cache.consumed until children.size) {
            build(children[index], labels)?.let(rest::add)
        }
        return cache.nodes.followedBy(rest)
    }

    private fun buildParagraph(block: OpenBlock.Paragraph, isFinal: Boolean, labels: MutableSet<String>?): BlockNode.Paragraph? {
        if (block.definitionLineCount == block.lines.size) {
            return null
        }
        val contentLines = block.lines.subList(block.definitionLineCount, block.lines.size)
        val startLine = contentLines.first().lineIndex
        val endLine = contentLines.last().lineIndex
        val id = idOf(block, "paragraph", "paragraph", isFinal, startLine = startLine)
        val joined = if (block.text.lineCount == block.lines.size) {
            var definitionLength = 0
            for (index in 0 until block.definitionLineCount) {
                definitionLength += block.lines[index].text.length + 1
            }
            block.text.substring(definitionLength)
        } else {
            contentLines.joinToString(separator = "\n") { it.text }
        }
        return BlockNode.Paragraph(
            id = id,
            range = rangeOf(startLine, endLine),
            lineRange = lineRangeOf(startLine, endLine),
            children = inlineContent(id, contentLines, labels, joined),
        )
    }

    private fun buildTable(block: OpenBlock.Table, isFinal: Boolean, labels: MutableSet<String>?): BlockNode.TableBlock {
        val rowCache = block.rowCache
        while (rowCache.size < block.rows.size && !block.rows[rowCache.size].isSpeculative) {
            val row = block.rows[rowCache.size]
            rowCache.add(buildRow(block, row, isHeader = false, isFinal = true, previous = null, labels = null))
        }
        if (labels != null) {
            for (index in 0 until rowCache.size) {
                labels += block.rows[index].labels
            }
        }
        val rest = ArrayList<BlockNode.TableRow>(block.rows.size - rowCache.size)
        for (index in rowCache.size until block.rows.size) {
            rest += buildRow(block, block.rows[index], isHeader = false, isFinal = false, previous = null, labels = labels)
        }
        val previousHeader = (block.built as BlockNode.TableBlock?)?.header?.takeIf { isFinal }
        return BlockNode.TableBlock(
            id = idOf(block, "table", BlockIdentity.tableDiscriminator(alignments = block.alignments), isFinal),
            range = rangeOf(block.startLine, block.endLine),
            lineRange = lineRangeOf(block.startLine, block.endLine),
            header = buildRow(block, block.header, isHeader = true, isFinal = isFinal, previous = previousHeader, labels = labels),
            alignments = block.alignments,
            rows = rowCache.followedBy(rest),
        )
    }

    /**
     * Rows keep exactly as many cells as the table has columns: extra cells are dropped and missing ones
     * are empty. A final row keeps the ids of its [previous] node.
     */
    private fun buildRow(
        table: OpenBlock.Table,
        row: TableRowDraft,
        isHeader: Boolean,
        isFinal: Boolean,
        previous: BlockNode.TableRow?,
        labels: MutableSet<String>?,
    ): BlockNode.TableRow {
        builtCount += 1
        val rowLabels = if (isFinal && !isHeader) HashSet<String>() else labels
        val rowStart = lineIndex.lineStart(row.lineIndex)
        val lineRange = lineRangeOf(row.lineIndex, row.lineIndex)
        val contentEnd = lineIndex.contentEnd(row.lineIndex)
        val rowId = previous?.id ?: ids.allocate(
            "table-row",
            rowStart,
            table.depth + 1,
            BlockIdentity.tableRowDiscriminator(isHeader = isHeader),
            isFinal,
        )
        val node = BlockNode.TableRow(
            id = rowId,
            range = TextRange(start = rowStart, endExclusive = lineIndex.lineEnd(row.lineIndex)),
            lineRange = lineRange,
            cells = List(table.alignments.size) { index ->
                val cell = row.cells.getOrNull(index)
                val range = if (cell == null) {
                    TextRange(start = contentEnd, endExclusive = contentEnd)
                } else {
                    TextRange(start = row.sourceStart + cell.start, endExclusive = row.sourceStart + cell.endExclusive)
                }
                val id = previous?.cells?.get(index)?.id ?: ids.allocate(
                    "table-cell",
                    range.start,
                    table.depth + 2,
                    BlockIdentity.tableCellDiscriminator(rowStart = rowStart, index = index),
                    isFinal,
                )
                BlockNode.TableCell(
                    id = id,
                    range = range,
                    lineRange = lineRange,
                    children = if (cell == null || cell.text.isEmpty()) emptyList() else resolve(id, cell.text, range, rowLabels),
                )
            },
            isHeader = isHeader,
        )
        if (rowLabels !== labels) {
            row.labels = rowLabels.orEmpty()
            labels?.addAll(row.labels)
        }
        return node
    }

    /** Joins content lines, drops trailing spaces and tabs (they never reach the rendered text) and resolves inline content. */
    private fun inlineContent(
        id: BlockId,
        contentLines: List<ContentLine>,
        labels: MutableSet<String>?,
        joined: String = contentLines.joinToString(separator = "\n") { it.text },
    ): List<InlineNode> {
        if (contentLines.isEmpty()) {
            return emptyList()
        }
        val literal = joined.trimEnd(' ', '\t')
        if (literal.isEmpty()) {
            return emptyList()
        }
        val start = contentLines.first().sourceStart
        val end = lineIndex.contentEnd(contentLines.last().lineIndex)
        return resolve(id, literal, TextRange(start = start, endExclusive = maxOf(start, end)), labels)
    }

    private fun resolve(id: BlockId, literal: String, range: TextRange, labels: MutableSet<String>?): List<InlineNode> {
        val resolved = inline.resolve(id, literal, range)
        labels?.addAll(resolved.labels)
        return resolved.nodes
    }

    /** A final block that is rebuilt keeps its id; any other block asks for the id of its identity key. */
    private fun idOf(block: OpenBlock, kind: String, discriminator: String, isFinal: Boolean, startLine: Int = block.startLine): BlockId {
        if (isFinal) {
            block.built?.let { return it.id }
        }
        return ids.allocate(kind, lineIndex.lineStart(startLine), block.depth, discriminator, isFinal)
    }

    private fun rangeOf(startLine: Int, endLine: Int): TextRange =
        TextRange(start = lineIndex.lineStart(startLine), endExclusive = lineIndex.lineEnd(maxOf(startLine, endLine)))

    private fun lineRangeOf(startLine: Int, endLine: Int): LineRange =
        LineRange(startLine = startLine, endLineExclusive = maxOf(startLine, endLine) + 1)
}

@Suppress("UNCHECKED_CAST")
private fun List<BlockNode>.asItems(): List<BlockNode.ListItem> = this as List<BlockNode.ListItem>

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
