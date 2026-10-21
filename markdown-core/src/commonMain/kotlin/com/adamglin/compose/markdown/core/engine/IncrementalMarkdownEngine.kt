package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.block.BlockParseResult
import com.adamglin.compose.markdown.core.block.BlockParser
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.inline.InlineParser
import com.adamglin.compose.markdown.core.internal.LinkReferenceDefinition
import com.adamglin.compose.markdown.core.model.BlockChange
import com.adamglin.compose.markdown.core.model.BlockId
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.MarkdownDocument
import com.adamglin.compose.markdown.core.model.MarkdownSnapshot
import com.adamglin.compose.markdown.core.model.ParseDelta
import com.adamglin.compose.markdown.core.model.ParseStats
import com.adamglin.compose.markdown.core.model.TextRange
import com.adamglin.compose.markdown.core.source.LineIndex
import com.adamglin.compose.markdown.core.source.SourceBuffer

/**
 * Streaming engine: the document is a stable prefix of final top-level blocks followed by a mutable tail
 * that is re-parsed on every call.
 *
 * Block parser contract this engine relies on ([BlockParser.parse]):
 * - Parsing may start at any offset that begins a top-level block (or is the end of the last complete
 *   line) once everything before it is closed, and yields exactly the blocks a parse from the start of the
 *   document would yield from there on.
 * - Unless final, [BlockParseResult.openBlockStack] lists the blocks that later input can still change,
 *   outermost first. Its first frame starts the first top-level block that may change; every top-level
 *   block and link reference definition before it is final. A block closed only by the unterminated last
 *   line stays open, because that line can still turn into something else.
 *
 * Each call therefore parses from the end of the stable prefix to the end of input, moves the blocks
 * before the first open frame into the stable prefix, and keeps the rest as the new tail. Per-call work is
 * proportional to the tail (plus a copy of the top-level block list for the snapshot), not to the
 * document.
 */
