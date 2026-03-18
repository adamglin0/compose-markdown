package dev.markstream.sample.chat

import dev.markstream.core.model.PlainTextBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SampleChatDefaultsTest {
    @Test
    fun placeholderSnapshotProvidesDefaultContent() {
        val snapshot = SampleChatDefaults.placeholderSnapshot()

        assertTrue(snapshot.isFinal)
        val block = assertIs<PlainTextBlock>(snapshot.document.blocks.single())
        assertEquals(SampleChatDefaults.initialMessage, block.text)
        assertTrue(block.text.contains("Stage 1 scaffold checkpoint"))
    }
}
