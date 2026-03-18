package dev.markstream.core.model

data class MarkdownDocument(
    val sourceLength: Int,
    val lineCount: Int,
    val blocks: List<BlockNode>,
) {
    init {
        require(sourceLength >= 0) { "sourceLength must be non-negative" }
        require(lineCount >= 0) { "lineCount must be non-negative" }
    }

    val sourceRange: TextRange
        get() = TextRange(start = 0, endExclusive = sourceLength)

    val lineRange: LineRange
        get() = LineRange(startLine = 0, endLineExclusive = lineCount)

    val root: BlockNode.Document
        get() = BlockNode.Document(
            id = BlockId.Document,
            range = sourceRange,
            lineRange = lineRange,
            children = blocks,
        )
}
