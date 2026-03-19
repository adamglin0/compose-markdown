package com.adamglin.compose.markdown.core.internal

import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.HeadingStyle
import com.adamglin.compose.markdown.core.model.TableAlignment

internal object BlockIdentity {
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
            is BlockNode.Heading,
            is BlockNode.Paragraph,
            is BlockNode.RawTextBlock,
            is BlockNode.ThematicBreak,
            is BlockNode.UnsupportedBlock,
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
        is BlockNode.ListBlock -> BlockIdentityKey(kind = "list-block", start = block.range.start, discriminator = block.style.name)
        is BlockNode.ListItem -> BlockIdentityKey(kind = "list-item", start = block.range.start, discriminator = block.marker)
        is BlockNode.Paragraph -> BlockIdentityKey(kind = "paragraph", start = block.range.start, discriminator = "paragraph")
        is BlockNode.RawTextBlock -> BlockIdentityKey(kind = "raw-text", start = block.range.start, discriminator = block.literal.take(16))
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
        is BlockNode.UnsupportedBlock -> BlockIdentityKey(kind = "unsupported", start = block.range.start, discriminator = block.reason.orEmpty())
    }
}
