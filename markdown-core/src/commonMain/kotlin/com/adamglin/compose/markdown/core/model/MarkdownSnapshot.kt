package com.adamglin.compose.markdown.core.model

import com.adamglin.compose.markdown.core.dialect.MarkdownDialect

@ConsistentCopyVisibility
public data class MarkdownSnapshot internal constructor(
    val version: Long,
    val dialect: MarkdownDialect,
    val document: MarkdownDocument,
    /**
     * End of the stable prefix `[0, stablePrefixEnd)`, whose top-level blocks are final: no later input
     * changes their structure, ranges or ids, and they are never parsed again. Their inline content changes
     * only when a link reference definition later in the document defines a label they use. The prefix ends
     * at a top-level block boundary, never moves backward within a session, and covers the whole source
     * once [isFinal].
     */
    val stablePrefixEnd: Int,
    val isFinal: Boolean,
) {
    init {
        require(version >= 0L) { "version must be non-negative" }
        require(stablePrefixEnd in 0..document.sourceLength) { "stablePrefixEnd must stay within the document" }
    }
}
