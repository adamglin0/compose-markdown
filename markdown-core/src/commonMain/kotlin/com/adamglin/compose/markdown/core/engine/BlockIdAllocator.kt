package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.block.BlockIdSource
import com.adamglin.compose.markdown.core.model.BlockId

/**
 * Hands out block ids so a block keeps its id while it is rebuilt. Blocks that may still change are built
 * on every call and ask for an id with an identity key (kind, start offset, depth, discriminator such as a
 * heading level); a key requested in the previous call gets the same id again. A final block asks once
 * and keeps its id for good.
 */
internal class BlockIdAllocator : BlockIdSource {
    private var nextId = 1L
    private var previous = HashMap<IdentityKey, Long>()
    private var current = HashMap<IdentityKey, Long>()

    override fun allocate(kind: String, start: Int, depth: Int, discriminator: String, isFinal: Boolean): BlockId {
        val key = IdentityKey(kind = kind, start = start, depth = depth, discriminator = discriminator)
        val id = previous.remove(key) ?: nextId++
        if (!isFinal) {
            current[key] = id
        }
        return BlockId(raw = id)
    }

    /** Ends one call and returns the ids handed out in the previous call that were not requested again. */
    fun endCall(): List<BlockId> {
        val removed = previous.values.map(::BlockId)
        previous = current
        current = HashMap()
        return removed
    }

    fun reset() {
        nextId = 1L
        previous = HashMap()
        current = HashMap()
    }

    private data class IdentityKey(
        val kind: String,
        val start: Int,
        val depth: Int,
        val discriminator: String,
    )
}
