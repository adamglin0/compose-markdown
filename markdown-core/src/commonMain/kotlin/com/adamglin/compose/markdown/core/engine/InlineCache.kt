package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.block.InlineResolver
import com.adamglin.compose.markdown.core.block.ResolvedInline
import com.adamglin.compose.markdown.core.inline.InlineParser
import com.adamglin.compose.markdown.core.model.BlockId
import com.adamglin.compose.markdown.core.model.TextRange

/**
 * Inline parse results of the leaves built in the previous call, reused while a leaf keeps its id, raw
 * text and range and the link reference definitions stay unchanged ([referenceRevision]). Leaves that are
 * not resolved in a call drop out of the cache.
 */
internal class InlineCache(
    private val inlineParser: InlineParser,
) : InlineResolver {
    private var previous = HashMap<Long, Entry>()
    private var current = HashMap<Long, Entry>()

    /** Moves on whenever the definition in effect for some label changes. */
    var referenceRevision: Int = 0
        private set

    var parsedCount: Int = 0
        private set
    var hitCount: Int = 0
        private set

    override fun resolve(id: BlockId, literal: String, range: TextRange): ResolvedInline {
        val cached = current[id.raw] ?: previous[id.raw]
        val entry = if (
            cached != null &&
            cached.referenceRevision == referenceRevision &&
            cached.range == range &&
            cached.literal == literal
        ) {
            hitCount += 1
            cached
        } else {
            parsedCount += 1
            val parsed = inlineParser.parse(literal = literal, range = range)
            Entry(
                literal = literal,
                range = range,
                referenceRevision = referenceRevision,
                resolved = ResolvedInline(nodes = parsed.nodes, labels = parsed.referenceLabels),
            )
        }
        current[id.raw] = entry
        return entry.resolved
    }

    fun definitionsChanged() {
        referenceRevision += 1
    }

    /** Ends one call: only the entries resolved in it stay cached. */
    fun endCall() {
        previous = current
        current = HashMap()
        parsedCount = 0
        hitCount = 0
    }

    fun clear() {
        previous = HashMap()
        current = HashMap()
        referenceRevision = 0
        parsedCount = 0
        hitCount = 0
    }

    private class Entry(
        val literal: String,
        val range: TextRange,
        val referenceRevision: Int,
        val resolved: ResolvedInline,
    )
}
