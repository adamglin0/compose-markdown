package dev.markstream.sample.chat

import dev.markstream.core.model.BlockNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SampleChatDefaultsTest {
    @Test
    fun placeholderSnapshotProvidesDefaultContent() {
        val snapshot = SampleChatDefaults.placeholderSnapshot()

        assertTrue(snapshot.isFinal)
        val block = assertIs<BlockNode.RawTextBlock>(snapshot.document.blocks.single())
        assertEquals(SampleChatDefaults.initialMessage, block.literal)
        assertTrue(block.literal.contains("Stage 2 placeholder checkpoint"))
    }
}
