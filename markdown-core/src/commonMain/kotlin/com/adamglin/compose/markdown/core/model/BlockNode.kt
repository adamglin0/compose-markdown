package com.adamglin.compose.markdown.core.model

public sealed interface BlockNode {
    public val id: BlockId
    public val range: TextRange
    public val lineRange: LineRange

    public data class Paragraph(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val children: List<InlineNode>,
    ) : BlockNode

    public data class Heading(
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

    public data class FencedCodeBlock(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val infoString: String?,
        val languageHint: String?,
        val literal: String,
        val isClosed: Boolean,
    ) : BlockNode

    /** A code block made of lines indented by four or more columns; it has no info string. */
    public data class IndentedCodeBlock(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val literal: String,
    ) : BlockNode

    public data class BlockQuote(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val children: List<BlockNode>,
    ) : BlockNode

    /**
     * A bullet or ordered list. [type] carries the list marker (bullet character, or start number and
     * delimiter); a list is loose when any of its items are separated by blank lines or any item directly
     * contains two block children with a blank line between them.
     */
    public data class ListBlock(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val type: ListType,
        val items: List<ListItem>,
        val isLoose: Boolean,
    ) : BlockNode

    public data class ListItem(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val children: List<BlockNode>,
        val taskState: TaskState? = null,
    ) : BlockNode

    public data class TableBlock(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val header: TableRow,
        val alignments: List<TableAlignment>,
        val rows: List<TableRow>,
    ) : BlockNode

    public data class TableRow(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val cells: List<TableCell>,
        val isHeader: Boolean,
    ) : BlockNode

    public data class TableCell(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val children: List<InlineNode>,
    ) : BlockNode

    public data class ThematicBreak(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val marker: String,
    ) : BlockNode

    public data class MathBlock(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val latex: String,
        val isClosed: Boolean,
        val delimiter: MathBlockDelimiter,
    ) : BlockNode
}

public sealed interface ListType {
    /** A bullet list; [bulletChar] is `-`, `+` or `*`. */
    public data class Bullet(val bulletChar: Char) : ListType

    /** An ordered list starting at [startNumber]; [delimiter] is `.` or `)`. */
    public data class Ordered(val startNumber: Int, val delimiter: Char) : ListType
}

public enum class HeadingStyle {
    Atx,
    Setext,
}

public enum class TaskState {
    Checked,
    Unchecked,
}

public enum class TableAlignment {
    Default,
    Left,
    Center,
    Right,
}

public enum class MathBlockDelimiter {
    Dollar,
    Bracket,
}

@ConsistentCopyVisibility
public data class BlockChange internal constructor(
    val id: BlockId,
    val oldIndex: Int?,
    val newIndex: Int,
    val block: BlockNode,
)
