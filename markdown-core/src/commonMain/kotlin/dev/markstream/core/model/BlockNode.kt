package dev.markstream.core.model

sealed interface BlockNode {
    val id: BlockId
    val range: TextRange
    val lineRange: LineRange

    data class Document(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val children: List<BlockNode>,
    ) : BlockNode

    data class Paragraph(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val children: List<InlineNode>,
    ) : BlockNode

    data class Heading(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val level: Int,
        val children: List<InlineNode>,
        val style: HeadingStyle = HeadingStyle.Atx,
    ) : BlockNode {
        init {
            require(level in 1..6) { "level must be between 1 and 6" }
        }
    }

    data class FencedCodeBlock(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val infoString: String?,
        val literal: String,
        val isClosed: Boolean,
    ) : BlockNode

    data class BlockQuote(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val children: List<BlockNode>,
    ) : BlockNode

    data class ListBlock(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val style: ListStyle,
        val items: List<ListItem>,
        val isLoose: Boolean,
    ) : BlockNode

    data class ListItem(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val marker: String,
        val children: List<BlockNode>,
        val taskState: TaskState? = null,
    ) : BlockNode

    data class TableBlock(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val header: TableRow,
        val alignments: List<TableAlignment>,
        val rows: List<TableRow>,
    ) : BlockNode

    data class TableRow(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val cells: List<TableCell>,
        val isHeader: Boolean,
    ) : BlockNode

    data class TableCell(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val children: List<InlineNode>,
    ) : BlockNode

    data class ThematicBreak(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val marker: String,
    ) : BlockNode

    data class UnsupportedBlock(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val literal: String,
        val reason: String? = null,
    ) : BlockNode

    data class RawTextBlock(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val literal: String,
    ) : BlockNode
}

enum class ListStyle {
    Unordered,
    Ordered,
}

enum class HeadingStyle {
    Atx,
    Setext,
}

enum class TaskState {
    Checked,
    Unchecked,
}

enum class TableAlignment {
    Default,
    Left,
    Center,
    Right,
}

data class BlockChange(
    val id: BlockId,
    val oldIndex: Int?,
    val newIndex: Int,
    val block: BlockNode,
)
