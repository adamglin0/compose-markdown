package com.adamglin.compose.markdown.core.api

import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.itemMarker
import com.adamglin.compose.markdown.core.model.BlockChange
import com.adamglin.compose.markdown.core.model.BlockId
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.InlineNode
import com.adamglin.compose.markdown.core.styleName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarkdownEngineTest {
    @Test
    fun appendCreatesSingleParagraphBlock() {
        val engine = MarkdownEngine()

        val delta = engine.append("hello\nworld")

        assertEquals(1L, delta.version)
        assertEquals(0, delta.snapshot.stablePrefixEnd)
        assertEquals(listOf(BlockChange(delta.snapshot.document.blocks.single().id, null, 0, delta.snapshot.document.blocks.single())), delta.changedBlocks)
        assertEquals(MarkdownDialect.ChatFast, delta.snapshot.dialect)

        val block = assertIs<BlockNode.Paragraph>(delta.snapshot.document.blocks.single())
        assertEquals("hello\nworld", block.children.inlineLiteral())
        assertEquals(0, block.range.start)
        assertEquals(11, block.range.endExclusive)
        assertFalse(delta.snapshot.isFinal)
        assertEquals(11, delta.stats.appendedChars)
    }

    @Test
    fun finishMarksSnapshotFinalWithoutReparsingClosedPrefix() {
        val engine = MarkdownEngine()

        engine.append("# ok\n")
        val delta = engine.finish()

        assertTrue(delta.snapshot.isFinal)
        assertEquals(5, delta.snapshot.stablePrefixEnd)
        assertEquals(0, delta.changedBlocks.size)
        assertEquals(0, delta.stats.builtBlocks)
        assertEquals(0, delta.stats.processedLines)
    }

    @Test
    fun appendAfterFinishFailsUntilReset() {
        val engine = MarkdownEngine()

        engine.append("# ok\n")
        val finished = engine.finish()
        val error = assertFailsWith<IllegalStateException> {
            engine.append("> ```\n> code")
        }

        assertEquals(5, finished.snapshot.stablePrefixEnd)
        assertEquals(
            "Cannot call append() after finish(); call reset() or create a new MarkdownEngine.",
            error.message,
        )
    }

    @Test
    fun resetAfterFinishAllowsAppendAgain() {
        val engine = MarkdownEngine()

        engine.append("# ok\n")
        engine.finish()

        engine.reset()
        val delta = engine.append("> ```\n> code")

        assertFalse(delta.snapshot.isFinal)
        assertEquals(0, delta.snapshot.stablePrefixRange.start)
        assertEquals(0, delta.snapshot.stablePrefixEnd)
    }

    @Test
    fun appendUsesMutableTailInsteadOfWholeDocumentReparse() {
        val engine = MarkdownEngine()

        engine.append("stable\n\n")
        val delta = engine.append("tail")

        assertEquals(8, delta.dirtyRegion.start)
        assertEquals(delta.snapshot.document.sourceLength, delta.dirtyRegion.endExclusive)
        assertEquals(1, delta.stats.preservedBlocks)
        assertEquals(1, delta.stats.builtBlocks)
        assertEquals(1, delta.stats.processedLines)
    }

    @Test
    fun appendKeepsBlockIdStableAcrossTailGrowth() {
        val engine = MarkdownEngine()

        val first = engine.append("hello")
        val second = engine.append(" world")

        val firstBlock = assertIs<BlockNode.Paragraph>(first.snapshot.document.blocks.single())
        val secondBlock = assertIs<BlockNode.Paragraph>(second.snapshot.document.blocks.single())
        assertEquals(firstBlock.id, secondBlock.id)
        assertEquals(listOf(BlockChange(firstBlock.id, 0, 0, secondBlock)), second.changedBlocks)
    }

    @Test
    fun headingKeepsBlockIdStableAcrossIncrementalReparse() {
        val engine = MarkdownEngine()

        val first = engine.append("## Title")
        val second = engine.append(" more")

        val firstHeading = assertIs<BlockNode.Heading>(first.snapshot.document.blocks.single())
        val secondHeading = assertIs<BlockNode.Heading>(second.snapshot.document.blocks.single())

        assertEquals(firstHeading.id, secondHeading.id)
        assertEquals(listOf(BlockChange(firstHeading.id, 0, 0, secondHeading)), second.changedBlocks)
    }

    @Test
    fun tableAndNestedBlocksKeepIdsStableAcrossIncrementalReparse() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)

        val first = engine.append("Name | Score\n--- | ---\nAda | 10")
        val second = engine.append("\nBob | 20")

        val firstTable = assertIs<BlockNode.TableBlock>(first.snapshot.document.blocks.single())
        val secondTable = assertIs<BlockNode.TableBlock>(second.snapshot.document.blocks.single())
        val firstRow = firstTable.rows.single()
        val secondRow = secondTable.rows.first()

        assertEquals(firstTable.id, secondTable.id)
        assertEquals(firstTable.header.id, secondTable.header.id)
        assertEquals(firstTable.header.cells.map(BlockNode.TableCell::id), secondTable.header.cells.map(BlockNode.TableCell::id))
        assertEquals(firstRow.id, secondRow.id)
        assertEquals(firstRow.cells.map(BlockNode.TableCell::id), secondRow.cells.map(BlockNode.TableCell::id))
    }

    @Test
    fun stablePrefixAdvancesPastClosedPrefixWhenTailRemainsOpen() {
        val engine = MarkdownEngine()

        val delta = engine.append("# ok\n> ```\n> code")

        assertEquals(5, delta.snapshot.stablePrefixEnd)
        assertEquals(0, delta.snapshot.stablePrefixRange.start)
        assertEquals(0, delta.stats.preservedBlocks)
        // The heading, the block quote and its fence.
        assertEquals(3, delta.stats.builtBlocks)
    }

    @Test
    fun inlineCacheReusesUnchangedTailBlocksAcrossContainerGrowth() {
        val engine = MarkdownEngine()

        engine.append("- one\n- two")
        val delta = engine.append("\n- three")

        assertEquals(2, delta.stats.inlineCacheHitBlockCount)
        assertEquals(1, delta.stats.inlineParsedBlockCount)
    }

    @Test
    fun deltaReportsUpdatedAndInsertedBlocksWithTheirIndexes() {
        val engine = MarkdownEngine()

        val first = engine.append("stable")
        val second = engine.append("\n\nnew")

        val updatedId = first.snapshot.document.blocks.single().id
        val insertedId = second.snapshot.document.blocks.last().id

        assertEquals(listOf(updatedId to 0, insertedId to null), second.changedBlocks.map { it.id to it.oldIndex })
        assertTrue(second.removedBlockIds.isEmpty())
    }

    @Test
    fun appendNormalizesCarriageReturnAndCrLfChunksBeforeParsing() {
        val incremental = MarkdownEngine()
        val normalized = MarkdownEngine()

        incremental.append("# title\r")
        incremental.append("\nplain\r\n> quote\r")
        incremental.append("tail")
        normalized.append("# title\nplain\n> quote\ntail")

        val incrementalSnapshot = incremental.finish().snapshot
        val normalizedSnapshot = normalized.finish().snapshot

        assertEquals(4, incrementalSnapshot.document.lineCount)
        assertEquals(normalizedSnapshot.document.sourceLength, incrementalSnapshot.document.sourceLength)
        assertEquals(normalizedSnapshot.document.blocks.debugShape(), incrementalSnapshot.document.blocks.debugShape())
    }

    @Test
    fun splitCrLfAcrossChunkBoundaryDoesNotCreateExtraLine() {
        val engine = MarkdownEngine()

        val first = engine.append("\r")
        val second = engine.append("\n")
        val snapshot = engine.finish().snapshot

        assertEquals(2, first.snapshot.document.lineCount)
        assertEquals(1, first.version)
        assertEquals(1, second.version)
        assertTrue(second.changedBlocks.isEmpty())
        assertTrue(second.dirtyRegion.isEmpty)
        assertEquals(1, snapshot.document.sourceLength)
        assertEquals(2, snapshot.document.lineCount)
        assertTrue(snapshot.document.blocks.isEmpty())
    }

    @Test
    fun appendOnlySequencesNeverRemoveBlocksInCurrentDialectSubset() {
        val engine = MarkdownEngine()

        val chunks = listOf("# Title\n", "\n- one", "\n- two", "\n> quote")
        var lastRemoved: List<BlockId> = emptyList()
        chunks.forEach { chunk ->
            lastRemoved = engine.append(chunk).removedBlockIds
        }

        assertTrue(lastRemoved.isEmpty())
    }
}

