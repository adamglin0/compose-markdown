package com.adamglin.compose.markdown.core.internal

import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.InlineNode
import com.adamglin.compose.markdown.core.model.MarkdownSnapshot
import com.adamglin.compose.markdown.core.model.TextRange
import com.adamglin.compose.markdown.core.source.LineIndex
import com.adamglin.compose.markdown.core.source.SourceBuffer

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
    val dependencyIndex: DependencyIndex = DependencyIndex()
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

internal class DependencyIndex {
    val definitionsByLabel: MutableMap<String, LinkReferenceDefinition> = linkedMapOf()
    val dependentBlocksByLabel: MutableMap<String, MutableSet<Long>> = linkedMapOf()
    val referenceLabelsByBlockId: MutableMap<Long, Set<String>> = linkedMapOf()
    val unresolvedBlocksByLabel: MutableMap<String, MutableSet<Long>> = linkedMapOf()
    val unresolvedLabelsByBlockId: MutableMap<Long, Set<String>> = linkedMapOf()

    fun reset() {
        definitionsByLabel.clear()
        dependentBlocksByLabel.clear()
        referenceLabelsByBlockId.clear()
        unresolvedBlocksByLabel.clear()
        unresolvedLabelsByBlockId.clear()
    }
}

internal data class LinkReferenceDefinition(
    val label: String,
    val destination: String,
    val title: String?,
    val range: TextRange,
)

internal data class CachedBlockRecord(
    val block: BlockNode,
    val isStable: Boolean,
    val referenceLabels: Set<String>,
    val unresolvedReferenceLabels: Set<String>,
)

internal data class BlockIdentityKey(
    val kind: String,
    val start: Int,
    val discriminator: String,
)

internal data class InlineCacheKey(
    val range: TextRange,
    val literalHash: Int,
    val referenceRevision: Int,
)

internal data class InlineCacheEntry(
    val key: InlineCacheKey,
    val nodes: List<InlineNode>,
    val referenceLabels: Set<String>,
    val unresolvedReferenceLabels: Set<String>,
)
