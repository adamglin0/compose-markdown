package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.block.BlockTreeBuilder
import com.adamglin.compose.markdown.core.block.OpenBlock
import com.adamglin.compose.markdown.core.internal.SharedArray
import com.adamglin.compose.markdown.core.model.BlockNode

/**
 * Top-level blocks of the stable prefix. The block parser is done with them; the only later change is
 * resolving inline content again when a link reference definition they use changes, so blocks that use
 * reference labels keep their parsed form and are indexed by label. Snapshots share the storage.
 */
internal class StableBlocks {
    private val nodes = SharedArray<BlockNode>()
    private val sourceByIndex = HashMap<Int, OpenBlock>()
    private val indexesByLabel = HashMap<String, MutableSet<Int>>()

    val size: Int
        get() = nodes.size

    operator fun get(index: Int): BlockNode = nodes[index]

    fun add(node: BlockNode, source: OpenBlock) {
        index(index = nodes.size, source = source)
        nodes.add(node)
    }

    /** The stable blocks followed by [tail], as an immutable list that shares the stable storage. */
    fun followedBy(tail: List<BlockNode>): List<BlockNode> = nodes.followedBy(tail)

    /** Indexes, in document order, of the blocks that use any of [labels]. */
    fun indexesUsing(labels: Set<String>): List<Int> {
        if (labels.isEmpty()) {
            return emptyList()
        }
        return labels.flatMapTo(HashSet()) { indexesByLabel[it].orEmpty() }.sorted()
    }

    /** Builds the block at [index] again, resolving the inline content that uses [labels], and returns it. */
    fun rebuild(index: Int, labels: Set<String>, builder: BlockTreeBuilder): BlockNode {
        val source = checkNotNull(sourceByIndex[index]) { "block $index uses no reference labels" }
        source.labels.forEach { label -> indexesByLabel[label]?.remove(index) }
        builder.invalidate(source, labels)
        val node = checkNotNull(builder.build(source)) { "a block that used labels still builds to a node" }
        index(index = index, source = source)
        nodes[index] = node
        return node
    }

    fun clear() {
        nodes.clear()
        sourceByIndex.clear()
        indexesByLabel.clear()
    }

    private fun index(index: Int, source: OpenBlock) {
        if (source.labels.isEmpty()) {
            sourceByIndex.remove(index)
            return
        }
        sourceByIndex[index] = source
        source.labels.forEach { label -> indexesByLabel.getOrPut(label) { HashSet() } += index }
    }
}
