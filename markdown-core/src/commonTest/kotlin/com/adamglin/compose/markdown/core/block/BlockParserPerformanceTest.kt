package com.adamglin.compose.markdown.core.block

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.InlineNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Pathological block inputs. Deep container nesting used to overflow the stack in the recursive tree
 * builder; the bound is deliberately generous so that only super-linear behaviour fails these tests.
 */
class BlockParserPerformanceTest {
    @Test
    fun deeplyNestedQuotesStopNestingAndKeepTheirText() {
        var block: BlockNode = parse(">".repeat(50_000) + " a\n").single()
        var depth = 0
        while (block is BlockNode.BlockQuote) {
            depth += 1
            block = block.children.single()
        }
        val paragraph = assertIs<BlockNode.Paragraph>(block)
        assertEquals(64, depth)
        assertEquals(">".repeat(50_000 - 64) + " a", (paragraph.children.single() as InlineNode.Text).literal)
    }

    @Test
    fun deeplyNestedContainersAreLinear() {
        parse("- ".repeat(50_000) + "a\n")
        parse("> - ".repeat(20_000) + "a\n")
        parse((0 until 1_000).joinToString(separator = "\n") { "  ".repeat(it) + "- a" })
        parse((1..1_000).joinToString(separator = "\n") { ">".repeat(it) + " a" })
    }

    @Test
    fun longDocumentsAreLinear() {
        parse("|a|b|c|\n|-|-|-|\n" + "|x|y|z|\n".repeat(30_000))
        parse("|" + "a|".repeat(10_000) + "\n|" + "-|".repeat(10_000) + "\n" + ("|" + "x|".repeat(10_000) + "\n").repeat(5))
        parse("- a\n".repeat(30_000))
        parse("-\n".repeat(30_000))
        parse("[a]: /u\n".repeat(30_000) + "===\n")
        parse("a ".repeat(200_000))
    }

    private fun parse(markdown: String): List<BlockNode> {
        val mark = TimeSource.Monotonic.markNow()
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmMath)
        engine.append(markdown)
        val blocks = engine.finish().snapshot.document.blocks
        val elapsed = mark.elapsedNow()
        assertTrue(elapsed < 5.seconds, "Parsing ${markdown.length} chars (${markdown.take(16)}...) took $elapsed")
        return blocks
    }
}
