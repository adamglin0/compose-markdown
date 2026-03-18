package dev.markstream.core.engine

import dev.markstream.core.block.BlockParseResult
import dev.markstream.core.block.BlockParser
import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.api.MarkdownEngine
import dev.markstream.core.internal.BlockIdentityKey
import dev.markstream.core.internal.EngineSessionState
import dev.markstream.core.model.BlockChange
import dev.markstream.core.model.BlockId
import dev.markstream.core.model.BlockNode
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
        ensureAppendSessionOpen()
        if (chunk.isEmpty()) {
            return noChangeDelta()
        }

        val previousLength = state.source.length
        state.source.append(chunk)
        state.lineIndex.append(chunk = chunk, startOffset = previousLength)
        state.isFinal = false
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
        val previousBlocks = previousSnapshot.document.blocks
        val previousIndexes = previousBlocks.mapIndexed { index, block -> block.id to index }.toMap()
        val previousIds = previousBlocks.flatMap(::flattenBlocks).map { it.id }.toSet()
        val blockIdLookup = previousBlocks
            .flatMap(::flattenBlocks)
            .groupBy(::identityKey)
            .mapValues { (_, blocks) -> blocks.map { it.id.raw }.toMutableList() }

        val parser = BlockParser(
            sourceBuffer = state.source,
            lineIndex = state.lineIndex,
            allocateBlockId = { kind, start, discriminator ->
                allocateBlockId(
                    kind = kind,
                    start = start,
                    discriminator = discriminator,
                    blockIdLookup = blockIdLookup,
                )
            },
        )
        val parseResult = parser.parse(isFinal = state.isFinal)
        val blocks = parseResult.blocks
        state.openBlockStack.clear()
        state.openBlockStack += parseResult.openBlockStack
        state.stablePrefixEnd = maxOf(previousSnapshot.stablePrefixEnd, stablePrefixEnd(parseResult = parseResult))

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

        val stablePrefixRange = TextRange(start = 0, endExclusive = state.stablePrefixEnd)
        val dirtyRegion = if (state.source.isEmpty()) {
            TextRange.Empty
        } else {
            TextRange(start = 0, endExclusive = state.source.length)
        }
        val snapshot = MarkdownSnapshot(
            version = state.version,
            dialect = dialect,
            document = MarkdownDocument(
                sourceLength = state.source.length,
                lineCount = state.lineIndex.lineCount(sourceLength = state.source.length),
                blocks = blocks,
            ),
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
        is BlockNode.Paragraph -> BlockIdentityKey(kind = "paragraph", start = block.range.start, discriminator = "paragraph")
        is BlockNode.RawTextBlock -> BlockIdentityKey(kind = "raw-text", start = block.range.start, discriminator = block.literal.take(16))
        is BlockNode.ThematicBreak -> BlockIdentityKey(kind = "thematic-break", start = block.range.start, discriminator = block.marker.filterNot(Char::isWhitespace))
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

    private fun ensureAppendSessionOpen() {
        if (state.isFinal) {
            error("Cannot call append() after finish(); call reset() or create a new MarkdownEngine.")
        }
    }

    private fun stablePrefixEnd(parseResult: BlockParseResult): Int {
        if (state.isFinal) {
            return state.source.length
        }
        val earliestOpenOffset = parseResult.openBlockStack.firstOrNull()?.startOffset
        return earliestOpenOffset ?: state.source.length
    }

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
