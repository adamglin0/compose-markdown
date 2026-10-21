package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.InlineNode
import com.adamglin.compose.markdown.core.model.TextRange
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IncrementalEngineTest {
    @Test
    fun firstDuplicateDefinitionWinsWhenStreamed() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)

        engine.append("[a]\n\n[a]: /first\n\n# h\n\n")
        engine.append("[a]: /second\n")
        val paragraph = assertIs<BlockNode.Paragraph>(engine.finish().snapshot.document.blocks.first())

        assertEquals("/first", paragraph.children.filterIsInstance<InlineNode.Link>().single().destination)
    }

    @Test
    fun laterDefinitionUpdatesStableBlockWithoutTouchingEarlierSnapshots() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)

        val before = engine.append("See [a]\n\npara\n\n").snapshot
        val delta = engine.append("[a]: /u\n")
        val after = assertIs<BlockNode.Paragraph>(delta.snapshot.document.blocks.first())
        val earlier = assertIs<BlockNode.Paragraph>(before.document.blocks.first())

        assertEquals(listOf(after.id), delta.changedBlocks.map { it.id })
        assertEquals("/u", after.children.filterIsInstance<InlineNode.Link>().single().destination)
        assertTrue(earlier.children.none { it is InlineNode.Link })
        assertEquals(before.document.blocks[1], delta.snapshot.document.blocks[1])
    }

    @Test
    fun paragraphStaysMutableUntilItCannotBecomeAHeading() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)

        assertEquals(0, engine.append("Title\n").snapshot.stablePrefixEnd)
        val underlined = engine.append("=")
        val heading = assertIs<BlockNode.Heading>(underlined.snapshot.document.blocks.single())

        assertEquals(0, underlined.snapshot.stablePrefixEnd)
        assertEquals(1, heading.level)
    }

    @Test
    fun tableStaysMutableWhileRowsCanBeAdded() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)

        val header = engine.append("| a |\n|---|\n")
        val row = engine.append("| 1 |\n")
        val table = assertIs<BlockNode.TableBlock>(row.snapshot.document.blocks.single())

        assertEquals(0, header.snapshot.stablePrefixEnd)
        assertEquals(0, row.snapshot.stablePrefixEnd)
        assertEquals(header.snapshot.document.blocks.single().id, table.id)
        assertEquals(1, table.rows.size)
    }

    @Test
    fun closedBlocksEnterTheStablePrefixAndAreNotReparsed() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)

        engine.append("# Title\n\nparagraph\n\n")
        val delta = engine.append("- item")

        assertEquals(TextRange(0, 20), delta.snapshot.stablePrefixRange)
        assertEquals(TextRange(20, 26), delta.dirtyRegion)
        assertEquals(2, delta.stats.preservedBlocks)
        // The list, its item and the item's paragraph.
        assertEquals(3, delta.stats.builtBlocks)
        assertEquals(1, delta.stats.processedLines)
    }

    @Test
    fun loneHighSurrogateWaitsForItsLowSurrogate() {
        val engine = MarkdownEngine()

        val split = engine.append("hi $HIGH_SURROGATE")
        val joined = engine.append("$LOW_SURROGATE")

        assertEquals(3, split.snapshot.document.sourceLength)
        assertEquals("hi", paragraphText(split.snapshot.document.blocks))
        assertEquals("hi 😀", paragraphText(joined.snapshot.document.blocks))
    }

    @Test
    fun unpairedHighSurrogateIsKeptAtFinish() {
        val engine = MarkdownEngine()

        engine.append("x$HIGH_SURROGATE")
        val finished = engine.finish()

        assertEquals(2, finished.snapshot.document.sourceLength)
        assertEquals("x$HIGH_SURROGATE", paragraphText(finished.snapshot.document.blocks))
    }

    @Test
    fun finishTwiceAndEmptyChunksChangeNothing() {
        val engine = MarkdownEngine()

        engine.append("a")
        val empty = engine.append("")
        val finished = engine.finish()
        val again = engine.finish()

        assertEquals(1L, empty.version)
        assertTrue(empty.changedBlocks.isEmpty())
        assertEquals(finished.version, again.version)
        assertTrue(again.changedBlocks.isEmpty())
        assertTrue(again.dirtyRegion.isEmpty)
    }

    @Test
    fun resetRestartsIdsAndLineEndingState() {
        val engine = MarkdownEngine()

        val first = engine.append("a\r")
        engine.reset()
        val restarted = engine.append("\nb")

        assertEquals(2, restarted.snapshot.document.sourceLength)
        assertEquals(first.snapshot.document.blocks.single().id, restarted.snapshot.document.blocks.single().id)
        assertEquals(1L, restarted.version)
    }

    /** Work per append is bounded by the mutable tail, so it does not grow with the document. */
    @Test
    fun perAppendWorkStaysBoundedAsTheDocumentGrows() {
        val section = "## Section\n\nSome *text* with a [link](https://x.y) and `code`.\nSecond line.\n\n" +
            "- one\n- two\n\n> quote\n\n```kotlin\nval x = 1\n```\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n"
        val text = section.repeat(40)
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)
        var maxProcessedLines = 0
        var maxInlineParsed = 0
        randomChunks(text, Random(1), minSize = 4, maxSize = 10).forEach { chunk ->
            val stats = engine.append(chunk).stats
            maxProcessedLines = maxOf(maxProcessedLines, stats.processedLines)
            maxInlineParsed = maxOf(maxInlineParsed, stats.inlineParsedBlockCount)
        }

        assertTrue(maxProcessedLines <= 8, "processed $maxProcessedLines lines in one append")
        assertTrue(maxInlineParsed <= 3, "parsed inline content of $maxInlineParsed blocks in one append")
        assertTrue(engine.snapshot().stablePrefixEnd > text.length - section.length)
    }

    private fun paragraphText(blocks: List<BlockNode>): String =
        assertIs<BlockNode.Paragraph>(blocks.single()).children.joinToString("") { (it as InlineNode.Text).literal }

    private companion object {
        // Split from a whole emoji at runtime: the JS compiler folds lone-surrogate constants into string
        // literals, which do not survive UTF-8 encoding of the emitted JavaScript.
        private const val EMOJI = "\uD83D\uDE00"
        val HIGH_SURROGATE = EMOJI[0].toString()
        val LOW_SURROGATE = EMOJI[1].toString()
    }
}
