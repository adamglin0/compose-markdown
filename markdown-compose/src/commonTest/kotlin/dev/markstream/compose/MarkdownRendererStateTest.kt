package dev.markstream.compose

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.markstream.core.api.MarkdownEngine
import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.model.BlockChange
import dev.markstream.core.model.BlockId
import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.InlineId
import dev.markstream.core.model.InlineNode
import dev.markstream.core.model.LineRange
import dev.markstream.core.model.MarkdownDocument
import dev.markstream.core.model.MarkdownSnapshot
import dev.markstream.core.model.ParseDelta
import dev.markstream.core.model.ParseStats
import dev.markstream.core.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MarkdownRendererStateTest {
    @Test
    fun appendFinishAndResetUpdateSnapshot() {
        val state = MarkdownState(engine = MarkdownEngine())

        assertEquals(0L, state.snapshot.version)
        assertTrue(state.snapshot.document.blocks.isEmpty())

        state.append("hello")
        val block = assertIs<BlockNode.Paragraph>(state.snapshot.document.blocks.single())
        assertEquals("hello", assertIs<InlineNode.Text>(block.children.single()).literal)
        assertFalse(state.snapshot.isFinal)

        state.finish()
        assertTrue(state.snapshot.isFinal)
        assertEquals(5, state.snapshot.stablePrefixEnd)

        state.reset()
        assertEquals(0L, state.snapshot.version)
        assertTrue(state.snapshot.document.blocks.isEmpty())
        assertFalse(state.snapshot.isFinal)
    }

    @Test
    fun applyDeltaReusesUnchangedBlocksAndReplacesChangedOnes() {
        val paragraph1 = paragraph(
            id = 1L,
            text = "keep",
            start = 0,
            end = 4,
        )
        val paragraph2 = paragraph(
            id = 2L,
            text = "before",
            start = 5,
            end = 11,
        )
        val initialSnapshot = snapshot(
            version = 1L,
            blocks = listOf(paragraph1, paragraph2),
            sourceLength = 11,
            isFinal = false,
        )
        val state = MarkdownRendererState(initialSnapshot)
        val initialFirst = state.blocks[0]
        val initialSecond = state.blocks[1]

        val updatedParagraph2 = paragraph(
            id = 2L,
            text = "after",
            start = 5,
            end = 10,
        )
        val paragraph3 = paragraph(
            id = 3L,
            text = "new",
            start = 11,
            end = 14,
        )
        val nextSnapshot = snapshot(
            version = 2L,
            blocks = listOf(paragraph1, updatedParagraph2, paragraph3),
            sourceLength = 14,
            isFinal = false,
        )

        state.apply(
            ParseDelta(
                version = 2L,
                changedBlocks = listOf(
                    BlockChange(
                        id = BlockId(2L),
                        oldIndex = 1,
                        newIndex = 1,
                        block = updatedParagraph2,
                    ),
                    BlockChange(
                        id = BlockId(3L),
                        oldIndex = null,
                        newIndex = 2,
                        block = paragraph3,
                    ),
                ),
                insertedBlockIds = listOf(BlockId(3L)),
                updatedBlockIds = listOf(BlockId(2L)),
                removedBlockIds = emptyList(),
                stablePrefixRange = TextRange(0, 5),
                dirtyRegion = TextRange(5, 14),
                snapshot = nextSnapshot,
                stats = ParseStats.Empty,
                hasStateChange = true,
            ),
        )

        assertEquals(listOf(1L, 2L, 3L), state.blocks.map { it.id.raw })
        assertSame(initialFirst, state.blocks[0])
        assertTrue(initialSecond !== state.blocks[1])
        assertEquals("after", (state.blocks[1].block as BlockNode.Paragraph).children.inlineText())
    }

    @Test
    fun applyDeltaRemovesDeletedBlocks() {
        val paragraph1 = paragraph(id = 1L, text = "one", start = 0, end = 3)
        val paragraph2 = paragraph(id = 2L, text = "two", start = 4, end = 7)
        val state = MarkdownRendererState(
            initialSnapshot = snapshot(
                version = 1L,
                blocks = listOf(paragraph1, paragraph2),
                sourceLength = 7,
                isFinal = false,
            ),
        )

        val nextSnapshot = snapshot(
            version = 2L,
            blocks = listOf(paragraph2),
            sourceLength = 3,
            isFinal = true,
        )

        state.apply(
            ParseDelta(
                version = 2L,
                changedBlocks = emptyList(),
                insertedBlockIds = emptyList(),
                updatedBlockIds = emptyList(),
                removedBlockIds = listOf(BlockId(1L)),
                stablePrefixRange = TextRange.Empty,
                dirtyRegion = TextRange(0, 3),
                snapshot = nextSnapshot,
                stats = ParseStats.Empty,
                hasStateChange = true,
            ),
        )

        assertEquals(listOf(2L), state.blocks.map { it.id.raw })
    }

    @Test
    fun inlineNodesMapToAnnotatedStringWithLinkAnnotations() {
        val annotatedString = listOf(
            InlineNode.Text(id = InlineId(1L), range = TextRange(0, 6), literal = "hello "),
            InlineNode.Link(
                id = InlineId(2L),
                range = TextRange(6, 10),
                destination = "https://example.com",
                title = null,
                children = listOf(
                    InlineNode.Strong(
                        id = InlineId(3L),
                        range = TextRange(6, 10),
                        children = listOf(
                            InlineNode.Text(id = InlineId(4L), range = TextRange(6, 10), literal = "docs"),
                        ),
                    ),
                ),
            ),
        ).toAnnotatedString(
            styles = MarkdownInlineStyles(
                emphasis = SpanStyle(fontStyle = FontStyle.Italic),
                strong = SpanStyle(fontWeight = FontWeight.Bold),
                strike = SpanStyle(textDecoration = TextDecoration.LineThrough),
                code = SpanStyle(),
                link = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline)),
            ),
        )

        assertEquals("hello docs", annotatedString.text)
        val link = assertIs<LinkAnnotation.Url>(annotatedString.getLinkAnnotations(6, 10).single().item)
        assertEquals("https://example.com", link.url)
    }
}

