package dev.markstream.core.api

import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.model.BlockNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarkdownEngineTest {
    @Test
    fun appendCreatesSingleParagraphBlock() {
        val engine = MarkdownEngine()

        val delta = engine.append("hello\nworld")

        assertEquals(1L, delta.version)
        assertEquals(0, delta.stablePrefixEnd)
        assertEquals(1, delta.changedBlocks.size)
        assertEquals(MarkdownDialect.ChatFast, delta.snapshot.dialect)

        val block = assertIs<BlockNode.Paragraph>(delta.snapshot.document.blocks.single())
        assertEquals(3, block.children.size)
        assertEquals(0, block.range.start)
        assertEquals(11, block.range.endExclusive)
        assertFalse(delta.snapshot.isFinal)
    }

    @Test
    fun finishMarksSnapshotFinal() {
        val engine = MarkdownEngine()

        engine.append("streaming text")
        val delta = engine.finish()

        assertTrue(delta.snapshot.isFinal)
        assertEquals(14, delta.snapshot.stablePrefixEnd)
        assertEquals(0, delta.changedBlocks.size)
        assertFalse(delta.isNoOp)
    }

    @Test
    fun appendDirtyRegionCoversFullPlaceholderReparse() {
        val engine = MarkdownEngine()

        engine.append("hello")
        val delta = engine.append(" world")

        assertEquals(0, delta.dirtyRegion.start)
        assertEquals(delta.snapshot.document.sourceLength, delta.dirtyRegion.endExclusive)
    }

    @Test
    fun appendKeepsBlockIdStableAcrossTailGrowth() {
        val engine = MarkdownEngine()

        val first = engine.append("hello")
        val second = engine.append(" world")

        val firstBlock = assertIs<BlockNode.Paragraph>(first.snapshot.document.blocks.single())
        val secondBlock = assertIs<BlockNode.Paragraph>(second.snapshot.document.blocks.single())
        assertEquals(firstBlock.id, secondBlock.id)
    }
}
