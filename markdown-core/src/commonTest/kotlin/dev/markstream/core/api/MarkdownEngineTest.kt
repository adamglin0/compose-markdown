package dev.markstream.core.api

import dev.markstream.core.model.PlainTextBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarkdownEngineTest {
    @Test
    fun appendCreatesSinglePlaceholderBlock() {
        val engine = MarkdownEngine()

        val delta = engine.append("hello\nworld")

        assertEquals(1L, delta.version)
        assertEquals(0, delta.stablePrefixEnd)
        assertEquals(1, delta.changedBlocks.size)

        val block = assertIs<PlainTextBlock>(delta.snapshot.document.blocks.single())
        assertEquals("hello\nworld", block.text)
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
    }
}
