package dev.markstream.core.engine

import dev.markstream.core.api.MarkdownEngine
import dev.markstream.core.block.BlockParseResult
import dev.markstream.core.block.BlockParser
import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.inline.InlineParseResult
import dev.markstream.core.inline.InlineParser
import dev.markstream.core.internal.BlockIdentityKey
import dev.markstream.core.internal.BlockIdentity
import dev.markstream.core.internal.CachedBlockRecord
import dev.markstream.core.internal.EngineSessionState
import dev.markstream.core.internal.InlineCacheEntry
import dev.markstream.core.internal.InlineCacheKey
import dev.markstream.core.internal.LinkReferenceDefinition
import dev.markstream.core.model.BlockChange
import dev.markstream.core.model.BlockId
import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.InlineNode
import dev.markstream.core.model.MarkdownDocument
import dev.markstream.core.model.MarkdownSnapshot
import dev.markstream.core.model.ParseDelta
import dev.markstream.core.model.ParseStats
import dev.markstream.core.model.TextRange

internal class IncrementalMarkdownEngine(
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
        state.dependencyIndex.reset()
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
        val previousFlatBlocks = flattenBlocks(previousBlocks)
        val previousIds = previousFlatBlocks.mapTo(linkedSetOf()) { it.id }
        val previousDefinitions = state.dependencyIndex.definitionsByLabel.toMap()
        val previousUnresolvedByLabel = state.dependencyIndex.unresolvedBlocksByLabel.mapValues { it.value.toSet() }
        val reparsePlan = createReparsePlan(
            previousSnapshot = previousSnapshot,
            previousRecords = previousRecords,
        )
        val preservedRecords = previousRecords
            .takeWhile { it.block.range.endExclusive <= reparsePlan.dirtyStart }
        val blockIdLookup = previousFlatBlocks
            .filter { it.range.endExclusive > reparsePlan.dirtyStart }
            .let(BlockIdentity::blockIdLookup)
        val parser = BlockParser(
            sourceBuffer = state.source,
            lineIndex = state.lineIndex,
            dialect = dialect,
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
                parsedDefinitions = emptyMap(),
            )
        } else {
            parser.parse(
                isFinal = state.isFinal,
                range = parseRange,
            )
        }
        val stablePrefixEnd = stablePrefixEnd(parseResult = parseResult)
        val effectiveDefinitions = linkedMapOf<String, LinkReferenceDefinition>().apply {
            putAll(previousDefinitions)
            parseResult.parsedDefinitions.forEach { (label, definition) -> putIfAbsent(label, definition) }
        }
        val inlineParser = InlineParser(
            dialect = dialect,
            referenceDefinitions = effectiveDefinitions,
        )
        val referenceRevision = effectiveDefinitions.hashCode()
        val inlineStats = InlineResolutionStats()
        val reparsedTopLevelBlocks = parseResult.blocks.map { block ->
            hydrateTopLevelBlock(
                block = block,
                inlineParser = inlineParser,
                referenceRevision = referenceRevision,
                stablePrefixEnd = stablePrefixEnd,
                sourceLength = state.source.length,
                inlineStats = inlineStats,
            )
        }
        val newDefinitionLabels = parseResult.parsedDefinitions.keys.filterNot(previousDefinitions::containsKey).toSet()
        val affectedPreservedBlockIds = newDefinitionLabels
            .flatMap { label -> previousUnresolvedByLabel[label].orEmpty() }
            .toSet()
        val rehydratedPreservedBlocks = preservedRecords.map { record ->
            if (record.block.id.raw in affectedPreservedBlockIds) {
                reparseSingleBlock(
                    block = record.block,
                    inlineParser = inlineParser,
                    referenceRevision = referenceRevision,
                    stablePrefixEnd = stablePrefixEnd,
                    sourceLength = state.source.length,
                    inlineStats = inlineStats,
                    blockIdLookup = blockIdLookup,
                )
            } else {
                HydratedTopLevelBlock(
                    block = record.block,
                    unresolvedReferenceLabels = record.unresolvedReferenceLabels,
                )
            }
        }

        state.openBlockStack.clear()
        state.openBlockStack += parseResult.openBlockStack

        val hydratedBlocks = rehydratedPreservedBlocks + reparsedTopLevelBlocks
        val blocks = hydratedBlocks.map { it.block }
        val currentFlatBlocks = flattenBlocks(blocks)
        val currentIds = currentFlatBlocks.mapTo(linkedSetOf()) { it.id }
        val currentRawIds = currentIds.mapTo(linkedSetOf()) { it.raw }
        val removedBlockIds = previousIds.subtract(currentIds).toList()
        state.cacheState.inlineByBlockId.keys.retainAll(currentRawIds)
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
        state.cacheState.blockRecords += hydratedBlocks.map { hydrated ->
            CachedBlockRecord(
                block = hydrated.block,
                isStable = hydrated.block.range.endExclusive <= stablePrefixEnd,
                unresolvedReferenceLabels = hydrated.unresolvedReferenceLabels,
            )
        }
        state.cacheState.blockIdsByKey.clear()
        state.cacheState.blockIdsByKey.putAll(BlockIdentity.blockIdLookup(currentFlatBlocks))
        state.dependencyIndex.reset()
        effectiveDefinitions.forEach { (label, definition) ->
            state.dependencyIndex.definitionsByLabel[label] = definition
        }
        hydratedBlocks.forEach { hydrated ->
            if (hydrated.unresolvedReferenceLabels.isNotEmpty()) {
                state.dependencyIndex.unresolvedLabelsByBlockId[hydrated.block.id.raw] = hydrated.unresolvedReferenceLabels
                hydrated.unresolvedReferenceLabels.forEach { label ->
                    state.dependencyIndex.unresolvedBlocksByLabel.getOrPut(label) { linkedSetOf() } += hydrated.block.id.raw
                }
            }
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
                parsedBlockCount = reparsedTopLevelBlocks.size + affectedPreservedBlockIds.size,
                changedBlockCount = changedBlocks.size,
                reusedBlockCount = (preservedRecords.size - affectedPreservedBlockIds.size).coerceAtLeast(0),
                inlineParsedBlockCount = inlineStats.parsedBlockCount,
                inlineCacheHitBlockCount = inlineStats.cacheHitBlockCount,
                appendedChars = appendedChunk?.length ?: 0,
                processedLines = parseResult.processedLineCount,
                reparsedBlocks = reparsedTopLevelBlocks.size + affectedPreservedBlockIds.size,
                preservedBlocks = (preservedRecords.size - affectedPreservedBlockIds.size).coerceAtLeast(0),
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
            val retroactiveStart = if (canRetroactivelyExtendPreviousBlock(previousLength)) {
                previousRecords.lastOrNull()?.block?.takeIf(::shouldBackUpForRetroactiveInterpretation)?.range?.start
            } else {
                null
            }
            return ReparsePlan(dirtyStart = retroactiveStart ?: previousLength)
        }

        val boundary = previousRecords.firstOrNull { record ->
            mutableTailStart >= record.block.range.start && mutableTailStart < record.block.range.endExclusive
        }?.block?.range?.start
        return ReparsePlan(dirtyStart = boundary ?: 0)
    }

    private fun shouldBackUpForRetroactiveInterpretation(block: BlockNode): Boolean = when (block) {
        is BlockNode.Paragraph -> dialect.blockFeatures.setextHeadings || dialect.blockFeatures.tables
        is BlockNode.BlockQuote,
        is BlockNode.ListBlock,
        -> true
        else -> false
    }

    private fun canRetroactivelyExtendPreviousBlock(previousLength: Int): Boolean {
        if (previousLength <= 0) {
            return false
        }
        if (previousLength > state.source.length || state.source[previousLength - 1] != '\n') {
            return false
        }
        return previousLength == 1 || state.source[previousLength - 2] != '\n'
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

        var containsCarriageReturn = false
        for (index in startIndex until chunk.length) {
            if (chunk[index] == '\r') {
                containsCarriageReturn = true
                break
            }
        }
        if (!containsCarriageReturn) {
            return if (startIndex == 0) chunk else chunk.substring(startIndex)
        }

        return buildString(chunk.length - startIndex) {
            var index = startIndex
            while (index < chunk.length) {
                val char = chunk[index]
                if (char == '\r') {
                    append('\n')
                    if (index + 1 < chunk.length && chunk[index + 1] == '\n') {
                        index += 1
                    }
                } else {
                    append(char)
                }
                index += 1
            }
        }
    }

    private fun flattenBlocks(blocks: List<BlockNode>): List<BlockNode> = buildList {
        blocks.forEach { block -> collectFlattenedBlocks(block, this) }
    }

    private fun flattenBlocks(block: BlockNode): List<BlockNode> = buildList {
        collectFlattenedBlocks(block, this)
    }

    private fun collectFlattenedBlocks(block: BlockNode, destination: MutableList<BlockNode>) {
        destination += block
        when (block) {
            is BlockNode.BlockQuote -> block.children.forEach { child -> collectFlattenedBlocks(child, destination) }
            is BlockNode.Document -> block.children.forEach { child -> collectFlattenedBlocks(child, destination) }
            is BlockNode.ListBlock -> block.items.forEach { item -> collectFlattenedBlocks(item, destination) }
            is BlockNode.ListItem -> block.children.forEach { child -> collectFlattenedBlocks(child, destination) }
            is BlockNode.TableBlock -> {
                collectFlattenedBlocks(block.header, destination)
                block.rows.forEach { row -> collectFlattenedBlocks(row, destination) }
            }
            is BlockNode.TableRow -> block.cells.forEach { cell -> collectFlattenedBlocks(cell, destination) }
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

    private fun hydrateTopLevelBlock(
        block: BlockNode,
        inlineParser: InlineParser,
        referenceRevision: Int,
        stablePrefixEnd: Int,
        sourceLength: Int,
        inlineStats: InlineResolutionStats,
    ): HydratedTopLevelBlock {
        val hydration = hydrateInline(
            block = block,
            topLevelBlockId = block.id,
            inlineParser = inlineParser,
            referenceRevision = referenceRevision,
            stablePrefixEnd = stablePrefixEnd,
            sourceLength = sourceLength,
            inlineStats = inlineStats,
        )
        return HydratedTopLevelBlock(
            block = hydration.block,
            unresolvedReferenceLabels = hydration.unresolvedReferenceLabels,
        )
    }

    private fun reparseSingleBlock(
        block: BlockNode,
        inlineParser: InlineParser,
        referenceRevision: Int,
        stablePrefixEnd: Int,
        sourceLength: Int,
        inlineStats: InlineResolutionStats,
        blockIdLookup: Map<BlockIdentityKey, MutableList<Long>>,
    ): HydratedTopLevelBlock {
        val preservedBlockIdLookup = flattenBlocks(block)
            .let(BlockIdentity::blockIdLookup)
        val parser = BlockParser(
            sourceBuffer = state.source,
            lineIndex = state.lineIndex,
            dialect = dialect,
            allocateBlockId = { kind, start, discriminator ->
                allocateBlockId(
                    kind = kind,
                    start = start,
                    discriminator = discriminator,
                    blockIdLookup = preservedBlockIdLookup,
                )
            },
        )
        val result = parser.parse(
            isFinal = true,
            range = block.range,
        )
        val reparsed = result.blocks.singleOrNull() ?: block
        return hydrateTopLevelBlock(
            block = reparsed,
            inlineParser = inlineParser,
            referenceRevision = referenceRevision,
            stablePrefixEnd = stablePrefixEnd,
            sourceLength = sourceLength,
            inlineStats = inlineStats,
        )
    }

    private fun hydrateInline(
        block: BlockNode,
        topLevelBlockId: BlockId,
        inlineParser: InlineParser,
        referenceRevision: Int,
        stablePrefixEnd: Int,
        sourceLength: Int,
        inlineStats: InlineResolutionStats,
    ): InlineHydration = when (block) {
        is BlockNode.Paragraph -> resolveInlineChildren(
            blockId = block.id,
            topLevelBlockId = topLevelBlockId,
            blockRange = block.range,
            children = block.children,
            inlineParser = inlineParser,
            referenceRevision = referenceRevision,
            stablePrefixEnd = stablePrefixEnd,
            sourceLength = sourceLength,
            inlineStats = inlineStats,
        ).let { resolved ->
            InlineHydration(
                block = block.copy(children = resolved.nodes),
                unresolvedReferenceLabels = resolved.unresolvedReferenceLabels,
            )
        }

        is BlockNode.Heading -> resolveInlineChildren(
            blockId = block.id,
            topLevelBlockId = topLevelBlockId,
            blockRange = block.range,
            children = block.children,
            inlineParser = inlineParser,
            referenceRevision = referenceRevision,
            stablePrefixEnd = stablePrefixEnd,
            sourceLength = sourceLength,
            inlineStats = inlineStats,
        ).let { resolved ->
            InlineHydration(
                block = block.copy(children = resolved.nodes),
                unresolvedReferenceLabels = resolved.unresolvedReferenceLabels,
            )
        }

        is BlockNode.TableCell -> resolveInlineChildren(
            blockId = block.id,
            topLevelBlockId = topLevelBlockId,
            blockRange = block.range,
            children = block.children,
            inlineParser = inlineParser,
            referenceRevision = referenceRevision,
            stablePrefixEnd = stablePrefixEnd,
            sourceLength = sourceLength,
            inlineStats = inlineStats,
        ).let { resolved ->
            InlineHydration(
                block = block.copy(children = resolved.nodes),
                unresolvedReferenceLabels = resolved.unresolvedReferenceLabels,
            )
        }

        is BlockNode.BlockQuote -> {
            val children = block.children.map {
                hydrateInline(it, topLevelBlockId, inlineParser, referenceRevision, stablePrefixEnd, sourceLength, inlineStats)
            }
            InlineHydration(
                block = block.copy(children = children.map { it.block }),
                unresolvedReferenceLabels = children.flatMapTo(linkedSetOf()) { it.unresolvedReferenceLabels },
            )
        }

        is BlockNode.ListBlock -> {
            val items = block.items.map {
                hydrateInline(it, topLevelBlockId, inlineParser, referenceRevision, stablePrefixEnd, sourceLength, inlineStats)
            }
            InlineHydration(
                block = block.copy(items = items.map { it.block as BlockNode.ListItem }),
                unresolvedReferenceLabels = items.flatMapTo(linkedSetOf()) { it.unresolvedReferenceLabels },
            )
        }

        is BlockNode.ListItem -> {
            val children = block.children.map {
                hydrateInline(it, topLevelBlockId, inlineParser, referenceRevision, stablePrefixEnd, sourceLength, inlineStats)
            }
            InlineHydration(
                block = block.copy(children = children.map { it.block }),
                unresolvedReferenceLabels = children.flatMapTo(linkedSetOf()) { it.unresolvedReferenceLabels },
            )
        }

        is BlockNode.TableBlock -> {
            val header = hydrateInline(block.header, topLevelBlockId, inlineParser, referenceRevision, stablePrefixEnd, sourceLength, inlineStats)
            val rows = block.rows.map {
                hydrateInline(it, topLevelBlockId, inlineParser, referenceRevision, stablePrefixEnd, sourceLength, inlineStats)
            }
            InlineHydration(
                block = block.copy(
                    header = header.block as BlockNode.TableRow,
                    rows = rows.map { it.block as BlockNode.TableRow },
                ),
                unresolvedReferenceLabels = buildSet {
                    addAll(header.unresolvedReferenceLabels)
                    rows.forEach { addAll(it.unresolvedReferenceLabels) }
                },
            )
        }

        is BlockNode.TableRow -> {
            val cells = block.cells.map {
                hydrateInline(it, topLevelBlockId, inlineParser, referenceRevision, stablePrefixEnd, sourceLength, inlineStats)
            }
            InlineHydration(
                block = block.copy(cells = cells.map { it.block as BlockNode.TableCell }),
                unresolvedReferenceLabels = cells.flatMapTo(linkedSetOf()) { it.unresolvedReferenceLabels },
            )
        }

        is BlockNode.Document,
        is BlockNode.FencedCodeBlock,
        is BlockNode.RawTextBlock,
        is BlockNode.ThematicBreak,
        is BlockNode.UnsupportedBlock,
        -> InlineHydration(block = block, unresolvedReferenceLabels = emptySet())
    }

    private fun resolveInlineChildren(
        blockId: BlockId,
        topLevelBlockId: BlockId,
        blockRange: TextRange,
        children: List<InlineNode>,
        inlineParser: InlineParser,
        referenceRevision: Int,
        stablePrefixEnd: Int,
        sourceLength: Int,
        inlineStats: InlineResolutionStats,
    ): ResolvedInline {
        val sourceText = children.singleOrNull() as? InlineNode.Text ?: return ResolvedInline(children, emptySet())
        val shouldParseNow = shouldParseInline(
            blockRange = blockRange,
            stablePrefixEnd = stablePrefixEnd,
            sourceLength = sourceLength,
        )
        if (!shouldParseNow) {
            return ResolvedInline(children, emptySet())
        }

        val cacheKey = InlineCacheKey(
            range = sourceText.range,
            literalHash = sourceText.literal.hashCode(),
            referenceRevision = referenceRevision,
        )
        val cached = state.cacheState.inlineByBlockId[blockId.raw]
        if (cached?.key == cacheKey) {
            inlineStats.cacheHitBlockCount += 1
            return ResolvedInline(cached.nodes, cached.unresolvedReferenceLabels)
        }

        val parsed = inlineParser.parse(
            literal = sourceText.literal,
            range = sourceText.range,
        )
        inlineStats.parsedBlockCount += 1
        state.cacheState.inlineByBlockId[blockId.raw] = InlineCacheEntry(
            key = cacheKey,
            nodes = parsed.nodes,
            unresolvedReferenceLabels = parsed.unresolvedReferenceLabels,
        )
        return ResolvedInline(
            nodes = parsed.nodes,
            unresolvedReferenceLabels = parsed.unresolvedReferenceLabels,
        )
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

        if (state.source.isEmpty() || state.source[state.source.length - 1] == '\n') {
            return state.source.length
        }
        val lastNewline = state.source.lastIndexOf('\n')
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

    private data class ResolvedInline(
        val nodes: List<InlineNode>,
        val unresolvedReferenceLabels: Set<String>,
    )

    private data class InlineHydration(
        val block: BlockNode,
        val unresolvedReferenceLabels: Set<String>,
    )

    private data class HydratedTopLevelBlock(
        val block: BlockNode,
        val unresolvedReferenceLabels: Set<String>,
    )
}
