package dev.markstream.core.api

import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.InlineNode
import dev.markstream.core.model.ListStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BlockParserStreamingTest {
    @Test
    fun chunkSplitAcrossNewlineKeepsSingleParagraph() {
        val engine = MarkdownEngine()

        val first = engine.append("hello")
        val second = engine.append("\nworld")

        val firstParagraph = assertIs<BlockNode.Paragraph>(first.snapshot.document.blocks.single())
        val secondParagraph = assertIs<BlockNode.Paragraph>(second.snapshot.document.blocks.single())
        assertEquals(firstParagraph.id, secondParagraph.id)
        assertEquals("hello\nworld", secondParagraph.children.inlineLiteral())
        assertEquals(2, second.snapshot.document.lineCount)
    }

    @Test
    fun chunkSplitInsideFenceProducesOpenCodeBlock() {
        val engine = MarkdownEngine()

        engine.append("```kotlin\nval ")
        val delta = engine.append("x = 1")

        val block = assertIs<BlockNode.FencedCodeBlock>(delta.snapshot.document.blocks.single())
        assertEquals("kotlin", block.infoString)
        assertEquals("kotlin", block.languageHint)
        assertEquals("val x = 1", block.literal)
        assertFalse(block.isClosed)

        val finalBlock = assertIs<BlockNode.FencedCodeBlock>(engine.finish().snapshot.document.blocks.single())
        assertFalse(finalBlock.isClosed)
        assertTrue(engine.snapshot().isFinal)
    }

    @Test
    fun fenceClosesAcrossChunksWhileKeepingBlockIdentity() {
        val engine = MarkdownEngine()

        val opening = engine.append("```kotlin\nval x")
        val body = engine.append(" = 1\n")
        val closing = engine.append("```")

        val openingBlock = assertIs<BlockNode.FencedCodeBlock>(opening.snapshot.document.blocks.single())
        val bodyBlock = assertIs<BlockNode.FencedCodeBlock>(body.snapshot.document.blocks.single())
        val closingBlock = assertIs<BlockNode.FencedCodeBlock>(closing.snapshot.document.blocks.single())

        assertEquals(openingBlock.id, bodyBlock.id)
        assertEquals(bodyBlock.id, closingBlock.id)
        assertFalse(openingBlock.isClosed)
        assertFalse(bodyBlock.isClosed)
        assertTrue(closingBlock.isClosed)
        assertEquals("kotlin", closingBlock.infoString)
        assertEquals("kotlin", closingBlock.languageHint)
        assertEquals("val x = 1", closingBlock.literal)
    }

    @Test
    fun fenceInfoStringNormalizesLanguageAliases() {
        val engine = MarkdownEngine()

        val snapshot = engine.append("```language-kotlin\tlinenums\nval x = 1\n```").snapshot

        val block = assertIs<BlockNode.FencedCodeBlock>(snapshot.document.blocks.single())
        assertEquals("language-kotlin\tlinenums", block.infoString)
        assertEquals("kotlin", block.languageHint)
    }

    @Test
    fun chunkSplitInsideListItemKeepsListIdentity() {
        val engine = MarkdownEngine()

        val first = engine.append("- hel")
        val second = engine.append("lo")

        val firstList = assertIs<BlockNode.ListBlock>(first.snapshot.document.blocks.single())
        val secondList = assertIs<BlockNode.ListBlock>(second.snapshot.document.blocks.single())
        assertEquals(ListStyle.Unordered, secondList.style)
        assertEquals(firstList.id, secondList.id)

        val item = secondList.items.single()
        val paragraph = assertIs<BlockNode.Paragraph>(item.children.single())
        assertEquals("hello", paragraph.children.inlineLiteral())
    }

    @Test
    fun finishWithoutTrailingNewlineFinalizesTailParagraph() {
        val engine = MarkdownEngine()

        engine.append("tail paragraph")
        val delta = engine.finish()

        val block = assertIs<BlockNode.Paragraph>(delta.snapshot.document.blocks.single())
        assertEquals(0, block.range.start)
        assertEquals("tail paragraph".length, block.range.endExclusive)
        assertTrue(delta.snapshot.isFinal)
    }

    @Test
    fun multipleAppendsBuildExpectedBlockTree() {
        val engine = MarkdownEngine()

        val first = engine.append("# Title\n")
        val second = engine.append("\n- one\n")
        val third = engine.append("- two\n> quote\n")

        val firstHeading = assertIs<BlockNode.Heading>(first.snapshot.document.blocks.single())
        val blocks = third.snapshot.document.blocks
        assertEquals(3, blocks.size)
        assertEquals(firstHeading.id, assertIs<BlockNode.Heading>(blocks[0]).id)

        val list = assertIs<BlockNode.ListBlock>(blocks[1])
        assertEquals(2, list.items.size)
        val quote = assertIs<BlockNode.BlockQuote>(blocks[2])
        val quoteParagraph = assertIs<BlockNode.Paragraph>(quote.children.single())
        assertEquals("quote", quoteParagraph.children.inlineLiteral())

        assertEquals(2, second.snapshot.document.blocks.size)
    }

    @Test
    fun commonContainerTextRangesTrackContentOffsets() {
        val engine = MarkdownEngine()

        val snapshot = engine.append("# Title\n> quote\n- item").snapshot

        val heading = assertIs<BlockNode.Heading>(snapshot.document.blocks[0])
        assertEquals("Title", heading.children.inlineLiteral())
        val headingText = heading.children.filterIsInstance<InlineNode.Text>().first()
        assertEquals(2, headingText.range.start)
        assertEquals(7, headingText.range.endExclusive)

        val quote = assertIs<BlockNode.BlockQuote>(snapshot.document.blocks[1])
        val quoteParagraph = assertIs<BlockNode.Paragraph>(quote.children.single())
        val quoteText = quoteParagraph.children.filterIsInstance<InlineNode.Text>().first()
        assertEquals(10, quoteText.range.start)
        assertEquals(15, quoteText.range.endExclusive)

        val list = assertIs<BlockNode.ListBlock>(snapshot.document.blocks[2])
        val listParagraph = assertIs<BlockNode.Paragraph>(list.items.single().children.single())
        val listText = listParagraph.children.filterIsInstance<InlineNode.Text>().first()
        assertEquals(18, listText.range.start)
        assertEquals(22, listText.range.endExclusive)
    }
}

private fun List<InlineNode>.inlineLiteral(): String = joinToString(separator = "") { node ->
    when (node) {
        is InlineNode.CodeSpan -> node.literal
        is InlineNode.Emphasis -> node.children.inlineLiteral()
        is InlineNode.HardBreak -> "\n"
        is InlineNode.Image -> node.alt.inlineLiteral()
        is InlineNode.Link -> node.children.inlineLiteral()
        is InlineNode.SoftBreak -> "\n"
        is InlineNode.Strikethrough -> node.children.inlineLiteral()
        is InlineNode.Strong -> node.children.inlineLiteral()
        is InlineNode.Text -> node.literal
        is InlineNode.UnsupportedInline -> node.literal
    }
}
