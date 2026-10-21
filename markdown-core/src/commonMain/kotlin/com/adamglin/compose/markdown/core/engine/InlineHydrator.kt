package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.inline.InlineParser
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.InlineNode
import com.adamglin.compose.markdown.core.model.TextRange

/**
 * Inline parse result of one leaf block, reused while the block keeps its id, its raw text and range, and
 * the link reference definitions stay unchanged ([referenceRevision]).
 */
internal class InlineCacheEntry(
    val literal: String,
    val range: TextRange,
    val referenceRevision: Int,
    val nodes: List<InlineNode>,
    val referenceLabels: Set<String>,
)

/** A block with parsed inline content and the reference labels its inline content uses. */
internal class HydratedBlock(
    val block: BlockNode,
    val referenceLabels: Set<String>,
)

/**
 * Replaces the raw text the block parser leaves in paragraphs, headings and table cells with parsed inline
 * nodes. Entries of [previousCache] are reused when nothing they depend on changed; [cache] collects the
 * entries of every block hydrated in this round, so blocks that left the mutable tail drop out of it.
 */
internal class InlineHydrator(
    private val inlineParser: InlineParser,
    private val referenceRevision: Int,
    private val previousCache: Map<Long, InlineCacheEntry>,
) {
    val cache: MutableMap<Long, InlineCacheEntry> = HashMap()
    var parsedCount: Int = 0
        private set
    var cacheHitCount: Int = 0
        private set

    fun hydrate(block: BlockNode): HydratedBlock {
        val labels = LinkedHashSet<String>()
        return HydratedBlock(block = hydrate(block, labels), referenceLabels = labels)
    }

    private fun hydrate(block: BlockNode, labels: MutableSet<String>): BlockNode = when (block) {
        is BlockNode.Paragraph -> block.copy(children = resolve(block, block.children, labels))
        is BlockNode.Heading -> block.copy(children = resolve(block, block.children, labels))
        is BlockNode.TableCell -> block.copy(children = resolve(block, block.children, labels))
        is BlockNode.BlockQuote -> block.copy(children = block.children.map { hydrate(it, labels) })
        is BlockNode.ListBlock -> block.copy(items = block.items.map { hydrate(it, labels) as BlockNode.ListItem })
        is BlockNode.ListItem -> block.copy(children = block.children.map { hydrate(it, labels) })
        is BlockNode.TableBlock -> block.copy(
            header = hydrate(block.header, labels) as BlockNode.TableRow,
            rows = block.rows.map { hydrate(it, labels) as BlockNode.TableRow },
        )
        is BlockNode.TableRow -> block.copy(cells = block.cells.map { hydrate(it, labels) as BlockNode.TableCell })
        is BlockNode.Document -> block.copy(children = block.children.map { hydrate(it, labels) })
        is BlockNode.FencedCodeBlock,
        is BlockNode.IndentedCodeBlock,
        is BlockNode.MathBlock,
        is BlockNode.ThematicBreak,
        -> block
    }

    /** The block parser leaves a leaf's inline content as one raw text node (or none when empty). */
    private fun resolve(block: BlockNode, children: List<InlineNode>, labels: MutableSet<String>): List<InlineNode> {
        val raw = children.singleOrNull() as? InlineNode.Text ?: return children
        val id = block.id.raw
        val cached = previousCache[id]
        val entry = if (
            cached != null &&
            cached.referenceRevision == referenceRevision &&
            cached.range == raw.range &&
            cached.literal == raw.literal
        ) {
            cacheHitCount += 1
            cached
        } else {
            parsedCount += 1
            val parsed = inlineParser.parse(literal = raw.literal, range = raw.range)
            InlineCacheEntry(
                literal = raw.literal,
                range = raw.range,
                referenceRevision = referenceRevision,
                nodes = parsed.nodes,
                referenceLabels = parsed.referenceLabels,
            )
        }
        cache[id] = entry
        labels += entry.referenceLabels
        return entry.nodes
    }
}
