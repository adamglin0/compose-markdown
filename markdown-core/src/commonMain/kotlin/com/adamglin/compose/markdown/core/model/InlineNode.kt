package com.adamglin.compose.markdown.core.model

public sealed interface InlineNode {
    public val id: InlineId
    public val range: TextRange

    public data class Text(
        override val id: InlineId,
        override val range: TextRange,
        val literal: String,
    ) : InlineNode

    public data class Emphasis(
        override val id: InlineId,
        override val range: TextRange,
        val children: List<InlineNode>,
    ) : InlineNode

    public data class Strong(
        override val id: InlineId,
        override val range: TextRange,
        val children: List<InlineNode>,
    ) : InlineNode

    public data class CodeSpan(
        override val id: InlineId,
        override val range: TextRange,
        val literal: String,
    ) : InlineNode

    public data class Link(
        override val id: InlineId,
        override val range: TextRange,
        val destination: String,
        val title: String?,
        val children: List<InlineNode>,
        val referenceLabel: String? = null,
    ) : InlineNode

    public data class Image(
        override val id: InlineId,
        override val range: TextRange,
        val destination: String,
        val title: String?,
        val alt: List<InlineNode>,
        val referenceLabel: String? = null,
    ) : InlineNode

    public data class SoftBreak(
        override val id: InlineId,
        override val range: TextRange,
    ) : InlineNode

    public data class HardBreak(
        override val id: InlineId,
        override val range: TextRange,
    ) : InlineNode

    public data class Strikethrough(
        override val id: InlineId,
        override val range: TextRange,
        val children: List<InlineNode>,
    ) : InlineNode

    public data class UnsupportedInline(
        override val id: InlineId,
        override val range: TextRange,
        val literal: String,
        val reason: String? = null,
    ) : InlineNode

    public data class MathSpan(
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
