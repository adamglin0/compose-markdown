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
import com.adamglin.compose.markdown.core.source.IndexedLine
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
 *
 * With [holdUnsettledLines], a line shows only once more text can no longer change its block structure or
 * that of the lines above it: an unterminated last line that is a table row, a `|` paragraph line or still
 * only block markers is left out of the fork, and a complete `|` paragraph line that the next line could
 * turn into a table header is not incorporated until that next line ends too. [releaseHeldLines] shows the
 * first held line anyway; it then stays shown while it grows. While held text grows without settling, the
 * shown source does not change, so a call builds nothing and keeps the previous document.
 */
internal class IncrementalMarkdownEngine(
    override val dialect: MarkdownDialect,
    private val holdUnsettledLines: Boolean = false,
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

    /**
     * Source offset up to which lines were incorporated into [parser]: the start of the unterminated last line,
     * or of a held-back table header candidate before it.
     */
    private var parsedEnd = 0

    /** Lines starting before this offset were released by [releaseHeldLines] and are never held again. */
    private var releasedEnd = 0

    /** End of the first held line in the current snapshot, or -1 when no line is held. */
    private var firstHeldLineEnd = -1

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

    override fun releaseHeldLines(): ParseDelta {
        if (!snapshot.hasHeldLines) {
            return noChangeDelta()
        }
        // Only the first held line has waited; a line after it (the row below a held header) can still be held.
        releasedEnd = firstHeldLineEnd
        return update(appendedChars = 0)
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
        releasedEnd = 0
        firstHeldLineEnd = -1
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
        completeLines.dropLast(1).forEach(parser::incorporate)
        val heldLine = completeLines.lastOrNull()?.takeIf(::isHeldTableHeaderCandidate)
        completeLines.lastOrNull()?.takeIf { it !== heldLine }?.let(parser::incorporate)
        parsedEnd = heldLine?.range?.start ?: completeEnd
        if (isFinal) {
            parser.finish()
        }
        val closedBlocks = parser.takeClosedBlocks()
        val openBlock = parser.openBlock()

        // The unterminated last line is parsed in a fork, which is then closed as if the input ended there.
        val pendingLine = if (heldLine != null) null else lineIndex.lines(source = source.content, startOffset = completeEnd, endExclusive = sourceLength).singleOrNull()
        var fork = if (isFinal) null else parser.fork()
        var pendingLineHeld = false
        if (fork != null && pendingLine != null) {
            fork.incorporate(pendingLine)
            if (canHold(pendingLine) && (isOnlyBlockMarkers(pendingLine.content) || fork.endsInTable() || fork.endsInTableHeaderCandidate())) {
                fork = parser.fork()
                pendingLineHeld = true
            }
        }
        // The document is the parse of the source up to the first held line.
        val firstHeldLine = heldLine ?: pendingLine?.takeIf { pendingLineHeld }
        val visibleEnd = firstHeldLine?.range?.start ?: sourceLength
        firstHeldLineEnd = firstHeldLine?.range?.endExclusive ?: -1
        val visibleLineCount = when {
            firstHeldLine == null -> lineIndex.lineCount()
            visibleEnd == 0 -> 0
            else -> firstHeldLine.number + 1
        }

        // Held text that grows without settling leaves the shown source, and so the document, as it was:
        // nothing was incorporated, so nothing is built and the document instance stays the same.
        if (parsedEnd == dirtyStart && visibleEnd == snapshot.document.sourceLength && !isFinal) {
            snapshot = snapshot.copy(version = version, hasHeldLines = firstHeldLine != null)
            return ParseDelta(
                changedBlocks = emptyList(),
                removedBlockIds = emptyList(),
                dirtyRegion = TextRange(start = dirtyStart, endExclusive = sourceLength),
                snapshot = snapshot,
                stats = ParseStats.Empty.copy(appendedChars = appendedChars, processedLines = completeLines.size + (if (pendingLine != null) 1 else 0)),
            )
        }
        fork?.finish()

        // Definitions on the unterminated last line may still change, so they take effect once the line ends.
        val tailDefinitions = LinkedHashMap<String, LinkReferenceDefinition>()
        fork?.takeDefinitions()?.forEach { definition ->
            if (definition.range.endExclusive <= parsedEnd && definition.label !in tailDefinitions) {
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
                if (current !== previous) {
                    changedBlocks += BlockChange(id = current.id, oldIndex = index, newIndex = index, block = current)
                }
            }
            closedBlocks.forEach { builder.invalidate(it, changedLabels) }
            openBlock?.let { builder.invalidate(it, changedLabels) }
        }

        // A block rebuilt equal to its previous version keeps that instance, so unchanged blocks are always
        // the same instances across snapshots.
        val previousIndexById = HashMap<BlockId, Int>(previousTail.size * 2)
        previousTail.forEachIndexed { offset, block -> previousIndexById[block.id] = previousStableCount + offset }
        fun BlockNode.orPrevious(): BlockNode =
            previousIndexById[id]?.let { previousTail[it - previousStableCount] }?.takeIf { it == this } ?: this
        closedBlocks.forEach { block ->
            builder.build(block)?.let { node -> stable.add(node = node.orPrevious(), source = block) }
        }
        tail = fork?.blocks()?.mapNotNull { block -> builder.build(block)?.orPrevious() }.orEmpty()
        val stablePrefixEnd = when {
            isFinal -> sourceLength
            openBlock != null -> lineIndex.lineStart(openBlock.startLine)
            else -> parsedEnd
        }
        val removedBlockIds = blockIds.endCall()

        // Only blocks from the previous tail onward can be new, moved, changed or removed.
        val blocks = stable.followedBy(tail)
        for (index in previousStableCount until blocks.size) {
            val block = blocks[index]
            val oldIndex = previousIndexById[block.id]
            if (oldIndex != index || previousTail[oldIndex - previousStableCount] !== block) {
                changedBlocks += BlockChange(id = block.id, oldIndex = oldIndex, newIndex = index, block = block)
            }
        }

        snapshot = MarkdownSnapshot(
            version = version,
            dialect = dialect,
            document = MarkdownDocument(
                sourceLength = visibleEnd,
                lineCount = visibleLineCount,
                blocks = blocks,
            ),
            stablePrefixEnd = stablePrefixEnd,
            hasHeldLines = firstHeldLine != null,
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
            changedBlocks = changedBlocks,
            removedBlockIds = removedBlockIds,
            dirtyRegion = TextRange(start = dirtyStart, endExclusive = sourceLength),
            snapshot = snapshot,
            stats = stats,
        )
    }

    private fun canHold(line: IndexedLine): Boolean = holdUnsettledLines && !isFinal && line.range.start >= releasedEnd

    /** Whether [line], the last complete line, has to wait for the next line to tell whether it is a table header. */
    private fun isHeldTableHeaderCandidate(line: IndexedLine): Boolean {
        if (!canHold(line) || '|' !in line.content) {
            return false
        }
        val probe = parser.fork()
        probe.incorporate(line)
        return probe.endsInTableHeaderCandidate()
    }

    private fun noChangeDelta(): ParseDelta = ParseDelta(
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
        stablePrefixEnd = 0,
        hasHeldLines = false,
        isFinal = false,
    )
}

/**
 * Whether [line] holds nothing but block markers so far (`-`, `#`, `>`, `1.`, a fence, `$$`, `- [x]`, …): the
 * next character may still decide between a list item, a setext underline, a thematic break, a heading or
 * plain text.
 */
private fun isOnlyBlockMarkers(line: String): Boolean = line.isNotEmpty() && line.indices.all { index ->
    val char = line[index]
    char.isWhitespace() || char.isDigit() || char in BLOCK_MARKER_CHARS || ((char == 'x' || char == 'X') && line.getOrNull(index - 1) == '[')
}

private const val BLOCK_MARKER_CHARS = "-*+_=#>|~`$\\[]():."