private fun snapshot(
    version: Long,
    blocks: List<BlockNode>,
    sourceLength: Int,
    isFinal: Boolean,
): MarkdownSnapshot = MarkdownSnapshot(
    version = version,
    dialect = MarkdownDialect.ChatFast,
    document = MarkdownDocument(
        sourceLength = sourceLength,
        lineCount = blocks.size,
        blocks = blocks,
    ),
    stablePrefixRange = TextRange(0, sourceLength),
    dirtyRegion = if (isFinal) TextRange.Empty else TextRange(0, sourceLength),
    isFinal = isFinal,
)

private fun paragraph(
    id: Long,
    text: String,
    start: Int,
    end: Int,
): BlockNode.Paragraph = BlockNode.Paragraph(
    id = BlockId(id),
    range = TextRange(start, end),
    lineRange = LineRange(0, 1),
    children = listOf(
        InlineNode.Text(
            id = InlineId(id),
            range = TextRange(start, end),
            literal = text,
        ),
    ),
)

private fun List<InlineNode>.inlineText(): String = joinToString(separator = "") { node ->
    when (node) {
        is InlineNode.CodeSpan -> node.literal
        is InlineNode.Emphasis -> node.children.inlineText()
        is InlineNode.HardBreak -> "\n"
        is InlineNode.Image -> node.alt.inlineText()
        is InlineNode.Link -> node.children.inlineText()
        is InlineNode.SoftBreak -> "\n"
        is InlineNode.Strikethrough -> node.children.inlineText()
        is InlineNode.Strong -> node.children.inlineText()
        is InlineNode.Text -> node.literal
        is InlineNode.UnsupportedInline -> node.literal
    }
}
