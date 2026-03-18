package dev.markstream.compose

import dev.markstream.core.api.MarkdownEngine
import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.InlineNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarkdownStateTest {
    @Test
    fun appendFinishAndResetUpdateSnapshot() {
        val state = MarkdownState(engine = MarkdownEngine())

        assertEquals(0L, state.snapshot.version)
        assertTrue(state.snapshot.document.blocks.isEmpty())

        state.append("hello")
        val block = assertIs<BlockNode.Paragraph>(state.snapshot.document.blocks.single())
        assertEquals("hello", assertIs<InlineNode.Text>(block.children.single()).literal)
        assertFalse(state.snapshot.isFinal)

        state.finish()
        assertTrue(state.snapshot.isFinal)
        assertEquals(5, state.snapshot.stablePrefixEnd)

        state.reset()
        assertEquals(0L, state.snapshot.version)
        assertTrue(state.snapshot.document.blocks.isEmpty())
        assertFalse(state.snapshot.isFinal)
    }
}