internal class IncrementalMarkdownEngine(
    override val dialect: MarkdownDialect,
) : MarkdownEngine {
    private val source = SourceBuffer()
    private val lineIndex = LineIndex()
    private val normalizer = ChunkNormalizer()
    private val definitions = ReferenceDefinitions()
    private val inlineParser = InlineParser(dialect = dialect, referenceDefinitions = definitions.effective)
    private val stable = StableBlocks()
    private val blockIds = BlockIdAllocator()

    /** Top-level blocks after the stable prefix, with inline content. */
    private var tail: List<BlockNode> = emptyList()
    private var stablePrefixEnd = 0
    private var inlineCache: Map<Long, InlineCacheEntry> = emptyMap()
    private var referenceRevision = 0
    private var version = 0L
    private var isFinal = false
    private var snapshot = emptySnapshot()

    override fun append(chunk: String): ParseDelta {
        check(!isFinal) { "Cannot call append() after finish(); call reset() or create a new MarkdownEngine." }
        val text = normalizer.normalize(chunk)
        if (text.isEmpty()) {
            return noChangeDelta()
        }
        appendSource(text)
        return rebuild(appendedChars = text.length)
    }

    override fun finish(): ParseDelta {
        if (isFinal) {
            return noChangeDelta()
        }
        val text = normalizer.flush()
        appendSource(text)
        isFinal = true
        return rebuild(appendedChars = text.length)
    }

    override fun snapshot(): MarkdownSnapshot = snapshot

    override fun reset() {
        source.clear()
        lineIndex.reset()
        normalizer.reset()
        definitions.clear()
        stable.clear()
        tail = emptyList()
        stablePrefixEnd = 0
        inlineCache = emptyMap()
        referenceRevision = 0
        blockIds.reset()
        version = 0L
        isFinal = false
        snapshot = emptySnapshot()
    }

    private fun appendSource(text: String) {
        lineIndex.append(chunk = text, startOffset = source.length)
        source.append(text)
    }

    private fun rebuild(appendedChars: Int): ParseDelta {
        version += 1
        val parseStart = stablePrefixEnd
        val sourceLength = source.length
        val previousTail = tail
        val previousStableCount = stable.size

        val result = parseTail(parseStart = parseStart)
        val newStablePrefixEnd = if (isFinal) sourceLength else stablePrefixEndAfter(result)
        check(newStablePrefixEnd >= parseStart) { "stable prefix moved backward: $parseStart -> $newStablePrefixEnd" }

        // Definitions on the unterminated last line may still change, so they take effect once the line ends.
        val pendingLineStart = if (isFinal) sourceLength else lineIndex.lastLineStart()
        val newlyStableDefinitions = ArrayList<LinkReferenceDefinition>()
        val tailDefinitions = LinkedHashMap<String, LinkReferenceDefinition>()
        result.parsedDefinitions.values.forEach { definition ->
            when {
                definition.range.start < newStablePrefixEnd -> newlyStableDefinitions += definition
                definition.range.endExclusive <= pendingLineStart -> tailDefinitions[definition.label] = definition
            }
        }
        val changedLabels = definitions.update(newlyStable = newlyStableDefinitions, newTail = tailDefinitions)
        if (changedLabels.isNotEmpty()) {
            referenceRevision += 1
        }

        val hydrator = InlineHydrator(
            inlineParser = inlineParser,
            referenceRevision = referenceRevision,
            previousCache = inlineCache,
        )
        val changedBlocks = ArrayList<BlockChange>()

        // Stable blocks are only touched when a definition they use changed.
        val rehydratedIndexes = stable.indexesUsing(changedLabels)
        rehydratedIndexes.forEach { index ->
            val previous = stable.blocks[index]
            val current = stable.rehydrate(index = index, hydrator = hydrator)
            if (current != previous) {
                changedBlocks += BlockChange(id = current.id, oldIndex = index, newIndex = index, block = current)
            }
        }

        val newTail = ArrayList<BlockNode>()
        result.blocks.forEach { raw ->
            val hydrated = hydrator.hydrate(raw)
            if (raw.range.start < newStablePrefixEnd) {
                stable.add(raw = raw, hydrated = hydrated)
            } else {
                newTail += hydrated.block
            }
        }
        tail = newTail
        stablePrefixEnd = newStablePrefixEnd
        inlineCache = hydrator.cache
        val removedBlockIds = blockIds.endParse(tailStart = newStablePrefixEnd)

        // Only blocks from the previous tail onward can be new, moved, changed or removed.
        val previousById = HashMap<BlockId, Int>(previousTail.size * 2)
        previousTail.forEachIndexed { offset, block -> previousById[block.id] = previousStableCount + offset }
        val blocks = ArrayList<BlockNode>(stable.size + tail.size)
        blocks.addAll(stable.blocks)
        blocks.addAll(tail)
        for (index in previousStableCount until blocks.size) {
            val block = blocks[index]
            val oldIndex = previousById[block.id]
            if (oldIndex != index || previousTail[oldIndex - previousStableCount] != block) {
                changedBlocks += BlockChange(id = block.id, oldIndex = oldIndex, newIndex = index, block = block)
            }
        }

        snapshot = MarkdownSnapshot(
            version = version,
            dialect = dialect,
            document = MarkdownDocument(
                sourceLength = sourceLength,
                lineCount = lineIndex.lineCount(sourceLength = sourceLength),
                blocks = blocks,
            ),
            stablePrefixRange = TextRange(start = 0, endExclusive = stablePrefixEnd),
            isFinal = isFinal,
        )
        return ParseDelta(
            version = version,
            changedBlocks = changedBlocks,
            removedBlockIds = removedBlockIds,
            dirtyRegion = TextRange(start = parseStart, endExclusive = sourceLength),
            snapshot = snapshot,
            stats = ParseStats(
                appendedChars = appendedChars,
                processedLines = result.processedLineCount,
                reparsedBlocks = result.blocks.size + rehydratedIndexes.size,
                preservedBlocks = previousStableCount - rehydratedIndexes.size,
                inlineParsedBlockCount = hydrator.parsedCount,
                inlineCacheHitBlockCount = hydrator.cacheHitCount,
            ),
        )
    }

    private fun parseTail(parseStart: Int): BlockParseResult {
        val parser = BlockParser(
            sourceBuffer = source,
            lineIndex = lineIndex,
            dialect = dialect,
            allocateBlockId = blockIds::allocate,
        )
        return parser.parse(isFinal = isFinal, range = TextRange(start = parseStart, endExclusive = source.length))
    }

    /**
     * Start of the first top-level block that later input can change. Without open blocks everything up to
     * the last complete line is final; an unterminated last line never is, even when it is blank.
     */
    private fun stablePrefixEndAfter(result: BlockParseResult): Int {
        val firstOpenStart = result.openBlockStack.firstOrNull()?.startOffset ?: source.length
        return minOf(firstOpenStart, lineIndex.lastLineStart())
    }

    private fun noChangeDelta(): ParseDelta = ParseDelta(
        version = snapshot.version,
        changedBlocks = emptyList(),
        removedBlockIds = emptyList(),
        dirtyRegion = TextRange(start = source.length, endExclusive = source.length),
        snapshot = snapshot,
        stats = ParseStats.Empty,
    )

    private fun emptySnapshot(): MarkdownSnapshot = MarkdownSnapshot(
        version = 0L,
        dialect = dialect,
        document = MarkdownDocument(sourceLength = 0, lineCount = 0, blocks = emptyList()),
        stablePrefixRange = TextRange.Empty,
        isFinal = false,
    )
}
