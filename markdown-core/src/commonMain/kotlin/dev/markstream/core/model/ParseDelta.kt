package dev.markstream.core.model

data class ParseDelta(
    val version: Long,
    val changedBlocks: List<BlockChange>,
    val insertedBlockIds: List<BlockId>,
    val updatedBlockIds: List<BlockId>,
    val removedBlockIds: List<BlockId>,
    val stablePrefixRange: TextRange,
    val dirtyRegion: TextRange,
    val snapshot: MarkdownSnapshot,
    val stats: ParseStats,
    val hasStateChange: Boolean,
) {
    init {
        require(version >= 0L) { "version must be non-negative" }
    }

    val stablePrefixEnd: Int
        get() = stablePrefixRange.endExclusive

    val isNoOp: Boolean
        get() = !hasStateChange && changedBlocks.isEmpty() && removedBlockIds.isEmpty() && dirtyRegion.isEmpty
}

data class ParseStats(
    val parsedBlockCount: Int,
    val changedBlockCount: Int,
    val reusedBlockCount: Int,
    val inlineParsedBlockCount: Int,
    val inlineCacheHitBlockCount: Int,
    val appendedChars: Int,
    val processedLines: Int,
    val reparsedBlocks: Int,
    val preservedBlocks: Int,
    val fallbackCount: Int,
    val fallbackReason: String?,
) {
    init {
        require(parsedBlockCount >= 0) { "parsedBlockCount must be non-negative" }
        require(changedBlockCount >= 0) { "changedBlockCount must be non-negative" }
        require(reusedBlockCount >= 0) { "reusedBlockCount must be non-negative" }
        require(inlineParsedBlockCount >= 0) { "inlineParsedBlockCount must be non-negative" }
        require(inlineCacheHitBlockCount >= 0) { "inlineCacheHitBlockCount must be non-negative" }
        require(appendedChars >= 0) { "appendedChars must be non-negative" }
        require(processedLines >= 0) { "processedLines must be non-negative" }
        require(reparsedBlocks >= 0) { "reparsedBlocks must be non-negative" }
        require(preservedBlocks >= 0) { "preservedBlocks must be non-negative" }
        require(fallbackCount >= 0) { "fallbackCount must be non-negative" }
    }

    companion object {
        val Empty = ParseStats(
            parsedBlockCount = 0,
            changedBlockCount = 0,
            reusedBlockCount = 0,
            inlineParsedBlockCount = 0,
            inlineCacheHitBlockCount = 0,
            appendedChars = 0,
            processedLines = 0,
            reparsedBlocks = 0,
            preservedBlocks = 0,
            fallbackCount = 0,
            fallbackReason = null,
        )
    }
}
