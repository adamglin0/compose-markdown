package dev.markstream.sample.chat

import dev.markstream.core.model.BlockNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SampleChatDefaultsTest {
    @Test
    fun finalSnapshotProvidesDefaultContent() {
        val snapshot = SampleChatDefaults.finalSnapshot()

        assertTrue(snapshot.isFinal)
        val heading = assertIs<BlockNode.Heading>(snapshot.document.blocks.first())
        assertEquals(1, heading.level)
        assertTrue(snapshot.toDebugText().contains("FencedCodeBlock"))
    }

    @Test
    fun createStreamingChunksSplitsMessageIntoMultiplePieces() {
        val script = SampleChatDefaults.scripts.first { it.id == "open-code-fence" }
        val chunks = SampleChatDefaults.createStreamingChunks(
            message = script.message,
            targetChunkSize = 14,
        )

        assertTrue(chunks.size > 3)
        assertEquals(script.message, chunks.joinToString(separator = ""))
        assertTrue(chunks.any { it.contains("```") })
    }
}
