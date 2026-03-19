package com.adamglin.compose.markdown.core.model

import com.adamglin.compose.markdown.core.dialect.MarkdownDialect

data class MarkdownSnapshot(
    val version: Long,
    val dialect: MarkdownDialect,
    val document: MarkdownDocument,
    val stablePrefixRange: TextRange,
    val dirtyRegion: TextRange,
    val isFinal: Boolean,
) {
    init {
        require(version >= 0L) { "version must be non-negative" }
        require(stablePrefixRange.endExclusive <= document.sourceLength) {
            "stablePrefixRange must stay within the document"
        }
        require(dirtyRegion.endExclusive <= document.sourceLength) {
            "dirtyRegion must stay within the document"
        }
    }

    val stablePrefixEnd: Int
        get() = stablePrefixRange.endExclusive
}
