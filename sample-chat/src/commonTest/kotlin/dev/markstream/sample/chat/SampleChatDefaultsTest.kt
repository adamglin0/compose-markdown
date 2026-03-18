package dev.markstream.sample.chat

import dev.markstream.core.model.BlockNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SampleChatDefaultsTest {
    @Test
    fun placeholderSnapshotProvidesDefaultContent() {
        val snapshot = SampleChatDefaults.placeholderSnapshot()

        assertTrue(snapshot.isFinal)
        val heading = assertIs<BlockNode.Heading>(snapshot.document.blocks.first())
        assertEquals(1, heading.level)
        assertNotNull(snapshot.toDebugText())
        assertTrue(snapshot.toDebugText().contains("BlockQuote"))
    }
}
