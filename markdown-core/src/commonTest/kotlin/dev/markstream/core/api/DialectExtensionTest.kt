package dev.markstream.core.api

import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.HeadingStyle
import dev.markstream.core.model.InlineNode
import dev.markstream.core.model.TextRange
import dev.markstream.core.model.TaskState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DialectExtensionTest {
    @Test
    fun setextDelimiterRetroactivelyUpgradesPreviousParagraph() {
        val engine = MarkdownEngine()

        engine.append("Title\n")
        val delta = engine.append("---\n")

        val heading = assertIs<BlockNode.Heading>(delta.snapshot.document.blocks.single())
        assertEquals(HeadingStyle.Setext, heading.style)
        assertEquals(2, heading.level)
        assertEquals("Title", heading.children.inlineLiteral())
        assertEquals(0, delta.dirtyRegion.start)
    }

    @Test
    fun tableDelimiterRetroactivelyTurnsParagraphIntoTableHeader() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)

        engine.append("Name | Score\n")
        val delta = engine.append("--- | ---\nAda | 10\n")

        val table = assertIs<BlockNode.TableBlock>(delta.snapshot.document.blocks.single())
        assertEquals(2, table.header.cells.size)
        assertEquals("Name", table.header.cells[0].children.inlineLiteral())
        assertEquals("Ada", table.rows.single().cells[0].children.inlineLiteral())
        assertEquals(0, delta.dirtyRegion.start)
    }

    @Test
    fun referenceDefinitionCanResolveEarlierUsageWithoutWholeDocumentReparse() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.CommonMarkCore)

        val first = engine.append("See [guide][docs]\n")
        val firstBlockId = first.snapshot.document.blocks.single().id
        assertFalse(assertIs<BlockNode.Paragraph>(first.snapshot.document.blocks.single()).children.any { it is InlineNode.Link })

        val delta = engine.append("\n[docs]: https://example.com/guide\n")

        val paragraph = assertIs<BlockNode.Paragraph>(delta.snapshot.document.blocks.single())
        val link = paragraph.children.filterIsInstance<InlineNode.Link>().single()
        assertEquals("https://example.com/guide", link.destination)
        assertTrue(delta.updatedBlockIds.contains(firstBlockId))
        assertEquals(1, delta.snapshot.document.blocks.size)
        assertTrue(delta.stats.reparsedBlocks in 1..2)
    }

    @Test
    fun referenceDefinitionAfterUnrelatedAppendRehydratesOnlyDependentPreservedBlock() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.CommonMarkCore)

        val first = engine.append("See [guide][docs]\n")
        val dependentBlockId = first.snapshot.document.blocks.single().id
        val second = engine.append("\nStable tail\n")
        val stableTailBlockId = second.snapshot.document.blocks.last().id

        val delta = engine.append("\n[docs]: https://example.com/guide\n")

        assertEquals(listOf(dependentBlockId), delta.updatedBlockIds)
        assertFalse(delta.updatedBlockIds.contains(stableTailBlockId))
        assertEquals(2, delta.snapshot.document.blocks.size)
        assertEquals(2, delta.stats.reparsedBlocks)

        val paragraph = assertIs<BlockNode.Paragraph>(delta.snapshot.document.blocks.first())
        val link = paragraph.children.filterIsInstance<InlineNode.Link>().single()
        assertEquals("https://example.com/guide", link.destination)
        assertEquals("Stable tail", assertIs<BlockNode.Paragraph>(delta.snapshot.document.blocks.last()).children.inlineLiteral())
    }

    @Test
    fun multiLineParagraphRetroactivelyUpgradesIntoSingleSetextHeading() {
        val engine = MarkdownEngine()

        val first = engine.append("# Lead\n\nLine one\nLine two\n")
        val preservedLeadId = first.snapshot.document.blocks.first().id
        val delta = engine.append("-----\n")

        assertEquals(8, delta.dirtyRegion.start)
        assertEquals(preservedLeadId, delta.snapshot.document.blocks.first().id)
        assertEquals(2, delta.snapshot.document.blocks.size)

        val heading = assertIs<BlockNode.Heading>(delta.snapshot.document.blocks[1])
        assertEquals(HeadingStyle.Setext, heading.style)
        assertEquals(2, heading.level)
        assertEquals("Line one\nLine two", heading.children.inlineLiteral())
    }

    @Test
    fun setextAndTableTrimmedTextRangesStayAligned() {
        val setextEngine = MarkdownEngine()
        val setext = setextEngine.append("  Title  \n---\n").snapshot
        val heading = assertIs<BlockNode.Heading>(setext.document.blocks.single())
        val headingText = heading.children.filterIsInstance<InlineNode.Text>().single()

        assertEquals("Title", headingText.literal)
        assertEquals(TextRange(start = 2, endExclusive = 7), headingText.range)

        val tableEngine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)
        val table = tableEngine.append("  | Name | Score |  \n| --- | --- |\n").snapshot
            .document.blocks.single() as BlockNode.TableBlock
        val firstCellText = table.header.cells.first().children.filterIsInstance<InlineNode.Text>().single()

        assertEquals("Name", firstCellText.literal)
        assertEquals(TextRange(start = 4, endExclusive = 8), firstCellText.range)
    }

    @Test
    fun taskListItemsExposeCheckedStateAndStripMarkerFromParagraphText() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)

        val snapshot = engine.append("- [x] done\n- [ ] later").snapshot

        val list = assertIs<BlockNode.ListBlock>(snapshot.document.blocks.single())
        assertEquals(TaskState.Checked, list.items[0].taskState)
        assertEquals(TaskState.Unchecked, list.items[1].taskState)
        assertEquals("done", assertIs<BlockNode.Paragraph>(list.items[0].children.single()).children.inlineLiteral())
        assertEquals("later", assertIs<BlockNode.Paragraph>(list.items[1].children.single()).children.inlineLiteral())
    }

    @Test
    fun chatFastKeepsReferenceDefinitionLinesAsPlainParagraphText() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.ChatFast)

        val snapshot = engine.append("See [guide][docs]\n\n[docs]: https://example.com/guide").snapshot

        assertEquals(2, snapshot.document.blocks.size)
        assertFalse(assertIs<BlockNode.Paragraph>(snapshot.document.blocks.first()).children.any { it is InlineNode.Link })
        assertEquals(
            "[docs]: https://example.com/guide",
            assertIs<BlockNode.Paragraph>(snapshot.document.blocks.last()).children.inlineLiteral(),
        )
    }

    @Test
    fun presetsWithReferenceLinksConsumeDefinitionLines() {
        val commonMark = MarkdownEngine(dialect = MarkdownDialect.CommonMarkCore)
        val gfm = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)
        val markdown = "See [guide][docs]\n\n[docs]: https://example.com/guide"

        val commonMarkSnapshot = commonMark.append(markdown).snapshot
        val gfmSnapshot = gfm.append(markdown).snapshot

        assertEquals(1, commonMarkSnapshot.document.blocks.size)
        assertEquals(1, gfmSnapshot.document.blocks.size)
        assertTrue(assertIs<BlockNode.Paragraph>(commonMarkSnapshot.document.blocks.single()).children.any { it is InlineNode.Link })
        assertTrue(assertIs<BlockNode.Paragraph>(gfmSnapshot.document.blocks.single()).children.any { it is InlineNode.Link })
    }

    @Test
    fun dialectPresetsExposeExpectedReferenceAndTableDifferences() {
        val referenceMarkdown = "See [guide][docs]\n\n[docs]: https://example.com/guide"
        val tableMarkdown = "A | B\n--- | ---\n1 | 2"

        val chatFastReferenceEngine = MarkdownEngine(dialect = MarkdownDialect.ChatFast)
        chatFastReferenceEngine.append(referenceMarkdown)
        val chatFastReference = chatFastReferenceEngine.finish().snapshot

        val commonMarkReferenceEngine = MarkdownEngine(dialect = MarkdownDialect.CommonMarkCore)
        commonMarkReferenceEngine.append(referenceMarkdown)
        val commonMarkReference = commonMarkReferenceEngine.finish().snapshot

        val commonMarkTableEngine = MarkdownEngine(dialect = MarkdownDialect.CommonMarkCore)
        commonMarkTableEngine.append(tableMarkdown)
        val commonMarkTable = commonMarkTableEngine.finish().snapshot

        val gfmTableEngine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)
        gfmTableEngine.append(tableMarkdown)
        val gfmTable = gfmTableEngine.finish().snapshot

        assertEquals(2, chatFastReference.document.blocks.size)
        assertFalse(assertIs<BlockNode.Paragraph>(chatFastReference.document.blocks.first()).children.any { it is InlineNode.Link })
        assertEquals("[docs]: https://example.com/guide", assertIs<BlockNode.Paragraph>(chatFastReference.document.blocks.last()).children.inlineLiteral())
        assertTrue(assertIs<BlockNode.Paragraph>(commonMarkReference.document.blocks.single()).children.any { it is InlineNode.Link })
        assertIs<BlockNode.Paragraph>(commonMarkTable.document.blocks.first())
        assertIs<BlockNode.TableBlock>(gfmTable.document.blocks.single())
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
