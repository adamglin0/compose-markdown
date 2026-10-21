package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.TextRange
import com.adamglin.compose.markdown.core.spec.SpecHtmlRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The stable prefix ends where the first top-level block that later input can still change starts. */
class StablePrefixTest {
    @Test
    fun openContainersStayOutOfTheStablePrefix() {
        assertEquals(0, stablePrefixEnd("> ```\n> code"))
        assertEquals(0, stablePrefixEnd("- a\n\n"))
        assertEquals(5, stablePrefixEnd("- a\n\nfoo\n"))
        assertEquals(0, stablePrefixEnd("> # heading\n"))
    }

    @Test
    fun paragraphFollowedByALineEndingStaysOpen() {
        // It can still become a setext heading or a table header.
        assertEquals(0, stablePrefixEnd("Foo\n"))
    }

    @Test
    fun tableStaysOpenUntilABlankLine() {
        assertEquals(0, stablePrefixEnd("|a|b|\n|-|-|\n|c|d|\n", MarkdownDialect.GfmCompat))
        assertEquals(19, stablePrefixEnd("|a|b|\n|-|-|\n|c|d|\n\n", MarkdownDialect.GfmCompat))
    }

    @Test
    fun aTableStartsAtItsHeaderLineAndTheParagraphAboveIsFinal() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)
        val snapshot = engine.append("a\n    b|c\n-|-\n").snapshot

        assertEquals(2, snapshot.stablePrefixEnd)
        assertIs<BlockNode.Paragraph>(snapshot.document.blocks[0])
        assertIs<BlockNode.TableBlock>(snapshot.document.blocks[1])
    }

    @Test
    fun finishedBlocksAreStable() {
        assertEquals(10, stablePrefixEnd("# heading\n"))
        assertEquals(13, stablePrefixEnd("```\ncode\n```\n"))
        assertEquals(4, stablePrefixEnd("---\n"))
        assertEquals(8, stablePrefixEnd("$$\nx\n$$\n", MarkdownDialect.GfmMath))
    }

    @Test
    fun unterminatedLastLineKeepsTheBlocksItWouldCloseOpen() {
        // `#` may still become `# heading` or `#hashtag` (a lazy continuation), so the list stays open.
        assertEquals(0, stablePrefixEnd("- a\n#"))
        // Even a closed block's line is not stable before it ends: `# h` may still grow.
        assertEquals(0, stablePrefixEnd("# h"))
    }

    @Test
    fun finishMakesEverythingStable() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.CommonMarkCore)
        engine.append("Foo\n")

        assertEquals(TextRange(start = 0, endExclusive = 4), engine.finish().snapshot.stablePrefixRange)
    }

    @Test
    fun streamingParagraphStaysOutOfTheStablePrefixUntilItCannotChange() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.CommonMarkCore)

        assertEquals(TextRange(start = 0, endExclusive = 0), engine.append("Foo\n").snapshot.stablePrefixRange)
        assertEquals(TextRange(start = 0, endExclusive = 8), engine.append("===\n").snapshot.stablePrefixRange)
    }

    @Test
    fun eachCallParsesOnlyTheLinesItCompletesAndTheLastLine() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.CommonMarkCore)
        engine.append("# Notes\n\nSee [a] and [b].\n\n")

        val firstDefinition = engine.append("[a]: /a\n")
        val secondDefinition = engine.append("[b]: /b\n")

        assertEquals(TextRange(start = 27, endExclusive = 35), firstDefinition.dirtyRegion)
        assertEquals(TextRange(start = 35, endExclusive = 43), secondDefinition.dirtyRegion)
        assertEquals(1, secondDefinition.stats.processedLines)
        val paragraph = engine.finish().snapshot.document.blocks[1]
        assertEquals("<p>See <a href=\"/a\">a</a> and <a href=\"/b\">b</a>.</p>\n", SpecHtmlRenderer.render(listOf(paragraph)))
    }

    @Test
    fun streamingKeepsTheFirstDefinitionOfALabel() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.CommonMarkCore)
        engine.append("[a]: /first\n\n")
        engine.append("[a]: /second\n\n")
        engine.append("[a]\n")

        assertEquals("<p><a href=\"/first\">a</a></p>\n", SpecHtmlRenderer.render(engine.finish().snapshot.document.blocks))
    }

    @Test
    fun aLaterDefinitionRebuildsAStableParagraphWithoutParsingItAgain() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.CommonMarkCore)
        // The paragraph node starts at the indented line, which alone would be an indented code block.
        engine.append("[x]: /x\n    see [a]\n\n")
        engine.append("[a]: /a\n")

        assertEquals("<p>see <a href=\"/a\">a</a></p>\n", SpecHtmlRenderer.render(engine.finish().snapshot.document.blocks))
    }

    @Test
    fun finishingAfterAWhitespaceOnlyBlankLineKeepsTheParsedBlocks() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.CommonMarkCore)
        engine.append("[a]: /u\n     - w\n  \n")

        assertEquals("<p>- w</p>\n", SpecHtmlRenderer.render(engine.finish().snapshot.document.blocks))
    }

    private fun stablePrefixEnd(source: String, dialect: MarkdownDialect = MarkdownDialect.CommonMarkCore): Int =
        MarkdownEngine(dialect = dialect).append(source).snapshot.stablePrefixEnd
}
