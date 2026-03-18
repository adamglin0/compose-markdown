package dev.markstream.core.engine

import dev.markstream.core.api.MarkdownEngine
import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.internal.BlockIdentityKey
import dev.markstream.core.internal.EngineSessionState
import dev.markstream.core.model.BlockChange
import dev.markstream.core.model.BlockId
import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.InlineId
import dev.markstream.core.model.InlineNode
import dev.markstream.core.model.LineRange
import dev.markstream.core.model.ListStyle
import dev.markstream.core.model.MarkdownDocument
import dev.markstream.core.model.MarkdownSnapshot
import dev.markstream.core.model.ParseDelta
import dev.markstream.core.model.ParseStats
import dev.markstream.core.model.TextRange

internal class PlaceholderMarkdownEngine(
    override val dialect: MarkdownDialect,
) : MarkdownEngine {
    private val state = EngineSessionState(
        dialect = dialect,
        snapshot = emptySnapshot(dialect = dialect),
    )

    override fun append(chunk: String): ParseDelta {
        if (chunk.isEmpty()) {
            return noChangeDelta()
        }

        val previousLength = state.source.length
        state.source.append(chunk)
        state.lineIndex.append(chunk = chunk, startOffset = previousLength)
        state.isFinal = false
        state.stablePrefixEnd = minOf(state.stablePrefixEnd, previousLength)

        return rebuild()
    }

    override fun finish(): ParseDelta {
        if (state.isFinal) {
            return noChangeDelta()
        }

        state.isFinal = true
        state.stablePrefixEnd = state.source.length

        return rebuild()
    }

    override fun snapshot(): MarkdownSnapshot = state.snapshot

    override fun reset() {
        state.source.clear()
        state.openBlockStack.clear()
        state.cacheState.blockIdsByKey.clear()
        state.lineIndex.reset()
        state.version = 0L
        state.nextBlockId = 1L
        state.stablePrefixEnd = 0
        state.isFinal = false
        state.snapshot = emptySnapshot(dialect = dialect)
    }

    private fun rebuild(): ParseDelta {
        state.version += 1

        val previousSnapshot = state.snapshot
        val previousBlocks = state.snapshot.document.blocks
        val previousIndexes = previousBlocks.mapIndexed { index, block -> block.id to index }.toMap()
        val previousIds = previousBlocks.map { it.id }.toSet()
        val blockIdLookup = previousBlocks
            .flatMap(::flattenBlocks)
            .groupBy(::identityKey)
            .mapValues { (_, blocks) -> blocks.map { it.id.raw }.toMutableList() }

        val blocks = parseTopLevelBlocks(
            source = state.source.toString(),
            blockIdLookup = blockIdLookup,
        )
        val currentIds = blocks.flatMap(::flattenBlocks).map { it.id }.toSet()
        val removedBlockIds = previousIds.subtract(currentIds)

        val changedBlocks = blocks.mapIndexedNotNull { index, block ->
            val oldIndex = previousIndexes[block.id]
            val oldBlock = oldIndex?.let(previousBlocks::get)
            if (oldBlock == block && oldIndex == index) {
                null
            } else {
                BlockChange(
                    id = block.id,
                    oldIndex = oldIndex,
                    newIndex = index,
                    block = block,
                )
            }
        }

        val lineCount = state.lineIndex.lineCount(sourceLength = state.source.length)
        val stablePrefixRange = TextRange(start = 0, endExclusive = state.stablePrefixEnd)
        val dirtyRegion = dirtyRegionForCurrentRebuild()
        val document = MarkdownDocument(
            sourceLength = state.source.length,
            lineCount = lineCount,
            blocks = blocks,
        )
        val snapshot = MarkdownSnapshot(
            version = state.version,
            dialect = dialect,
            document = document,
            stablePrefixRange = stablePrefixRange,
            dirtyRegion = dirtyRegion,
            isFinal = state.isFinal,
        )

        state.cacheState.blockIdsByKey.clear()
        blocks.flatMap(::flattenBlocks).forEach { block ->
            state.cacheState.blockIdsByKey.getOrPut(identityKey(block)) { mutableListOf() } += block.id.raw
        }
        state.snapshot = snapshot

        return ParseDelta(
            version = state.version,
            changedBlocks = changedBlocks,
            removedBlockIds = removedBlockIds.toList(),
            stablePrefixRange = stablePrefixRange,
            dirtyRegion = dirtyRegion,
            snapshot = snapshot,
            stats = ParseStats(
                parsedBlockCount = blocks.size,
                changedBlockCount = changedBlocks.size,
                reusedBlockCount = blocks.size - changedBlocks.size,
            ),
            hasStateChange = snapshot != previousSnapshot,
        )
    }

    private fun dirtyRegionForCurrentRebuild(): TextRange {
        if (state.source.isEmpty()) {
            return TextRange.Empty
        }

        return TextRange(start = 0, endExclusive = state.source.length)
    }

    private fun parseTopLevelBlocks(
        source: String,
        blockIdLookup: Map<BlockIdentityKey, MutableList<Long>>,
    ): List<BlockNode> {
        if (source.isEmpty()) {
            return emptyList()
        }

        val trimmed = source.trimEnd('\n')
        val fullRange = TextRange(start = 0, endExclusive = source.length)
        val fullLineRange = state.lineIndex.lineRangeOf(range = fullRange, sourceLength = source.length)

        if (source.lines().all(::isListItemLine)) {
            return listOf(buildListBlock(source = source, blockIdLookup = blockIdLookup))
        }

        if (source.lines().all(::isBlockQuoteLine)) {
            return listOf(buildBlockQuote(source = source, blockIdLookup = blockIdLookup))
        }

        if (isFencedCodeBlock(source = trimmed)) {
            val infoString = trimmed.lineSequence().first().removePrefix("```").trim().ifEmpty { null }
            return listOf(
                BlockNode.FencedCodeBlock(
                    id = allocateBlockId(
                        kind = "fenced-code",
                        start = 0,
                        discriminator = infoString.orEmpty(),
                        blockIdLookup = blockIdLookup,
                    ),
                    range = fullRange,
                    lineRange = fullLineRange,
                    infoString = infoString,
                    literal = source,
                    isClosed = trimmed.lines().lastOrNull()?.startsWith("```") == true,
                ),
            )
        }

        if (isThematicBreak(trimmed)) {
            return listOf(
                BlockNode.ThematicBreak(
                    id = allocateBlockId(
                        kind = "thematic-break",
                        start = 0,
                        discriminator = trimmed,
                        blockIdLookup = blockIdLookup,
                    ),
                    range = fullRange,
                    lineRange = fullLineRange,
                    marker = trimmed,
                ),
            )
        }

        if (!trimmed.contains('\n') && isHeading(trimmed)) {
            val markerLength = trimmed.takeWhile { it == '#' }.length
            val contentStart = minOf(markerLength + 1, trimmed.length)
            val inlineRange = TextRange(start = contentStart, endExclusive = trimmed.length)
            return listOf(
                BlockNode.Heading(
                    id = allocateBlockId(
                        kind = "heading",
                        start = 0,
                        discriminator = markerLength.toString(),
                        blockIdLookup = blockIdLookup,
                    ),
                    range = fullRange,
                    lineRange = fullLineRange,
                    level = markerLength,
                    children = parseInlineNodes(
                        source = source,
                        range = inlineRange,
                    ),
                ),
            )
        }

        if (!source.contains("\n\n") && source.lineSequence().none(::looksStructured)) {
            return listOf(
                BlockNode.Paragraph(
                    id = allocateBlockId(
                        kind = "paragraph",
                        start = 0,
                        discriminator = "single-paragraph",
                        blockIdLookup = blockIdLookup,
                    ),
                    range = fullRange,
                    lineRange = fullLineRange,
                    children = parseInlineNodes(
                        source = source,
                        range = fullRange,
                    ),
                ),
            )
        }

        return listOf(
            BlockNode.RawTextBlock(
                id = allocateBlockId(
                    kind = "raw-text",
                    start = 0,
                    discriminator = "full-document",
                    blockIdLookup = blockIdLookup,
                ),
                range = fullRange,
                lineRange = fullLineRange,
                literal = source,
            ),
        )
    }

    private fun buildListBlock(
        source: String,
        blockIdLookup: Map<BlockIdentityKey, MutableList<Long>>,
    ): BlockNode.ListBlock {
        val lines = source.lines()
        var offset = 0
        val items = lines.map { line ->
            val marker = line.substringBefore(' ')
            val itemRange = TextRange(start = offset, endExclusive = offset + line.length)
            val itemLineRange = state.lineIndex.lineRangeOf(range = itemRange, sourceLength = source.length)
            val paragraph = BlockNode.Paragraph(
                id = allocateBlockId(
                    kind = "paragraph",
                    start = offset,
                    discriminator = "single-paragraph",
                    blockIdLookup = blockIdLookup,
                ),
                range = itemRange,
                lineRange = itemLineRange,
                children = parseInlineNodes(source = source, range = itemRange),
            )
            val item = BlockNode.ListItem(
                id = allocateBlockId(
                    kind = "list-item",
                    start = offset,
                    discriminator = marker,
                    blockIdLookup = blockIdLookup,
                ),
                range = itemRange,
                lineRange = itemLineRange,
                marker = marker,
                children = listOf(paragraph),
            )
            offset += line.length + 1
            item
        }

        val style = if (lines.first().first().isDigit()) ListStyle.Ordered else ListStyle.Unordered
        val range = TextRange(start = 0, endExclusive = source.length)
        return BlockNode.ListBlock(
            id = allocateBlockId(
                kind = "list-block",
                start = 0,
                discriminator = style.name,
                blockIdLookup = blockIdLookup,
            ),
            range = range,
            lineRange = state.lineIndex.lineRangeOf(range = range, sourceLength = source.length),
            style = style,
            items = items,
            isLoose = false,
        )
    }

    private fun buildBlockQuote(
        source: String,
        blockIdLookup: Map<BlockIdentityKey, MutableList<Long>>,
    ): BlockNode.BlockQuote {
        val range = TextRange(start = 0, endExclusive = source.length)
        val paragraph = BlockNode.RawTextBlock(
            id = allocateBlockId(
                kind = "raw-text",
                start = 0,
                discriminator = "full-document",
                blockIdLookup = blockIdLookup,
            ),
            range = range,
            lineRange = state.lineIndex.lineRangeOf(range = range, sourceLength = source.length),
            literal = source.lineSequence().joinToString("\n") { it.removePrefix("> ").removePrefix(">") },
        )

        return BlockNode.BlockQuote(
            id = allocateBlockId(
                kind = "blockquote",
                start = 0,
                discriminator = "container",
                blockIdLookup = blockIdLookup,
            ),
            range = range,
            lineRange = state.lineIndex.lineRangeOf(range = range, sourceLength = source.length),
            children = listOf(paragraph),
        )
    }

    private fun parseInlineNodes(
        source: String,
        range: TextRange,
    ): List<InlineNode> {
        if (range.isEmpty) {
            return emptyList()
        }

        val content = source.substring(range.start, range.endExclusive)
        if (!content.contains('\n')) {
            return listOf(
                InlineNode.Text(
                    id = inlineId(range = range, salt = 1L),
                    range = range,
                    literal = content,
                ),
            )
        }

        val nodes = mutableListOf<InlineNode>()
        var segmentStart = range.start
        content.forEachIndexed { index, char ->
            val absoluteOffset = range.start + index
            if (char == '\n') {
                if (segmentStart < absoluteOffset) {
                    nodes += InlineNode.Text(
                        id = inlineId(
                            range = TextRange(start = segmentStart, endExclusive = absoluteOffset),
                            salt = 1L,
                        ),
                        range = TextRange(start = segmentStart, endExclusive = absoluteOffset),
                        literal = source.substring(segmentStart, absoluteOffset),
                    )
                }
                nodes += InlineNode.SoftBreak(
                    id = inlineId(
                        range = TextRange(start = absoluteOffset, endExclusive = absoluteOffset + 1),
                        salt = 2L,
                    ),
                    range = TextRange(start = absoluteOffset, endExclusive = absoluteOffset + 1),
                )
                segmentStart = absoluteOffset + 1
            }
        }

        if (segmentStart < range.endExclusive) {
            nodes += InlineNode.Text(
                id = inlineId(
                    range = TextRange(start = segmentStart, endExclusive = range.endExclusive),
                    salt = 1L,
                ),
                range = TextRange(start = segmentStart, endExclusive = range.endExclusive),
                literal = source.substring(segmentStart, range.endExclusive),
            )
        }

        return nodes
    }

    private fun flattenBlocks(block: BlockNode): List<BlockNode> = buildList {
        add(block)
        when (block) {
            is BlockNode.BlockQuote -> block.children.forEach { addAll(flattenBlocks(it)) }
            is BlockNode.Document -> block.children.forEach { addAll(flattenBlocks(it)) }
            is BlockNode.ListBlock -> block.items.forEach { addAll(flattenBlocks(it)) }
            is BlockNode.ListItem -> block.children.forEach { addAll(flattenBlocks(it)) }
            is BlockNode.FencedCodeBlock,
            is BlockNode.Heading,
            is BlockNode.Paragraph,
            is BlockNode.RawTextBlock,
            is BlockNode.ThematicBreak,
            is BlockNode.UnsupportedBlock,
            -> Unit
        }
    }

    private fun identityKey(block: BlockNode): BlockIdentityKey = when (block) {
        is BlockNode.BlockQuote -> BlockIdentityKey(kind = "blockquote", start = block.range.start, discriminator = "container")
        is BlockNode.Document -> BlockIdentityKey(kind = "document", start = block.range.start, discriminator = "root")
        is BlockNode.FencedCodeBlock -> BlockIdentityKey(kind = "fenced-code", start = block.range.start, discriminator = block.infoString.orEmpty())
        is BlockNode.Heading -> BlockIdentityKey(kind = "heading", start = block.range.start, discriminator = block.level.toString())
        is BlockNode.ListBlock -> BlockIdentityKey(kind = "list-block", start = block.range.start, discriminator = block.style.name)
        is BlockNode.ListItem -> BlockIdentityKey(kind = "list-item", start = block.range.start, discriminator = block.marker)
        is BlockNode.Paragraph -> BlockIdentityKey(kind = "paragraph", start = block.range.start, discriminator = "single-paragraph")
        is BlockNode.RawTextBlock -> BlockIdentityKey(kind = "raw-text", start = block.range.start, discriminator = "full-document")
        is BlockNode.ThematicBreak -> BlockIdentityKey(kind = "thematic-break", start = block.range.start, discriminator = block.marker)
        is BlockNode.UnsupportedBlock -> BlockIdentityKey(kind = "unsupported", start = block.range.start, discriminator = block.reason.orEmpty())
    }

    private fun allocateBlockId(
        kind: String,
        start: Int,
        discriminator: String,
        blockIdLookup: Map<BlockIdentityKey, MutableList<Long>>,
    ): BlockId {
        val key = BlockIdentityKey(kind = kind, start = start, discriminator = discriminator)
        val reused = blockIdLookup[key]?.firstOrNull()
        if (reused != null) {
            blockIdLookup[key]?.removeAt(0)
            return BlockId(raw = reused)
        }

        val fresh = state.nextBlockId
        state.nextBlockId += 1L
        return BlockId(raw = fresh)
    }

    private fun inlineId(range: TextRange, salt: Long): InlineId {
        val raw = (range.start.toLong() shl 32) xor range.endExclusive.toLong() xor salt
        return InlineId(raw = raw and Long.MAX_VALUE)
    }

    private fun isHeading(line: String): Boolean {
        val markerLength = line.takeWhile { it == '#' }.length
        return markerLength in 1..6 && line.length > markerLength && line[markerLength] == ' '
    }

    private fun isThematicBreak(line: String): Boolean {
        val compact = line.filterNot(Char::isWhitespace)
        return compact.length >= 3 && compact.all { it == '-' || it == '*' || it == '_' }
    }

    private fun isFencedCodeBlock(source: String): Boolean {
        val lines = source.lines()
        if (lines.isEmpty()) {
            return false
        }

        return lines.first().startsWith("```")
    }

    private fun isListItemLine(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.matches(Regex("\\d+\\. .+"))
    }

    private fun isBlockQuoteLine(line: String): Boolean = line.trimStart().startsWith(">")

    private fun looksStructured(line: String): Boolean =
        isHeading(line) || isListItemLine(line) || isBlockQuoteLine(line) || line.startsWith("```") || isThematicBreak(line)

    private fun noChangeDelta(): ParseDelta = ParseDelta(
        version = state.snapshot.version,
        changedBlocks = emptyList(),
        removedBlockIds = emptyList(),
        stablePrefixRange = state.snapshot.stablePrefixRange,
        dirtyRegion = TextRange(start = state.source.length, endExclusive = state.source.length),
        snapshot = state.snapshot,
        stats = ParseStats.Empty,
        hasStateChange = false,
    )

    private fun emptySnapshot(dialect: MarkdownDialect): MarkdownSnapshot = MarkdownSnapshot(
        version = 0L,
        dialect = dialect,
        document = MarkdownDocument(
            sourceLength = 0,
            lineCount = 0,
            blocks = emptyList(),
        ),
        stablePrefixRange = TextRange.Empty,
        dirtyRegion = TextRange.Empty,
        isFinal = false,
    )
}
