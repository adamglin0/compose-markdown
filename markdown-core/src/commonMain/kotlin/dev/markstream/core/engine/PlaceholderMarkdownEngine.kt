package dev.markstream.core.engine

import dev.markstream.core.api.MarkdownEngine
import dev.markstream.core.block.BlockParseResult
import dev.markstream.core.block.BlockParser
import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.inline.InlineParser
import dev.markstream.core.internal.BlockIdentityKey
import dev.markstream.core.internal.CachedBlockRecord
import dev.markstream.core.internal.EngineSessionState
import dev.markstream.core.internal.InlineCacheEntry
import dev.markstream.core.internal.InlineCacheKey
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

        val normalizedChunk = normalizeChunk(chunk)
        if (normalizedChunk.isEmpty()) {
            return noChangeDelta()
        }

        val previousLength = state.source.length
        state.source.append(normalizedChunk)
        state.lineIndex.append(chunk = normalizedChunk, startOffset = previousLength)
        state.isFinal = false
        return rebuild(appendedChunk = normalizedChunk)
    }

    override fun finish(): ParseDelta {
        if (state.isFinal) {
            return noChangeDelta()
        }

        state.isFinal = true
        return rebuild(appendedChunk = null)
    }

    override fun snapshot(): MarkdownSnapshot = state.snapshot

    override fun reset() {
        state.source.clear()
        state.openBlockStack.clear()
        state.cacheState.blockRecords.clear()
        state.cacheState.blockIdsByKey.clear()
        state.cacheState.inlineByBlockId.clear()
        state.lineIndex.reset()
        state.version = 0L
        state.nextBlockId = 1L
        state.stablePrefixEnd = 0
        state.mutableTailStart = 0
        state.isFinal = false
        state.suppressLeadingLineFeed = false
        state.snapshot = emptySnapshot(dialect = dialect)
    }

    private fun rebuild(appendedChunk: String?): ParseDelta {
        state.version += 1

        val previousSnapshot = state.snapshot
        val previousBlocks = previousSnapshot.document.blocks
        val previousRecords = state.cacheState.blockRecords.toList()
        val previousIndexes = previousBlocks.mapIndexed { index, block -> block.id to index }.toMap()
        val previousIds = previousBlocks.flatMap(::flattenBlocks).map { it.id }.toSet()
        val reparsePlan = createReparsePlan(
            previousSnapshot = previousSnapshot,
            previousRecords = previousRecords,
        )
        val preservedBlocks = previousRecords
            .takeWhile { it.block.range.endExclusive <= reparsePlan.dirtyStart }
            .map { it.block }
        val blockIdLookup = previousBlocks
            .flatMap(::flattenBlocks)
            .filter { it.range.endExclusive > reparsePlan.dirtyStart }
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
        val parseRange = TextRange(start = reparsePlan.dirtyStart, endExclusive = state.source.length)
        val parseResult = if (parseRange.isEmpty) {
            BlockParseResult(
                blocks = emptyList(),
                openBlockStack = emptyList(),
                processedLineCount = 0,
            )
        } else {
            parser.parse(
                isFinal = state.isFinal,
                range = parseRange,
            )
        }
        val stablePrefixEnd = stablePrefixEnd(parseResult = parseResult)
        val inlineParser = InlineParser(dialect = dialect)
        val inlineStats = InlineResolutionStats()
        val reparsedBlocks = parseResult.blocks.map { block ->
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

        val blocks = preservedBlocks + reparsedBlocks
        val currentIds = blocks.flatMap(::flattenBlocks).map { it.id }.toSet()
        val removedBlockIds = previousIds.subtract(currentIds).toList()
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
        val insertedBlockIds = changedBlocks.filter { it.oldIndex == null }.map { it.id }
        val updatedBlockIds = changedBlocks.filter { it.oldIndex != null }.map { it.id }
        val stablePrefixRange = TextRange(start = 0, endExclusive = stablePrefixEnd)
        val dirtyRegion = TextRange(start = reparsePlan.dirtyStart, endExclusive = state.source.length)
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

        state.stablePrefixEnd = stablePrefixEnd
        state.mutableTailStart = stablePrefixEnd
        state.cacheState.blockRecords.clear()
        state.cacheState.blockRecords += blocks.map { block ->
            CachedBlockRecord(
                block = block,
                isStable = block.range.endExclusive <= stablePrefixEnd,
            )
        }
        state.cacheState.blockIdsByKey.clear()
        blocks.flatMap(::flattenBlocks).forEach { block ->
            state.cacheState.blockIdsByKey.getOrPut(identityKey(block)) { mutableListOf() } += block.id.raw
        }
        state.snapshot = snapshot

        return ParseDelta(
            version = state.version,
            changedBlocks = changedBlocks,
            insertedBlockIds = insertedBlockIds,
            updatedBlockIds = updatedBlockIds,
            removedBlockIds = removedBlockIds,
            stablePrefixRange = stablePrefixRange,
            dirtyRegion = dirtyRegion,
            snapshot = snapshot,
            stats = ParseStats(
                parsedBlockCount = reparsedBlocks.size,
                changedBlockCount = changedBlocks.size,
                reusedBlockCount = preservedBlocks.size,
                inlineParsedBlockCount = inlineStats.parsedBlockCount,
                inlineCacheHitBlockCount = inlineStats.cacheHitBlockCount,
                appendedChars = appendedChunk?.length ?: 0,
                processedLines = parseResult.processedLineCount,
                reparsedBlocks = reparsedBlocks.size,
                preservedBlocks = preservedBlocks.size,
                fallbackCount = 0,
                fallbackReason = null,
            ),
            hasStateChange = snapshot != previousSnapshot,
        )
    }

    private fun createReparsePlan(
        previousSnapshot: MarkdownSnapshot,
        previousRecords: List<CachedBlockRecord>,
    ): ReparsePlan {
        val previousLength = previousSnapshot.document.sourceLength
        val mutableTailStart = previousSnapshot.stablePrefixEnd
        if (previousLength == 0) {
            return ReparsePlan(dirtyStart = 0)
        }
        if (mutableTailStart >= previousLength) {
            return ReparsePlan(dirtyStart = previousLength)
        }

        val boundary = previousRecords.firstOrNull { record ->
            mutableTailStart >= record.block.range.start && mutableTailStart < record.block.range.endExclusive
        }?.block?.range?.start
        return ReparsePlan(dirtyStart = boundary ?: 0)
    }

    private fun normalizeChunk(chunk: String): String {
        var startIndex = 0
        if (state.suppressLeadingLineFeed && chunk.startsWith('\n')) {
            startIndex = 1
        }
        state.suppressLeadingLineFeed = chunk.endsWith('\r')

        if (startIndex >= chunk.length) {
            return ""
        }

        val slice = chunk.substring(startIndex)
        if (!slice.contains('\r')) {
            return slice
        }

        return buildString(slice.length) {
            var index = 0
            while (index < slice.length) {
                val char = slice[index]
                if (char == '\r') {
                    append('\n')
                    if (index + 1 < slice.length && slice[index + 1] == '\n') {
                        index += 1
                    }
                } else {
                    append(char)
                }
                index += 1
            }
        }
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
        insertedBlockIds = emptyList(),
        updatedBlockIds = emptyList(),
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
        if (earliestOpenOffset != null) {
            return earliestOpenOffset
        }

        val source = state.source.snapshot()
        if (source.isEmpty() || source.endsWith('\n')) {
            return source.length
        }
        val lastNewline = source.lastIndexOf('\n')
        return if (lastNewline >= 0) lastNewline + 1 else 0
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

    private data class ReparsePlan(
        val dirtyStart: Int,
    )
}
