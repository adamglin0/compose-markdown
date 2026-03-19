package com.adamglin.compose.markdown.core.api

import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.InlineNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InlineParserMvpTest {
    @Test
    fun parsesEmphasisStrongAndStrikethrough() {
        val paragraph = appendSingleParagraph("a *em* **strong** ~~strike~~")

        assertTrue(paragraph.children.any { it is InlineNode.Emphasis })
        assertTrue(paragraph.children.any { it is InlineNode.Strong })
        assertTrue(paragraph.children.any { it is InlineNode.Strikethrough })
    }

    @Test
    fun parsesNestedEmphasisCommonCase() {
        val paragraph = appendSingleParagraph("**a *b* c**")

        val strong = paragraph.children.filterIsInstance<InlineNode.Strong>().single()
        assertTrue(strong.children.any { it is InlineNode.Emphasis })
    }

    @Test
    fun parsesCodeSpanWithoutParsingInnerMarkers() {
        val paragraph = appendSingleParagraph("before `*not emphasis*` after")

        val codeSpan = paragraph.children.filterIsInstance<InlineNode.CodeSpan>().single()
        assertEquals("*not emphasis*", codeSpan.literal)
    }

    @Test
    fun parsesInlineLink() {
        val paragraph = appendSingleParagraph("go to [site](https://example.com/path)")

        val link = paragraph.children.filterIsInstance<InlineNode.Link>().single()
        assertEquals("https://example.com/path", link.destination)
        assertEquals("site", link.children.inlineLiteral())
    }

    @Test
    fun inlineNodeRangesAreCorrectForCommonMarkers() {
        val paragraph = appendSingleParagraph("*em* **strong** `code` [link](https://e.dev) ~~del~~")

        val emphasis = paragraph.children.filterIsInstance<InlineNode.Emphasis>().single()
        assertEquals(0, emphasis.range.start)
        assertEquals(4, emphasis.range.endExclusive)

        val strong = paragraph.children.filterIsInstance<InlineNode.Strong>().single()
        assertEquals(5, strong.range.start)
        assertEquals(15, strong.range.endExclusive)

        val code = paragraph.children.filterIsInstance<InlineNode.CodeSpan>().single()
        assertEquals(16, code.range.start)
        assertEquals(22, code.range.endExclusive)

        val link = paragraph.children.filterIsInstance<InlineNode.Link>().single()
        assertEquals(23, link.range.start)
        assertEquals(44, link.range.endExclusive)

        val strike = paragraph.children.filterIsInstance<InlineNode.Strikethrough>().single()
        assertEquals(45, strike.range.start)
        assertEquals(52, strike.range.endExclusive)
    }

    @Test
    fun parsesAutolinkAndBareUrl() {
        val paragraph = appendSingleParagraph("<https://a.dev> and https://b.dev/docs")

        val links = paragraph.children.filterIsInstance<InlineNode.Link>()
        assertEquals(2, links.size)
        assertEquals("https://a.dev", links[0].destination)
        assertEquals("https://b.dev/docs", links[1].destination)
    }

    @Test
    fun parsesHardAndSoftBreaks() {
        val paragraph = appendSingleParagraph("a  \nb\\\nc\nd")

        assertEquals(2, paragraph.children.count { it is InlineNode.HardBreak })
        assertEquals(1, paragraph.children.count { it is InlineNode.SoftBreak })
    }

    @Test
    fun inlineBreakRangesTrackOriginalSourceOffsets() {
        val paragraph = appendSingleParagraph("a\nb  \nc\\\nd")

        val softBreak = paragraph.children.filterIsInstance<InlineNode.SoftBreak>().single()
        assertEquals(1, softBreak.range.start)
        assertEquals(2, softBreak.range.endExclusive)

        val hardBreaks = paragraph.children.filterIsInstance<InlineNode.HardBreak>()
        assertEquals(2, hardBreaks.size)
        assertEquals(3, hardBreaks[0].range.start)
        assertEquals(6, hardBreaks[0].range.endExclusive)
        assertEquals(7, hardBreaks[1].range.start)
        assertEquals(9, hardBreaks[1].range.endExclusive)
    }

    @Test
    fun incompleteLinkIsStableAcrossChunks() {
        val engine = MarkdownEngine()

        val first = assertIs<BlockNode.Paragraph>(engine.append("[lin").snapshot.document.blocks.single())
        assertEquals("[lin", first.children.inlineLiteral())

        val second = assertIs<BlockNode.Paragraph>(engine.append("k](https://example.com)").snapshot.document.blocks.single())
        val link = second.children.filterIsInstance<InlineNode.Link>().single()
        assertEquals("https://example.com", link.destination)
        assertEquals("link", link.children.inlineLiteral())
    }

    @Test
    fun incompleteCodeSpanIsStableAcrossChunks() {
        val engine = MarkdownEngine()

        val first = assertIs<BlockNode.Paragraph>(engine.append("`co").snapshot.document.blocks.single())
        assertEquals("`co", first.children.inlineLiteral())

        val second = assertIs<BlockNode.Paragraph>(engine.append("de`").snapshot.document.blocks.single())
        val code = second.children.filterIsInstance<InlineNode.CodeSpan>().single()
        assertEquals("code", code.literal)
    }

    private fun appendSingleParagraph(markdown: String): BlockNode.Paragraph {
        val engine = MarkdownEngine()
        return assertIs<BlockNode.Paragraph>(engine.append(markdown).snapshot.document.blocks.single())
    }

    private fun List<InlineNode>.inlineLiteral(): String = joinToString(separator = "") { node ->
        when (node) {
            is InlineNode.CodeSpan -> node.literal
            is InlineNode.Emphasis -> node.children.inlineLiteral()
            is InlineNode.HardBreak -> "\\n"
            is InlineNode.Image -> node.alt.inlineLiteral()
            is InlineNode.Link -> node.children.inlineLiteral()
            is InlineNode.SoftBreak -> "\\n"
            is InlineNode.Strikethrough -> node.children.inlineLiteral()
            is InlineNode.Strong -> node.children.inlineLiteral()
            is InlineNode.Text -> node.literal
            is InlineNode.UnsupportedInline -> node.literal
        }
    }
}