private fun List<BlockNode>.debugShape(): String = joinToString(separator = "\n") { block ->
    when (block) {
        is BlockNode.BlockQuote -> "quote(${block.children.debugShape()})"
        is BlockNode.Document -> "document(${block.children.debugShape()})"
        is BlockNode.FencedCodeBlock -> "fence(${block.literal.replace("\n", "\\n")},${block.isClosed})"
        is BlockNode.Heading -> "heading(${block.level},${block.children.inlineLiteral()})"
        is BlockNode.ListBlock -> "list(${block.styleName},${block.items.withIndex().joinToString(separator = "\n") { (index, item) -> "item(${block.itemMarker(index)},${item.children.debugShape()})" }})"
        is BlockNode.ListItem -> "item(${block.children.debugShape()})"
        is BlockNode.Paragraph -> "paragraph(${block.children.inlineLiteral()})"
        is BlockNode.IndentedCodeBlock -> "code(${block.literal.replace("\n", "\\n")})"
        is BlockNode.TableBlock -> "table(${block.header.cells.size},${block.rows.size})"
        is BlockNode.TableCell -> "cell(${block.children.inlineLiteral()})"
        is BlockNode.TableRow -> "row(${block.cells.joinToString(separator = "|") { it.children.inlineLiteral() }})"
        is BlockNode.ThematicBreak -> "break(${block.marker})"
        is BlockNode.MathBlock -> "math(${block.latex.replace("\n", "\\n")})"
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
        is InlineNode.MathSpan -> node.latex
    }
}
