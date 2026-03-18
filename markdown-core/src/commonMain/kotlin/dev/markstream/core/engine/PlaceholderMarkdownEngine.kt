package dev.markstream.core.engine

import dev.markstream.core.block.BlockParseResult
import dev.markstream.core.block.BlockParser
import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.api.MarkdownEngine
import dev.markstream.core.internal.BlockIdentityKey
import dev.markstream.core.internal.EngineSessionState
import dev.markstream.core.internal.InlineCacheEntry
import dev.markstream.core.internal.InlineCacheKey
import dev.markstream.core.inline.InlineParser
import dev.markstream.core.model.BlockChange
import dev.markstream.core.model.BlockId
import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.InlineNode
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
        state.cacheState.inlineByBlockId.clear()
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
        val stablePrefixEnd = maxOf(previousSnapshot.stablePrefixEnd, stablePrefixEnd(parseResult = parseResult))
        state.stablePrefixEnd = stablePrefixEnd
        val inlineParser = InlineParser(dialect = dialect)
        val inlineStats = InlineResolutionStats()
        val blocks = parseResult.blocks.map { block ->
            hydrateInline(
                block = block,
                inlineParser = inlineParser,
                stablePrefixEnd = stablePrefixEnd,
                sourceLength = state.source.length,
                inlineStats = inlineStats,
            )
        }
        state.openBlockStack.clear()
        state.openBlockStack += parseResult.openBlockStack

        val currentIds = blocks.flatMap(::flattenBlocks).map { it.id }.toSet()
        val removedBlockIds = previousIds.subtract(currentIds)
        state.cacheState.inlineByBlockId.keys.retainAll(currentIds.map { it.raw }.toSet())
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
                inlineParsedBlockCount = inlineStats.parsedBlockCount,
                inlineCacheHitBlockCount = inlineStats.cacheHitBlockCount,
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

    private fun hydrateInline(
        block: BlockNode,
        inlineParser: InlineParser,
        stablePrefixEnd: Int,
        sourceLength: Int,
        inlineStats: InlineResolutionStats,
    ): BlockNode = when (block) {
        is BlockNode.Paragraph -> block.copy(
            children = resolveInlineChildren(
                blockId = block.id,
                blockRange = block.range,
                children = block.children,
                inlineParser = inlineParser,
                stablePrefixEnd = stablePrefixEnd,
                sourceLength = sourceLength,
                inlineStats = inlineStats,
            ),
        )

        is BlockNode.Heading -> block.copy(
            children = resolveInlineChildren(
                blockId = block.id,
                blockRange = block.range,
                children = block.children,
                inlineParser = inlineParser,
                stablePrefixEnd = stablePrefixEnd,
                sourceLength = sourceLength,
                inlineStats = inlineStats,
            ),
        )

        is BlockNode.BlockQuote -> block.copy(
            children = block.children.map { child ->
                hydrateInline(
                    block = child,
                    inlineParser = inlineParser,
                    stablePrefixEnd = stablePrefixEnd,
                    sourceLength = sourceLength,
                    inlineStats = inlineStats,
                )
            },
        )

        is BlockNode.ListBlock -> block.copy(
            items = block.items.map { item ->
                hydrateInline(
                    block = item,
                    inlineParser = inlineParser,
                    stablePrefixEnd = stablePrefixEnd,
                    sourceLength = sourceLength,
                    inlineStats = inlineStats,
                ) as BlockNode.ListItem
            },
        )

        is BlockNode.ListItem -> block.copy(
            children = block.children.map { child ->
                hydrateInline(
                    block = child,
                    inlineParser = inlineParser,
                    stablePrefixEnd = stablePrefixEnd,
                    sourceLength = sourceLength,
                    inlineStats = inlineStats,
                )
            },
        )

        is BlockNode.Document,
        is BlockNode.FencedCodeBlock,
        is BlockNode.RawTextBlock,
        is BlockNode.ThematicBreak,
        is BlockNode.UnsupportedBlock,
        -> block
    }

    private fun resolveInlineChildren(
        blockId: BlockId,
        blockRange: TextRange,
        children: List<InlineNode>,
        inlineParser: InlineParser,
        stablePrefixEnd: Int,
        sourceLength: Int,
        inlineStats: InlineResolutionStats,
    ): List<InlineNode> {
        val sourceText = children.singleOrNull() as? InlineNode.Text ?: return children
        val shouldParseNow = shouldParseInline(
            blockRange = blockRange,
            stablePrefixEnd = stablePrefixEnd,
            sourceLength = sourceLength,
        )
        if (!shouldParseNow) {
            return children
        }

        val cacheKey = InlineCacheKey(
            range = sourceText.range,
            literalHash = sourceText.literal.hashCode(),
        )
        val cached = state.cacheState.inlineByBlockId[blockId.raw]
        if (cached?.key == cacheKey) {
            inlineStats.cacheHitBlockCount += 1
            return cached.nodes
        }

        val parsedNodes = inlineParser.parse(
            literal = sourceText.literal,
            range = sourceText.range,
        )
        inlineStats.parsedBlockCount += 1
        state.cacheState.inlineByBlockId[blockId.raw] = InlineCacheEntry(
            key = cacheKey,
            nodes = parsedNodes,
        )
        return parsedNodes
    }

    private fun shouldParseInline(blockRange: TextRange, stablePrefixEnd: Int, sourceLength: Int): Boolean {
        if (blockRange.endExclusive <= stablePrefixEnd) {
            return true
        }
        if (stablePrefixEnd >= sourceLength) {
            return false
        }
        val mutableTail = TextRange(start = stablePrefixEnd, endExclusive = sourceLength)
        return blockRange.intersects(mutableTail)
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

    private data class InlineResolutionStats(
        var parsedBlockCount: Int = 0,
        var cacheHitBlockCount: Int = 0,
    )
}
