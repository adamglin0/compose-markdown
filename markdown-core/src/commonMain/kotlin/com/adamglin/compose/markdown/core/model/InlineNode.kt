package com.adamglin.compose.markdown.core.model

public sealed interface InlineNode {
    public val id: InlineId
    public val range: TextRange

    @ConsistentCopyVisibility
    public data class Text internal constructor(
        override val id: InlineId,
        override val range: TextRange,
        val literal: String,
    ) : InlineNode

    @ConsistentCopyVisibility
    public data class Emphasis internal constructor(
        override val id: InlineId,
        override val range: TextRange,
        val children: List<InlineNode>,
    ) : InlineNode

    @ConsistentCopyVisibility
    public data class Strong internal constructor(
        override val id: InlineId,
        override val range: TextRange,
        val children: List<InlineNode>,
    ) : InlineNode

    @ConsistentCopyVisibility
    public data class CodeSpan internal constructor(
        override val id: InlineId,
        override val range: TextRange,
        val literal: String,
    ) : InlineNode

    @ConsistentCopyVisibility
    public data class Link internal constructor(
        override val id: InlineId,
        override val range: TextRange,
        val destination: String,
        val title: String?,
        val children: List<InlineNode>,
        val referenceLabel: String? = null,
    ) : InlineNode

    @ConsistentCopyVisibility
    public data class Image internal constructor(
        override val id: InlineId,
        override val range: TextRange,
        val destination: String,
        val title: String?,
        val alt: List<InlineNode>,
        val referenceLabel: String? = null,
    ) : InlineNode

    @ConsistentCopyVisibility
    public data class SoftBreak internal constructor(
        override val id: InlineId,
        override val range: TextRange,
    ) : InlineNode

    @ConsistentCopyVisibility
    public data class HardBreak internal constructor(
        override val id: InlineId,
        override val range: TextRange,
    ) : InlineNode

    @ConsistentCopyVisibility
    public data class Strikethrough internal constructor(
        override val id: InlineId,
        override val range: TextRange,
        val children: List<InlineNode>,
    ) : InlineNode

    @ConsistentCopyVisibility
    public data class MathSpan internal constructor(
        override val id: InlineId,
        override val range: TextRange,
        val latex: String,
        val delimiter: MathInlineDelimiter,
    ) : InlineNode
}

public enum class MathInlineDelimiter {
    Dollar,
    Paren,
}
