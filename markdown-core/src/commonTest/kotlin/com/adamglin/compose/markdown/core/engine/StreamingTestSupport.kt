package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.BlockId
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.InlineNode
import com.adamglin.compose.markdown.core.model.ListType
import com.adamglin.compose.markdown.core.model.MarkdownSnapshot
import com.adamglin.compose.markdown.core.model.ParseDelta
import kotlin.random.Random

internal val allDialects: List<MarkdownDialect> = listOf(
    MarkdownDialect.ChatFast,
    MarkdownDialect.CommonMarkCore,
    MarkdownDialect.GfmCompat,
    MarkdownDialect.GfmMath,
)

/**
 * Streams [chunks] through one engine and checks every step against a one-shot parse of the same prefix,
 * plus the streaming invariants (stable prefix, ids, deltas). Returns the first violation, or null.
 */
internal fun streamingViolation(dialect: MarkdownDialect, chunks: List<String>): String? {
    val engine = MarkdownEngine(dialect = dialect)
    val prefix = StringBuilder()
    var previous = engine.snapshot()
    for ((step, chunk) in chunks.withIndex()) {
        prefix.append(chunk)
        val delta = engine.append(chunk)
        val expected = MarkdownEngine(dialect = dialect).append(prefix.toString()).snapshot
        stepViolation(previous, delta, expected)?.let { return "step $step (+${chunk.escaped()}): $it" }
        previous = delta.snapshot
    }
    val finished = engine.finish()
    val expected = MarkdownEngine(dialect = dialect).also { it.append(prefix.toString()) }.finish().snapshot
    stepViolation(previous, finished, expected)?.let { return "finish: $it" }
    return null
}

/** Only checks the result after [MarkdownEngine.finish]; cheap enough for fuzzing many inputs. */
internal fun finalResultDiffers(dialect: MarkdownDialect, text: String, chunks: List<String>): Boolean {
    val streamed = MarkdownEngine(dialect = dialect).also { engine -> chunks.forEach(engine::append) }.finish()
    val oneShot = MarkdownEngine(dialect = dialect).also { it.append(text) }.finish()
    return streamed.snapshot.document.blocks.dump() != oneShot.snapshot.document.blocks.dump()
}

private fun stepViolation(previous: MarkdownSnapshot, delta: ParseDelta, expected: MarkdownSnapshot): String? {
    val snapshot = delta.snapshot
    val actualDump = snapshot.document.blocks.dump()
    val expectedDump = expected.document.blocks.dump()
    if (actualDump != expectedDump) {
        return "tree differs from one-shot parse\n  expected: ${expectedDump.escaped()}\n  actual:   ${actualDump.escaped()}"
    }
    if (snapshot.document.sourceLength != expected.document.sourceLength || snapshot.document.lineCount != expected.document.lineCount) {
        return "source length/line count differ from one-shot parse"
    }
    if (snapshot.stablePrefixEnd != expected.stablePrefixEnd) {
        return "stable prefix ends at ${snapshot.stablePrefixEnd}, one-shot parse at ${expected.stablePrefixEnd}"
    }
    if (snapshot.stablePrefixEnd < previous.stablePrefixEnd) {
        return "stable prefix moved backward ${previous.stablePrefixEnd} -> ${snapshot.stablePrefixEnd}"
    }
    val ids = HashSet<BlockId>()
    snapshot.document.blocks.forEach { block ->
        block.forEachBlock { if (!ids.add(it.id)) return "duplicate block id ${it.id.raw}" }
    }
    // Blocks of the previous stable prefix keep their index, id and structure.
    previous.document.blocks.forEachIndexed { index, block ->
        if (block.range.endExclusive > previous.stablePrefixEnd) {
            return@forEachIndexed
        }
        val current = snapshot.document.blocks.getOrNull(index)
        if (current == null || current.id != block.id || current.structure() != block.structure()) {
            return "stable block ${block.structure().escaped()} changed to ${current?.structure()?.escaped()}"
        }
    }
    // Applying the delta to the previous blocks reproduces the snapshot.
    val previousById = previous.document.blocks.associateBy { it.id }
    val changedById = delta.changedBlocks.associateBy { it.id }
    val removed = delta.removedBlockIds.toSet()
    snapshot.document.blocks.forEachIndexed { index, block ->
        val change = changedById[block.id]
        val applied = change?.block ?: previousById[block.id]?.takeIf { block.id !in removed }
        if (applied != block) {
            return "delta does not produce block ${index} ${block.structure().escaped()}"
        }
        if (change != null && change.newIndex != index) {
            return "change of block ${block.id.raw} reports index ${change.newIndex}, not $index"
        }
        if (change == null && previous.document.blocks.indexOfFirst { it.id == block.id } != index) {
            return "block ${block.id.raw} moved without a change"
        }
        if (change == null && applied !== block) {
            return "unchanged block ${block.id.raw} is a new instance"
        }
    }
    val currentIds = HashSet<BlockId>()
    snapshot.document.blocks.forEach { block -> block.forEachBlock { currentIds += it.id } }
    previous.document.blocks.forEach { block ->
        block.forEachBlock {
            if (it.id !in currentIds && it.id !in removed) {
                return "block ${it.id.raw} vanished without being reported removed"
            }
        }
    }
    if (delta.removedBlockIds.any { it in currentIds }) {
        return "a block reported removed is still present"
    }
    return null
}

