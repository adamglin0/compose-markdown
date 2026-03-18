package dev.markstream.core.internal

import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.InlineNode
import dev.markstream.core.model.MarkdownSnapshot
import dev.markstream.core.model.TextRange
import dev.markstream.core.source.LineIndex
import dev.markstream.core.source.SourceBuffer

internal class EngineSessionState(
    val dialect: MarkdownDialect,
    var snapshot: MarkdownSnapshot,
) {
    val source: SourceBuffer = SourceBuffer()
    val openBlockStack: MutableList<OpenBlockFrame> = mutableListOf()
    val lineIndex: LineIndex = LineIndex()
    val cacheState: ParseCacheState = ParseCacheState()
    var version: Long = snapshot.version
    var stablePrefixEnd: Int = snapshot.stablePrefixEnd
    var mutableTailStart: Int = snapshot.stablePrefixEnd
    var isFinal: Boolean = snapshot.isFinal
    var nextBlockId: Long = 1L
    var suppressLeadingLineFeed: Boolean = false
}

internal data class OpenBlockFrame(
    val marker: String,
    val startOffset: Int,
)

internal class ParseCacheState {
    val blockRecords: MutableList<CachedBlockRecord> = mutableListOf()
    val blockIdsByKey: MutableMap<BlockIdentityKey, MutableList<Long>> = linkedMapOf()
    val inlineByBlockId: MutableMap<Long, InlineCacheEntry> = linkedMapOf()
}

internal data class CachedBlockRecord(
    val block: BlockNode,
    val isStable: Boolean,
)

internal data class BlockIdentityKey(
    val kind: String,
    val start: Int,
    val discriminator: String,
)

internal data class InlineCacheKey(
    val range: TextRange,
    val literalHash: Int,
)

internal data class InlineCacheEntry(
    val key: InlineCacheKey,
    val nodes: List<InlineNode>,
)
