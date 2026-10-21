package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.model.BlockNode

/**
 * Top-level blocks of the stable prefix. The block parser never sees them again; the only later change is
 * re-resolving inline content when a link reference definition they use changes, so blocks that use
 * reference labels keep their raw (pre-inline) form and are indexed by label.
 */
internal class StableBlocks {
    val blocks: MutableList<BlockNode> = ArrayList()
    private val referencingByIndex = HashMap<Int, Referencing>()
    private val indexesByLabel = HashMap<String, MutableSet<Int>>()

    val size: Int
        get() = blocks.size

    fun add(raw: BlockNode, hydrated: HydratedBlock) {
        val index = blocks.size
        blocks += hydrated.block
        index(index = index, raw = raw, labels = hydrated.referenceLabels)
    }

    /** Indexes, in document order, of the blocks that use any of [labels]. */
    fun indexesUsing(labels: Set<String>): List<Int> {
        if (labels.isEmpty()) {
            return emptyList()
        }
        return labels.flatMapTo(HashSet()) { indexesByLabel[it].orEmpty() }.sorted()
    }

    /** Resolves the inline content of the block at [index] again and returns the new block. */
    fun rehydrate(index: Int, hydrator: InlineHydrator): BlockNode {
        val referencing = checkNotNull(referencingByIndex[index]) { "block $index uses no reference labels" }
        val hydrated = hydrator.hydrate(referencing.raw)
        referencing.labels.forEach { label -> indexesByLabel[label]?.remove(index) }
        referencingByIndex.remove(index)
        index(index = index, raw = referencing.raw, labels = hydrated.referenceLabels)
        blocks[index] = hydrated.block
        return hydrated.block
    }

    fun clear() {
        blocks.clear()
        referencingByIndex.clear()
        indexesByLabel.clear()
    }

    private fun index(index: Int, raw: BlockNode, labels: Set<String>) {
        if (labels.isEmpty()) {
            return
        }
        referencingByIndex[index] = Referencing(raw = raw, labels = labels)
        labels.forEach { label -> indexesByLabel.getOrPut(label) { HashSet() } += index }
    }

    private class Referencing(
        val raw: BlockNode,
        val labels: Set<String>,
    )
}
