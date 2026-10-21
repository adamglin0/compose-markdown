package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.block.BlockParser
import com.adamglin.compose.markdown.core.block.BlockTreeBuilder
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
 * Streaming engine on top of a resumable block parser.
 *
 * Every complete line is parsed exactly once ([BlockParser.incorporate]); closed top-level blocks are final
 * and move into the stable prefix, built once. The unterminated last line can still turn into something
 * else, so it is parsed in a fork of the parser that is thrown away after the call; the fork copies only
 * the open blocks. The mutable tail (the open top-level block and whatever the last line adds) is built
 * from the fork, reusing the cached nodes of every closed block inside it. Per-call work is therefore
 * proportional to the new text and the open part of the tail, not to the document or to the size of the
 * open block, apart from copying the text of the open leaf into its node.
 */
internal class IncrementalMarkdownEngine(
    override val dialect: MarkdownDialect,
) : MarkdownEngine {
    private val source = SourceBuffer()
    private val lineIndex = LineIndex()
    private val normalizer = ChunkNormalizer()
    private val definitions = ReferenceDefinitions()
    private val inlineCache = InlineCache(InlineParser(dialect = dialect, referenceDefinitions = definitions.effective))
    private val blockIds = BlockIdAllocator()
    private val builder = BlockTreeBuilder(lineIndex = lineIndex, ids = blockIds, inline = inlineCache)
    private val stable = StableBlocks()

    private var parser = BlockParser(dialect = dialect, lineIndex = lineIndex)

    /** Source offset up to which lines were incorporated into [parser]: the start of the unterminated last line. */
    private var parsedEnd = 0

    /** Top-level blocks after the stable prefix. */
    private var tail: List<BlockNode> = emptyList()
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
        return update(appendedChars = text.length)
    }

    override fun finish(): ParseDelta {
        if (isFinal) {
            return noChangeDelta()
        }
        val text = normalizer.flush()
        appendSource(text)
        isFinal = true
        return update(appendedChars = text.length)
    }

    override fun snapshot(): MarkdownSnapshot = snapshot

    override fun reset() {
        source.clear()
        lineIndex.reset()
        normalizer.reset()
        definitions.clear()
        inlineCache.clear()
        blockIds.reset()
        stable.clear()
        parser = BlockParser(dialect = dialect, lineIndex = lineIndex)
        parsedEnd = 0
        tail = emptyList()
        version = 0L
        isFinal = false
        snapshot = emptySnapshot()
    }

    private fun appendSource(text: String) {
        lineIndex.append(text)
        source.append(text)
    }

    private fun update(appendedChars: Int): ParseDelta {
        version += 1
        val dirtyStart = parsedEnd
        val sourceLength = source.length
        val previousTail = tail
        val previousStableCount = stable.size
        val builtBefore = builder.builtCount

        // Complete lines become part of the parser for good; on finish() so does the last line.
        val completeEnd = if (isFinal) sourceLength else lineIndex.lastLineStart()
        val completeLines = lineIndex.lines(source = source.content, startOffset = parsedEnd, endExclusive = completeEnd)
        completeLines.forEach(parser::incorporate)
        parsedEnd = completeEnd
        if (isFinal) {
            parser.finish()
        }
        val closedBlocks = parser.takeClosedBlocks()
        val openBlock = parser.openBlock()

        // The unterminated last line is parsed in a fork, which is then closed as if the input ended there.
        val pendingLine = lineIndex.lines(source = source.content, startOffset = completeEnd, endExclusive = sourceLength).singleOrNull()
        val fork = if (isFinal) null else parser.fork()
        pendingLine?.let { fork?.incorporate(it) }
        fork?.finish()

        // Definitions on the unterminated last line may still change, so they take effect once the line ends.
        val tailDefinitions = LinkedHashMap<String, LinkReferenceDefinition>()
        fork?.takeDefinitions()?.forEach { definition ->
            if (definition.range.endExclusive <= completeEnd && definition.label !in tailDefinitions) {
                tailDefinitions[definition.label] = definition
            }
        }
        val changedLabels = definitions.update(newlyStable = parser.takeDefinitions(), newTail = tailDefinitions)
        val changedBlocks = ArrayList<BlockChange>()
        var rebuiltStableCount = 0
        if (changedLabels.isNotEmpty()) {
            inlineCache.definitionsChanged()
            stable.indexesUsing(changedLabels).forEach { index ->
                val previous = stable[index]
                val current = stable.rebuild(index = index, labels = changedLabels, builder = builder)
                rebuiltStableCount += 1
                if (current != previous) {
                    changedBlocks += BlockChange(id = current.id, oldIndex = index, newIndex = index, block = current)
                }
            }
            closedBlocks.forEach { builder.invalidate(it, changedLabels) }
            openBlock?.let { builder.invalidate(it, changedLabels) }
        }

        closedBlocks.forEach { block ->
            builder.build(block)?.let { node -> stable.add(node = node, source = block) }
        }
        tail = fork?.blocks()?.mapNotNull(builder::build).orEmpty()
        val stablePrefixEnd = when {
            isFinal -> sourceLength
            openBlock != null -> lineIndex.lineStart(openBlock.startLine)
            else -> completeEnd
        }
        val removedBlockIds = blockIds.endCall()

        // Only blocks from the previous tail onward can be new, moved, changed or removed.
        val previousById = HashMap<BlockId, Int>(previousTail.size * 2)
        previousTail.forEachIndexed { offset, block -> previousById[block.id] = previousStableCount + offset }
        val blocks = stable.followedBy(tail)
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
                lineCount = lineIndex.lineCount(),
                blocks = blocks,
            ),
            stablePrefixRange = TextRange(start = 0, endExclusive = stablePrefixEnd),
            isFinal = isFinal,
        )
        val stats = ParseStats(
            appendedChars = appendedChars,
            processedLines = completeLines.size + (if (pendingLine != null) 1 else 0),
            builtBlocks = builder.builtCount - builtBefore,
            preservedBlocks = previousStableCount - rebuiltStableCount,
            inlineParsedBlockCount = inlineCache.parsedCount,
            inlineCacheHitBlockCount = inlineCache.hitCount,
        )
        inlineCache.endCall()
        return ParseDelta(
            version = version,
            changedBlocks = changedBlocks,
            removedBlockIds = removedBlockIds,
            dirtyRegion = TextRange(start = dirtyStart, endExclusive = sourceLength),
            snapshot = snapshot,
            stats = stats,
        )
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
