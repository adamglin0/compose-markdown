package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.block.InlineResolver
import com.adamglin.compose.markdown.core.block.ResolvedInline
import com.adamglin.compose.markdown.core.inline.InlineCheckpoint
import com.adamglin.compose.markdown.core.inline.InlineParser
import com.adamglin.compose.markdown.core.model.BlockId
import com.adamglin.compose.markdown.core.model.TextRange

/**
 * Inline parse results of the leaves built in the previous call, reused while a leaf keeps its id, raw
 * text and range and the link reference definitions stay unchanged ([referenceRevision]). Leaves that are
 * not resolved in a call drop out of the cache.
 *
 * A growing open paragraph keeps its id and start, and its lines before the last one are complete, so its
 * new text starts with the text of the previous call up to the parse's last checkpoint (a line start, see
 * [InlineParser.parse]); it is parsed again from there only.
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
            val resume = cached?.checkpoint?.takeIf {
                cached.referenceRevision == referenceRevision && cached.range.start == range.start && it.offset < literal.length
            }
            val parsed = inlineParser.parse(literal = literal, range = range, resume = resume)
            Entry(
                literal = literal,
                range = range,
                referenceRevision = referenceRevision,
                resolved = ResolvedInline(nodes = parsed.nodes, labels = parsed.referenceLabels),
                checkpoint = parsed.checkpoint,
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
        val checkpoint: InlineCheckpoint?,
    )
}