internal fun charChunks(text: String): List<String> = text.map { it.toString() }

/** Chunks of [minSize]..[maxSize] characters, like the tokens an LLM streams. */
internal fun randomChunks(text: String, random: Random, minSize: Int = 1, maxSize: Int = 8): List<String> {
    val chunks = ArrayList<String>()
    var index = 0
    while (index < text.length) {
        val size = random.nextInt(minSize, maxSize + 1)
        chunks += text.substring(index, minOf(text.length, index + size))
        index += size
    }
    return chunks
}

internal fun String.escaped(): String = replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")

private inline fun BlockNode.forEachBlock(action: (BlockNode) -> Unit) {
    val stack = ArrayDeque<BlockNode>()
    stack.addLast(this)
    while (stack.isNotEmpty()) {
        val block = stack.removeLast()
        action(block)
        block.childBlocks().forEach(stack::addLast)
    }
}

private fun BlockNode.childBlocks(): List<BlockNode> = when (this) {
    is BlockNode.BlockQuote -> children
    is BlockNode.Document -> children
    is BlockNode.ListBlock -> items
    is BlockNode.ListItem -> children
    is BlockNode.TableBlock -> listOf(header) + rows
    is BlockNode.TableRow -> cells
    is BlockNode.TableCell,
    is BlockNode.FencedCodeBlock,
    is BlockNode.IndentedCodeBlock,
    is BlockNode.Heading,
    is BlockNode.MathBlock,
    is BlockNode.Paragraph,
    is BlockNode.ThematicBreak,
    -> emptyList()
}

/** Blocks with ranges, block attributes and inline content; block and inline ids are left out. */
internal fun List<BlockNode>.dump(): String = buildString { this@dump.forEach { appendBlock(it, withInline = true) } }

private fun BlockNode.structure(): String = buildString { appendBlock(this@structure, withInline = false) }

private fun StringBuilder.appendBlock(block: BlockNode, withInline: Boolean) {
    append(block::class.simpleName).append(block.range.start).append('-').append(block.range.endExclusive)
    append(" L").append(block.lineRange.startLine).append('-').append(block.lineRange.endLineExclusive)
    when (block) {
        is BlockNode.Heading -> append(" h${block.level} ${block.style}")
        is BlockNode.FencedCodeBlock -> append(" info=${block.infoString} closed=${block.isClosed} '${block.literal}'")
        is BlockNode.IndentedCodeBlock -> append(" '${block.literal}'")
        is BlockNode.MathBlock -> append(" ${block.delimiter} closed=${block.isClosed} '${block.latex}'")
        is BlockNode.ListBlock -> append(
            when (val type = block.type) {
                is ListType.Bullet -> " bullet${type.bulletChar}"
                is ListType.Ordered -> " ordered${type.startNumber}${type.delimiter}"
            },
        ).append(" loose=${block.isLoose}")
        is BlockNode.ListItem -> append(" task=${block.taskState}")
        is BlockNode.TableBlock -> append(" ${block.alignments}")
        is BlockNode.TableRow -> append(" header=${block.isHeader}")
        is BlockNode.ThematicBreak -> append(" '${block.marker}'")
        else -> Unit
    }
    append('{')
    if (withInline) {
        when (block) {
            is BlockNode.Paragraph -> block.children.forEach { appendInline(it) }
            is BlockNode.Heading -> block.children.forEach { appendInline(it) }
            is BlockNode.TableCell -> block.children.forEach { appendInline(it) }
            else -> Unit
        }
    }
    block.childBlocks().forEach { appendBlock(it, withInline) }
    append("} ")
}

private fun StringBuilder.appendInline(node: InlineNode) {
    append(node::class.simpleName).append(node.range.start).append('-').append(node.range.endExclusive)
    when (node) {
        is InlineNode.Text -> append("'${node.literal}'")
        is InlineNode.CodeSpan -> append("'${node.literal}'")
        is InlineNode.UnsupportedInline -> append("'${node.literal}'")
        is InlineNode.MathSpan -> append("'${node.latex}' ${node.delimiter}")
        is InlineNode.Link -> append("[${node.destination} ${node.title} ${node.referenceLabel}]")
        is InlineNode.Image -> append("[${node.destination} ${node.title} ${node.referenceLabel}]")
        else -> Unit
    }
    val children = when (node) {
        is InlineNode.Emphasis -> node.children
        is InlineNode.Strong -> node.children
        is InlineNode.Strikethrough -> node.children
        is InlineNode.Link -> node.children
        is InlineNode.Image -> node.alt
        else -> emptyList()
    }
    if (children.isNotEmpty()) {
        append('(')
        children.forEach { appendInline(it) }
        append(')')
    }
    append(' ')
}
