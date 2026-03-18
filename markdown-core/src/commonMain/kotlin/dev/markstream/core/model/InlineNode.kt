package dev.markstream.core.model

sealed interface InlineNode {
    val id: InlineId
    val range: TextRange

    data class Text(
        override val id: InlineId,
        override val range: TextRange,
        val literal: String,
    ) : InlineNode

    data class Emphasis(
        override val id: InlineId,
        override val range: TextRange,
        val children: List<InlineNode>,
    ) : InlineNode

    data class Strong(
        override val id: InlineId,
        override val range: TextRange,
        val children: List<InlineNode>,
    ) : InlineNode

    data class CodeSpan(
        override val id: InlineId,
        override val range: TextRange,
        val literal: String,
    ) : InlineNode

    data class Link(
        override val id: InlineId,
        override val range: TextRange,
        val destination: String,
        val title: String?,
        val children: List<InlineNode>,
        val referenceLabel: String? = null,
    ) : InlineNode

    data class Image(
        override val id: InlineId,
        override val range: TextRange,
        val destination: String,
        val title: String?,
        val alt: List<InlineNode>,
        val referenceLabel: String? = null,
    ) : InlineNode

    data class SoftBreak(
        override val id: InlineId,
        override val range: TextRange,
    ) : InlineNode

    data class HardBreak(
        override val id: InlineId,
        override val range: TextRange,
    ) : InlineNode

    data class Strikethrough(
        override val id: InlineId,
        override val range: TextRange,
        val children: List<InlineNode>,
    ) : InlineNode

    data class UnsupportedInline(
        override val id: InlineId,
        override val range: TextRange,
        val literal: String,
        val reason: String? = null,
    ) : InlineNode
}
