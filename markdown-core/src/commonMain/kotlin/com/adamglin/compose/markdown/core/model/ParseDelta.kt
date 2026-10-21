package com.adamglin.compose.markdown.core.model

import androidx.compose.runtime.Immutable

/**
 * The change one [com.adamglin.compose.markdown.core.api.MarkdownEngine.append] or `finish` call made.
 *
 * Applying [removedBlockIds] and [changedBlocks] to the previous top-level block list, then ordering by
 * [snapshot], yields `snapshot.document.blocks`. Top-level blocks that are not listed are the same
 * instances as in the previous snapshot.
 */
data class ParseDelta(
    val version: Long,
    /** Top-level blocks that are new, moved, or whose content changed, in document order. */
    val changedBlocks: List<BlockChange>,
    /** Ids of blocks (at any depth) present in the previous snapshot and absent from this one. */
    val removedBlockIds: List<BlockId>,
    /** The source range this call re-parsed: from the end of the previous stable prefix to the end of input. */
    val dirtyRegion: TextRange,
    val snapshot: MarkdownSnapshot,
    val stats: ParseStats,
) {
    init {
        require(version >= 0L) { "version must be non-negative" }
    }
}

/** Work counters for one engine call. */
@Immutable
data class ParseStats(
    /** Characters added to the source by this call, after line ending normalization. */
    val appendedChars: Int,
    /** Source lines the block parser processed. */
    val processedLines: Int,
    /** Top-level blocks produced by the block parser, plus stable blocks whose inline content was resolved again. */
    val reparsedBlocks: Int,
    /** Stable top-level blocks carried over untouched. */
    val preservedBlocks: Int,
    /** Blocks (at any depth) whose inline content was parsed. */
    val inlineParsedBlockCount: Int,
    /** Blocks (at any depth) whose inline content was reused from the previous call. */
    val inlineCacheHitBlockCount: Int,
) {
    init {
        require(appendedChars >= 0) { "appendedChars must be non-negative" }
        require(processedLines >= 0) { "processedLines must be non-negative" }
        require(reparsedBlocks >= 0) { "reparsedBlocks must be non-negative" }
        require(preservedBlocks >= 0) { "preservedBlocks must be non-negative" }
        require(inlineParsedBlockCount >= 0) { "inlineParsedBlockCount must be non-negative" }
        require(inlineCacheHitBlockCount >= 0) { "inlineCacheHitBlockCount must be non-negative" }
    }

    companion object {
        val Empty = ParseStats(
            appendedChars = 0,
            processedLines = 0,
            reparsedBlocks = 0,
            preservedBlocks = 0,
            inlineParsedBlockCount = 0,
            inlineCacheHitBlockCount = 0,
        )
    }
}
