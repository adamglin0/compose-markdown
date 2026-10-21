package com.adamglin.compose.markdown.core.internal

import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.HeadingStyle
import com.adamglin.compose.markdown.core.model.ListType
import com.adamglin.compose.markdown.core.model.TableAlignment

internal object BlockIdentity {
    const val LIST_ITEM_DISCRIMINATOR = "item"

    fun listDiscriminator(type: ListType): String = when (type) {
        is ListType.Bullet -> "bullet:${type.bulletChar}"
        is ListType.Ordered -> "ordered:${type.delimiter}"
    }

    fun headingDiscriminator(style: HeadingStyle, level: Int): String = "${style.name}:$level"

    fun tableDiscriminator(alignments: List<TableAlignment>): String = alignments.joinToString(separator = ",") { it.name }

    fun tableRowDiscriminator(isHeader: Boolean): String = if (isHeader) "header" else "row"

    fun tableCellDiscriminator(rowStart: Int, index: Int): String = "$rowStart:$index"

    fun blockIdLookup(blocks: List<BlockNode>): Map<BlockIdentityKey, MutableList<Long>> = linkedMapOf<BlockIdentityKey, MutableList<Long>>().also { lookup ->
        blocks.forEach { block -> collect(block = block, lookup = lookup) }
    }

    private fun collect(
        block: BlockNode,
        lookup: MutableMap<BlockIdentityKey, MutableList<Long>>,
        tableRowStart: Int? = null,
        tableCellIndex: Int? = null,
    ) {
        lookup.getOrPut(identityKey(block = block, tableRowStart = tableRowStart, tableCellIndex = tableCellIndex)) { mutableListOf() } += block.id.raw
        when (block) {
            is BlockNode.BlockQuote -> block.children.forEach { child -> collect(block = child, lookup = lookup) }
            is BlockNode.Document -> block.children.forEach { child -> collect(block = child, lookup = lookup) }
            is BlockNode.ListBlock -> block.items.forEach { item -> collect(block = item, lookup = lookup) }
            is BlockNode.ListItem -> block.children.forEach { child -> collect(block = child, lookup = lookup) }
            is BlockNode.TableBlock -> {
                collect(block = block.header, lookup = lookup)
                block.rows.forEach { row -> collect(block = row, lookup = lookup) }
            }
            is BlockNode.TableRow -> block.cells.forEachIndexed { index, cell ->
                collect(block = cell, lookup = lookup, tableRowStart = block.range.start, tableCellIndex = index)
            }
            is BlockNode.TableCell,
            is BlockNode.FencedCodeBlock,
            is BlockNode.IndentedCodeBlock,
            is BlockNode.Heading,
            is BlockNode.MathBlock,
            is BlockNode.Paragraph,
            is BlockNode.ThematicBreak,
            -> Unit
        }
    }

    private fun identityKey(
        block: BlockNode,
        tableRowStart: Int?,
        tableCellIndex: Int?,
    ): BlockIdentityKey = when (block) {
        is BlockNode.BlockQuote -> BlockIdentityKey(kind = "blockquote", start = block.range.start, discriminator = "container")
        is BlockNode.Document -> BlockIdentityKey(kind = "document", start = block.range.start, discriminator = "root")
        is BlockNode.FencedCodeBlock -> BlockIdentityKey(kind = "fenced-code", start = block.range.start, discriminator = block.infoString.orEmpty())
        is BlockNode.Heading -> BlockIdentityKey(
            kind = "heading",
            start = block.range.start,
            discriminator = headingDiscriminator(style = block.style, level = block.level),
        )
        is BlockNode.IndentedCodeBlock -> BlockIdentityKey(kind = "indented-code", start = block.range.start, discriminator = "indented")
        is BlockNode.ListBlock -> BlockIdentityKey(kind = "list-block", start = block.range.start, discriminator = listDiscriminator(block.type))
        is BlockNode.ListItem -> BlockIdentityKey(kind = "list-item", start = block.range.start, discriminator = LIST_ITEM_DISCRIMINATOR)
        is BlockNode.Paragraph -> BlockIdentityKey(kind = "paragraph", start = block.range.start, discriminator = "paragraph")
        is BlockNode.TableBlock -> BlockIdentityKey(
            kind = "table",
            start = block.range.start,
            discriminator = tableDiscriminator(alignments = block.alignments),
        )
        is BlockNode.TableCell -> BlockIdentityKey(
            kind = "table-cell",
            start = block.range.start,
            discriminator = tableCellDiscriminator(
                rowStart = tableRowStart ?: block.range.start,
                index = tableCellIndex ?: 0,
            ),
        )
        is BlockNode.TableRow -> BlockIdentityKey(
            kind = "table-row",
            start = block.range.start,
            discriminator = tableRowDiscriminator(isHeader = block.isHeader),
        )
        is BlockNode.ThematicBreak -> BlockIdentityKey(kind = "thematic-break", start = block.range.start, discriminator = block.marker.filterNot(Char::isWhitespace))
        is BlockNode.MathBlock -> BlockIdentityKey(kind = "math-block", start = block.range.start, discriminator = block.delimiter.name)
    }
}
