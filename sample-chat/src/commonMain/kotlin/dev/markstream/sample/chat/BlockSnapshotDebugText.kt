package dev.markstream.sample.chat

import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.InlineNode
import dev.markstream.core.model.MarkdownSnapshot
import dev.markstream.core.model.ParseDelta

internal fun MarkdownSnapshot.toDebugText(): String = buildString {
    appendLine("version=$version final=$isFinal stablePrefixEnd=$stablePrefixEnd dirty=${dirtyRegion.start}..${dirtyRegion.endExclusive}")
    appendLine("lines=${document.lineCount} sourceLength=${document.sourceLength}")
    if (document.blocks.isEmpty()) {
        append("(no blocks)")
        return@buildString
    }
    document.blocks.forEach { block ->
        appendBlock(block = block, depth = 0)
    }
}

internal fun ParseDelta.toDebugText(): String = buildString {
    appendLine("version=$version stateChange=$hasStateChange dirty=${dirtyRegion.start}..${dirtyRegion.endExclusive}")
    appendLine(
        "inserted=${insertedBlockIds.joinToString { it.raw.toString() }} updated=${updatedBlockIds.joinToString { it.raw.toString() }} removed=${removedBlockIds.joinToString { it.raw.toString() }}",
    )
    appendLine(
        "preserved=${stats.preservedBlocks} reparsed=${stats.reparsedBlocks} lines=${stats.processedLines} appended=${stats.appendedChars}",
    )
    append(
        "inlineParsed=${stats.inlineParsedBlockCount} inlineCacheHit=${stats.inlineCacheHitBlockCount} fallback=${stats.fallbackCount}:${stats.fallbackReason ?: "-"}",
    )
}

private fun StringBuilder.appendBlock(block: BlockNode, depth: Int) {
    val indent = "  ".repeat(depth)
    when (block) {
        is BlockNode.BlockQuote -> {
            appendLine("${indent}BlockQuote id=${block.id.raw} range=${block.range.start}..${block.range.endExclusive} lines=${block.lineRange.startLine}..${block.lineRange.endLineExclusive}")
            block.children.forEach { child -> appendBlock(child, depth + 1) }
        }

        is BlockNode.Document -> {
            appendLine("${indent}Document id=${block.id.raw}")
            block.children.forEach { child -> appendBlock(child, depth + 1) }
        }

        is BlockNode.FencedCodeBlock -> appendLine(
            "${indent}FencedCodeBlock id=${block.id.raw} info=${block.infoString ?: "-"} closed=${block.isClosed} literal=${block.literal.debugLiteral()} range=${block.range.start}..${block.range.endExclusive}",
        )

        is BlockNode.Heading -> appendLine(
            "${indent}Heading(level=${block.level}) id=${block.id.raw} text=${block.children.inlineLiteral().debugLiteral()} range=${block.range.start}..${block.range.endExclusive}",
        )

        is BlockNode.ListBlock -> {
            appendLine("${indent}ListBlock(style=${block.style}, loose=${block.isLoose}) id=${block.id.raw} range=${block.range.start}..${block.range.endExclusive}")
            block.items.forEach { item -> appendBlock(item, depth + 1) }
        }

        is BlockNode.ListItem -> {
            appendLine("${indent}ListItem(marker=${block.marker}) id=${block.id.raw} range=${block.range.start}..${block.range.endExclusive}")
            block.children.forEach { child -> appendBlock(child, depth + 1) }
        }

        is BlockNode.Paragraph -> appendLine(
            "${indent}Paragraph id=${block.id.raw} text=${block.children.inlineLiteral().debugLiteral()} range=${block.range.start}..${block.range.endExclusive}",
        )

        is BlockNode.RawTextBlock -> appendLine(
            "${indent}RawTextBlock id=${block.id.raw} literal=${block.literal.debugLiteral()} range=${block.range.start}..${block.range.endExclusive}",
        )

        is BlockNode.ThematicBreak -> appendLine(
            "${indent}ThematicBreak id=${block.id.raw} marker=${block.marker.debugLiteral()} range=${block.range.start}..${block.range.endExclusive}",
        )

        is BlockNode.UnsupportedBlock -> appendLine(
            "${indent}UnsupportedBlock id=${block.id.raw} literal=${block.literal.debugLiteral()} reason=${block.reason ?: "-"} range=${block.range.start}..${block.range.endExclusive}",
        )
    }
}

private fun List<InlineNode>.inlineLiteral(): String = joinToString(separator = "") { node ->
    when (node) {
        is InlineNode.CodeSpan -> node.literal
        is InlineNode.Emphasis -> node.children.inlineLiteral()
        is InlineNode.HardBreak -> "\\n"
        is InlineNode.Link -> node.children.inlineLiteral()
        is InlineNode.SoftBreak -> "\\n"
        is InlineNode.Strikethrough -> node.children.inlineLiteral()
        is InlineNode.Strong -> node.children.inlineLiteral()
        is InlineNode.Text -> node.literal
        is InlineNode.UnsupportedInline -> node.literal
    }
}

private fun String.debugLiteral(): String = '"' + replace("\n", "\\n") + '"'
