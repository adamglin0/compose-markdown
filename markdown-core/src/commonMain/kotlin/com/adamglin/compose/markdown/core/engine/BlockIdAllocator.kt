package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.model.BlockId

/**
 * Hands out block ids so a block keeps its id while it is re-parsed. The block parser asks for an id per
 * block with an identity key (kind, start offset, discriminator such as a heading level); a key that was
 * allocated in the previous parse of the mutable tail gets the same id again.
 */
internal class BlockIdAllocator {
    private var nextId = 1L
    private var reusable = LinkedHashMap<IdentityKey, ArrayDeque<Long>>()
    private var allocated = ArrayList<Pair<IdentityKey, Long>>()

    fun allocate(kind: String, start: Int, discriminator: String): BlockId {
        val key = IdentityKey(kind = kind, start = start, discriminator = discriminator)
        val id = reusable[key]?.removeFirstOrNull() ?: nextId++
        allocated += key to id
        return BlockId(raw = id)
    }

    /**
     * Ends one parse. Ids of blocks starting at or after [tailStart] stay reusable by the next parse; the
     * returned ids were reusable in this parse but not reused, so their blocks are gone.
     */
    fun endParse(tailStart: Int): List<BlockId> {
        val removed = ArrayList<BlockId>()
        reusable.values.forEach { ids -> ids.forEach { removed += BlockId(raw = it) } }
        val next = LinkedHashMap<IdentityKey, ArrayDeque<Long>>()
        allocated.forEach { (key, id) ->
            if (key.start >= tailStart) {
                next.getOrPut(key) { ArrayDeque() }.addLast(id)
            }
        }
        reusable = next
        allocated = ArrayList()
        return removed
    }

    fun reset() {
        nextId = 1L
        reusable = LinkedHashMap()
        allocated = ArrayList()
    }

    private data class IdentityKey(
        val kind: String,
        val start: Int,
        val discriminator: String,
    )
}
