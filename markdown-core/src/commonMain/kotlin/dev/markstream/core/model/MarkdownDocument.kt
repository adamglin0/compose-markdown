package dev.markstream.core.model

data class MarkdownDocument(
    val sourceLength: Int,
    val blocks: List<BlockNode>,
)
