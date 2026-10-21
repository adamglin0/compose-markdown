package com.adamglin.compose.markdown.core.block

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.internal.OpenBlockFrame
import com.adamglin.compose.markdown.core.model.BlockId
import com.adamglin.compose.markdown.core.model.TextRange
import com.adamglin.compose.markdown.core.source.LineIndex
import com.adamglin.compose.markdown.core.source.SourceBuffer
import com.adamglin.compose.markdown.core.spec.SpecHtmlRenderer
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockParserOpenFrameTest {
    @Test
    fun nestedOpenTailKeepsNestedFrames() {
        assertEquals(listOf("blockquote", "fenced-code"), openFrames("> ```\n> code").map { it.marker })
    }

    @Test
    fun paragraphFollowedByALineEndingStaysOpen() {
        assertEquals(listOf(OpenBlockFrame(marker = "paragraph", startOffset = 0)), openFrames("Foo\n"))
    }

    @Test
    fun tableStaysOpenUntilABlankLine() {
        assertEquals(listOf("table"), openFrames("|a|b|\n|-|-|\n|c|d|\n", MarkdownDialect.GfmCompat).map { it.marker })
        assertEquals(emptyList(), openFrames("|a|b|\n|-|-|\n|c|d|\n\n", MarkdownDialect.GfmCompat))
    }

    @Test
    fun listStaysOpenAcrossBlankLinesUntilAFollowingBlock() {
        assertEquals(listOf("list", "list-item"), openFrames("- a\n\n").map { it.marker })
        assertEquals(listOf(OpenBlockFrame(marker = "paragraph", startOffset = 5)), openFrames("- a\n\nfoo\n"))
    }

    @Test
    fun finishedBlocksAreNotOpen() {
        assertEquals(emptyList(), openFrames("# heading\n"))
        assertEquals(emptyList(), openFrames("```\ncode\n```\n"))
        assertEquals(emptyList(), openFrames("---\n"))
        assertEquals(emptyList(), openFrames("$$\nx\n$$\n", MarkdownDialect.GfmMath))
        assertEquals(listOf("blockquote"), openFrames("> # heading\n").map { it.marker })
    }

    @Test
    fun unterminatedLastLineKeepsTheBlocksItWouldCloseOpen() {
        // `#` may still become `# heading` or `#hashtag` (a lazy continuation), so the list stays open.
        assertEquals(listOf("list", "list-item", "paragraph"), openFrames("- a\n#").map { it.marker })
    }

    @Test
    fun finalParseReportsNoOpenFrames() {
        assertEquals(emptyList(), openFrames("Foo\n", isFinal = true))
    }

    @Test
    fun streamingParagraphStaysOutOfTheStablePrefixUntilItCannotChange() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.CommonMarkCore)

        assertEquals(TextRange(start = 0, endExclusive = 0), engine.append("Foo\n").snapshot.stablePrefixRange)
        assertEquals(TextRange(start = 0, endExclusive = 8), engine.append("===\n").snapshot.stablePrefixRange)
    }

    @Test
    fun streamingDefinitionsReparseOnlyFromTheOpenDefinitionParagraph() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.CommonMarkCore)
        engine.append("# Notes\n\nSee [a] and [b].\n\n")

        val firstDefinition = engine.append("[a]: /a\n")
        val secondDefinition = engine.append("[b]: /b\n")

        assertEquals(TextRange(start = 27, endExclusive = 35), firstDefinition.dirtyRegion)
        assertEquals(27, secondDefinition.dirtyRegion.start)
        val paragraph = engine.finish().snapshot.document.blocks[1]
        assertEquals("<p>See <a href=\"/a\">a</a> and <a href=\"/b\">b</a>.</p>\n", SpecHtmlRenderer.render(listOf(paragraph)))
    }

    @Test
    fun aTableStaysOpenFromTheStartOfTheParagraphItsHeaderCameFrom() {
        // Reparsed alone, the indented header line would start an indented code block.
        assertEquals(listOf(OpenBlockFrame(marker = "table", startOffset = 0)), openFrames("a\n    b|c\n-|-\n", MarkdownDialect.GfmCompat))
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
    fun aLaterDefinitionRehydratesAStableParagraphWithoutReparsingItsBlockStructure() {
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

    private fun openFrames(
        source: String,
        dialect: MarkdownDialect = MarkdownDialect.CommonMarkCore,
        isFinal: Boolean = false,
    ): List<OpenBlockFrame> {
        val sourceBuffer = SourceBuffer().apply { append(source) }
        val lineIndex = LineIndex().apply { append(chunk = source, startOffset = 0) }
        return BlockParser(
            sourceBuffer = sourceBuffer,
            lineIndex = lineIndex,
            dialect = dialect,
            allocateBlockId = { _, _, _ -> BlockId(1L) },
        ).parse(isFinal = isFinal).openBlockStack
    }
}
