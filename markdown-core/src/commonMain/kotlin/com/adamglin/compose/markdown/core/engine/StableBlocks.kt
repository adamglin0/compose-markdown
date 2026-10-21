package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.model.BlockNode

/**
 * Top-level blocks of the stable prefix. The block parser never sees them again; the only later change is
 * re-resolving inline content when a link reference definition they use changes, so blocks that use
 * reference labels keep their raw (pre-inline) form and are indexed by label.
 *
 * Snapshots share the storage instead of copying it on every append: the storage only grows, a snapshot
 * reads the slots that were filled before it was published, and replacing a block copies the storage
 * first, so published snapshots never observe a change.
 */
internal class StableBlocks {
    private var storage = arrayOfNulls<BlockNode>(INITIAL_CAPACITY)
    private val referencingByIndex = HashMap<Int, Referencing>()
    private val indexesByLabel = HashMap<String, MutableSet<Int>>()

    var size: Int = 0
        private set

    operator fun get(index: Int): BlockNode = checkNotNull(storage[index])

    fun add(raw: BlockNode, hydrated: HydratedBlock) {
        if (size == storage.size) {
            storage = storage.copyOf(newSize = size * 2)
        }
        storage[size] = hydrated.block
        index(index = size, raw = raw, labels = hydrated.referenceLabels)
        size += 1
    }

    /** The stable blocks followed by [tail], as an immutable list that shares the stable storage. */
    fun followedBy(tail: List<BlockNode>): List<BlockNode> = BlockList(storage = storage, stableSize = size, tail = tail)

    /** Indexes, in document order, of the blocks that use any of [labels]. */
    fun indexesUsing(labels: Set<String>): List<Int> {
        if (labels.isEmpty()) {
            return emptyList()
        }
        return labels.flatMapTo(HashSet()) { indexesByLabel[it].orEmpty() }.sorted()
    }

    /** Resolves the inline content of the block at [index] again and returns the new block. */
    fun rehydrate(index: Int, hydrator: InlineHydrator): BlockNode {
        val referencing = checkNotNull(referencingByIndex.remove(index)) { "block $index uses no reference labels" }
        val hydrated = hydrator.hydrate(referencing.raw)
        referencing.labels.forEach { label -> indexesByLabel[label]?.remove(index) }
        index(index = index, raw = referencing.raw, labels = hydrated.referenceLabels)
        storage = storage.copyOf()
        storage[index] = hydrated.block
        return hydrated.block
    }

    fun clear() {
        storage = arrayOfNulls(INITIAL_CAPACITY)
        size = 0
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

    private class BlockList(
        private val storage: Array<BlockNode?>,
        private val stableSize: Int,
        private val tail: List<BlockNode>,
    ) : AbstractList<BlockNode>() {
        override val size: Int
            get() = stableSize + tail.size

        override fun get(index: Int): BlockNode {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException("index $index, size $size")
            }
            return if (index < stableSize) checkNotNull(storage[index]) else tail[index - stableSize]
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
    }
}
