package com.adamglin.compose.markdown.core.model

/** The parsed document at one snapshot: its top-level [blocks] and the size of the source they cover. */
@ConsistentCopyVisibility
public data class MarkdownDocument internal constructor(
    val sourceLength: Int,
    val lineCount: Int,
    val blocks: List<BlockNode>,
) {
    init {
        require(sourceLength >= 0) { "sourceLength must be non-negative" }
        require(lineCount >= 0) { "lineCount must be non-negative" }
    }
}
