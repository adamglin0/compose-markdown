package dev.markstream.core.model

@JvmInline
value class BlockId(val raw: Long)

sealed interface BlockNode {
    val id: BlockId
    val range: TextRange
}

data class PlainTextBlock(
    override val id: BlockId,
    override val range: TextRange,
    val text: String,
) : BlockNode

data class BlockChange(
    val id: BlockId,
    val newIndex: Int,
    val block: BlockNode,
)
