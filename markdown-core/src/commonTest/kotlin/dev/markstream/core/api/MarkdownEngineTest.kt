package dev.markstream.core.api

import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.InlineNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

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
        assertEquals("hello\nworld", assertIs<InlineNode.Text>(block.children.single()).literal)
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

    @Test
    fun stablePrefixAdvancesPastClosedPrefixWhenTailRemainsOpen() {
        val engine = MarkdownEngine()

        val delta = engine.append("# ok\n> ```\n> code")

        assertEquals(5, delta.snapshot.stablePrefixEnd)
        assertEquals(0, delta.snapshot.stablePrefixRange.start)
    }
}
