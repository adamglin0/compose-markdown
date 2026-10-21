package com.adamglin.compose.markdown.core.model

import com.adamglin.compose.markdown.core.dialect.MarkdownDialect

public data class MarkdownSnapshot(
    val version: Long,
    val dialect: MarkdownDialect,
    val document: MarkdownDocument,
    /**
     * Source prefix whose top-level blocks are final: no later input changes their structure, ranges or
     * ids, and they are never parsed again. Their inline content changes only when a link reference
     * definition later in the document defines a label they use. The prefix starts at 0, ends at a
     * top-level block boundary, never moves backward within a session, and covers the whole source once
     * [isFinal].
     */
    val stablePrefixRange: TextRange,
    val isFinal: Boolean,
) {
    init {
        require(version >= 0L) { "version must be non-negative" }
        require(stablePrefixRange.endExclusive <= document.sourceLength) {
            "stablePrefixRange must stay within the document"
        }
    }

    public val stablePrefixEnd: Int
        get() = stablePrefixRange.endExclusive
}
