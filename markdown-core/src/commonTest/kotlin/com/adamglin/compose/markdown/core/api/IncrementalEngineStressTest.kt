package com.adamglin.compose.markdown.core.api

import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.itemMarker
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.InlineNode
import com.adamglin.compose.markdown.core.styleName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IncrementalEngineStressTest {
    @Test
    fun tokenByTokenAppendMatchesOneShotParse() {
        val text = "# Title\n\nhello *world* from `chat`\n\n- one\n- two\n> quote"

        val incremental = MarkdownEngine()
        val tokens = listOf("# ", "Title", "\n\n", "hello ", "*world*", " from ", "`chat`", "\n\n", "- one\n", "- two\n", "> quote")
        tokens.forEach { chunk -> incremental.append(chunk) }
        val incrementalSnapshot = incremental.finish().snapshot

        val full = MarkdownEngine()
        full.append(text)
        val fullSnapshot = full.finish().snapshot

        assertEquals(fullSnapshot.document.blocks.debugShape(), incrementalSnapshot.document.blocks.debugShape())
    }

    @Test
    fun charByCharAppendMatchesOneShotParse() {
        val text = "> quote\n> still quote\n\n```kotlin\nval x = 1\n```"

        val incremental = MarkdownEngine()
        text.forEach { char -> incremental.append(char.toString()) }
        val incrementalSnapshot = incremental.finish().snapshot

        val full = MarkdownEngine()
        full.append(text)
        val fullSnapshot = full.finish().snapshot

        assertEquals(fullSnapshot.document.blocks.debugShape(), incrementalSnapshot.document.blocks.debugShape())
    }

    @Test
    fun charByCharAppendMatchesOneShotParseForContainerAndLeafBlocks() {
        val text = "Title\n===\n\n- a\n  - b\n\n  - c\nlazy\n\n    code\n\tmore\n\n> quote\ncontinued\n> - item\n\n" +
            "1. one\n1) two\n\n[ref]: /url\n\"title\"\n\nsee [ref]\n\n| a | b |\n|---|---|\nrow\n\n$$\nx\n$$\n- [ ] task\n  ```\n  fenced\n"

        listOf(MarkdownDialect.ChatFast, MarkdownDialect.CommonMarkCore, MarkdownDialect.GfmMath).forEach { dialect ->
            val incremental = MarkdownEngine(dialect = dialect)
            text.forEach { char -> incremental.append(char.toString()) }
            val full = MarkdownEngine(dialect = dialect)
            full.append(text)

            assertEquals(
                full.finish().snapshot.document.blocks.debugShape(),
                incremental.finish().snapshot.document.blocks.debugShape(),
                "dialect: ${dialect.id}",
            )
        }
    }

    @Test
    fun manySmallChunksKeepSnapshotsRenderable() {
        val text = "- alpha\n- beta\n- gamma\n\nparagraph"
        val engine = MarkdownEngine()

        text.chunked(2).forEach { chunk ->
            val delta = engine.append(chunk)
            assertTrue(delta.snapshot.document.blocks.isNotEmpty())
        }

        val snapshot = engine.snapshot()
        assertEquals(2, snapshot.document.blocks.size)
        assertEquals(text.length, snapshot.document.sourceLength)
    }

    @Test
    fun codeFenceAcrossChunksReparsesOnlyMutableTail() {
        val engine = MarkdownEngine()

        engine.append("prefix\n\n```kotlin\nval")
        val delta = engine.append(" x = 1\n```")

        val paragraph = assertIs<BlockNode.Paragraph>(delta.snapshot.document.blocks.first())
        val fence = assertIs<BlockNode.FencedCodeBlock>(delta.snapshot.document.blocks.last())
        assertEquals("prefix", paragraph.children.inlineLiteral())
        assertTrue(fence.isClosed)
        assertEquals(1, delta.stats.preservedBlocks)
        assertEquals(1, delta.stats.builtBlocks)
    }

    @Test
    fun listAndQuoteAcrossChunksKeepPrefixIdsStable() {
        val engine = MarkdownEngine()

        val first = engine.append("# Title\n\n- one")
        val headingId = first.snapshot.document.blocks.first().id
        val second = engine.append("\n- two\n> quote")

        assertEquals(headingId, second.snapshot.document.blocks.first().id)
        assertEquals(9, second.dirtyRegion.start)
        assertEquals(1, second.stats.preservedBlocks)
        // The list with two items and their paragraphs, then the block quote and its paragraph.
        assertEquals(7, second.stats.builtBlocks)
    }

    @Test
    fun finishCanSnapshotOpenTailWithoutLosingBlockIdentity() {
        val engine = MarkdownEngine()

        val open = engine.append("```\ncode")
        val openId = open.snapshot.document.blocks.single().id
        val finished = engine.finish()

        val fence = assertIs<BlockNode.FencedCodeBlock>(finished.snapshot.document.blocks.single())
        assertEquals(openId, fence.id)
        assertFalse(fence.isClosed)
        assertTrue(finished.changedBlocks.isEmpty())
    }
}

private fun List<BlockNode>.debugShape(): String = joinToString(separator = "\n") { block ->
    block.debugShape(depth = 0)
}

private fun BlockNode.debugShape(depth: Int): String {
    val indent = "  ".repeat(depth)
    return when (this) {
        is BlockNode.BlockQuote -> buildString {
            append("${indent}quote")
            children.forEach { child ->
                append('\n')
                append(child.debugShape(depth + 1))
            }
        }

        is BlockNode.Document -> buildString {
            append("${indent}document")
            children.forEach { child ->
                append('\n')
                append(child.debugShape(depth + 1))
            }
        }

        is BlockNode.FencedCodeBlock -> "${indent}fence(info=${infoString ?: "-"}, closed=$isClosed, literal=${literal.replace("\n", "\\n")})"
        is BlockNode.Heading -> "${indent}heading(level=$level, text=${children.inlineLiteral()})"
        is BlockNode.ListBlock -> buildString {
            append("${indent}list(style=$styleName, loose=$isLoose)")
            items.forEachIndexed { index, item ->
                append('\n')
                append("$indent  item(marker=${itemMarker(index)})")
                item.children.forEach { child ->
                    append('\n')
                    append(child.debugShape(depth + 2))
                }
            }
        }

        is BlockNode.ListItem -> buildString {
            append("${indent}item")
            children.forEach { child ->
                append('\n')
                append(child.debugShape(depth + 1))
            }
        }

        is BlockNode.Paragraph -> "${indent}paragraph(text=${children.inlineLiteral()})"
        is BlockNode.IndentedCodeBlock -> "${indent}code(${literal.replace("\n", "\\n")})"
        is BlockNode.TableBlock -> "${indent}table(columns=${header.cells.size}, rows=${rows.size})"
        is BlockNode.TableCell -> "${indent}cell(${children.inlineLiteral()})"
        is BlockNode.TableRow -> "${indent}row(${cells.joinToString(separator = "|") { it.children.inlineLiteral() }})"
        is BlockNode.ThematicBreak -> "${indent}break($marker)"
        is BlockNode.MathBlock -> "${indent}math(${latex.replace("\n", "\\n")})"
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
        is InlineNode.MathSpan -> node.latex
    }
}
