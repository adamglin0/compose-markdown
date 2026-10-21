package com.adamglin.compose.markdown.core.model

public sealed interface BlockNode {
    public val id: BlockId
    public val range: TextRange
    public val lineRange: LineRange

    @ConsistentCopyVisibility
    public data class Paragraph internal constructor(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val children: List<InlineNode>,
    ) : BlockNode

    @ConsistentCopyVisibility
    public data class Heading internal constructor(
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

    @ConsistentCopyVisibility
    public data class FencedCodeBlock internal constructor(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val infoString: String?,
        val languageHint: String?,
        val literal: String,
        val isClosed: Boolean,
    ) : BlockNode

    /** A code block made of lines indented by four or more columns; it has no info string. */
    @ConsistentCopyVisibility
    public data class IndentedCodeBlock internal constructor(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val literal: String,
    ) : BlockNode

    @ConsistentCopyVisibility
    public data class BlockQuote internal constructor(
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
    @ConsistentCopyVisibility
    public data class ListBlock internal constructor(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val type: ListType,
        val items: List<ListItem>,
        val isLoose: Boolean,
    ) : BlockNode

    @ConsistentCopyVisibility
    public data class ListItem internal constructor(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val children: List<BlockNode>,
        val taskState: TaskState? = null,
    ) : BlockNode

    @ConsistentCopyVisibility
    public data class TableBlock internal constructor(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val header: TableRow,
        val alignments: List<TableAlignment>,
        val rows: List<TableRow>,
    ) : BlockNode

    @ConsistentCopyVisibility
    public data class TableRow internal constructor(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val cells: List<TableCell>,
        val isHeader: Boolean,
    ) : BlockNode

    @ConsistentCopyVisibility
    public data class TableCell internal constructor(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val children: List<InlineNode>,
    ) : BlockNode

    @ConsistentCopyVisibility
    public data class ThematicBreak internal constructor(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val marker: String,
    ) : BlockNode

    @ConsistentCopyVisibility
    public data class MathBlock internal constructor(
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
    @ConsistentCopyVisibility
    public data class Bullet internal constructor(val bulletChar: Char) : ListType

    /** An ordered list starting at [startNumber]; [delimiter] is `.` or `)`. */
    @ConsistentCopyVisibility
    public data class Ordered internal constructor(val startNumber: Int, val delimiter: Char) : ListType
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
