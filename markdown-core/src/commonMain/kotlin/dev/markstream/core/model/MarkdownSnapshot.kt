package dev.markstream.core.model

data class MarkdownSnapshot(
    val version: Long,
    val document: MarkdownDocument,
    val stablePrefixEnd: Int,
    val dirtyRegion: TextRange,
    val isFinal: Boolean,
)

data class ParseDelta(
    val version: Long,
    val changedBlocks: List<BlockChange>,
    val removedBlockIds: List<BlockId>,
    val stablePrefixEnd: Int,
    val dirtyRegion: TextRange,
    val snapshot: MarkdownSnapshot,
)
