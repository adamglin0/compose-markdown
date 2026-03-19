package dev.markstream.sample.chat

import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.InlineNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SampleChatDefaultsTest {
    @Test
    fun createScriptsBuildsBundledExamples() {
        val scripts = SampleChatDefaults.createScripts { path ->
            when (path) {
                "markdown-examples/full-markdown.md" -> "# Full Markdown\n\nA bundled document."
                "markdown-examples/chat-streaming.md" -> "# Chat\n\nHello stream."
                "markdown-examples/quotes-and-lists.md" -> "# Quotes\n\n> Quote"
                "markdown-examples/tables-and-tasks.md" -> "# Tables\n\n- [x] Done"
                "markdown-examples/reference-links.md" -> "# Refs\n\n[doc][1]\n\n[1]: https://example.com"
                "markdown-examples/engineering-deep-dive.md" -> "# Engineering Deep Dive\n\nA realistic long-form article."
                "markdown-examples/progressive-code-fence.md" -> "# Fence\n\n```kotlin\nprintln(1)\n```"
                else -> error("unexpected path: $path")
            }
        }

        assertEquals(7, scripts.size)
        assertEquals("full-markdown", scripts.first().id)
        assertTrue(scripts.all { it.message.isNotBlank() })
    }

    @Test
    fun finalSnapshotProvidesExpectedBlocks() {
        val snapshot = SampleChatDefaults.finalSnapshot(
            message = "# Sample\n\n```kotlin\nprintln(1)\n```",
        )

        assertTrue(snapshot.isFinal)
        val heading = assertIs<BlockNode.Heading>(snapshot.document.blocks.first())
        assertEquals(1, heading.level)
        assertTrue(snapshot.toDebugText().contains("FencedCodeBlock"))
    }

    @Test
    fun createStreamingChunksSplitsMessageIntoMultiplePieces() {
        val message = """
            # Progressive code fence

            ```text
            chunk 1
            chunk 2
            chunk 3
            ```
        """.trimIndent()

        val chunks = SampleChatDefaults.createStreamingChunks(
            message = message,
            targetChunkSize = 14,
        )

        assertTrue(chunks.size > 3)
        assertEquals(message, chunks.joinToString(separator = ""))
        assertTrue(chunks.any { it.contains("```") })
    }

    @Test
    fun finalSnapshotResolvesReferenceLinksWithSampleDialect() {
        val snapshot = SampleChatDefaults.finalSnapshot(
            message = "See [guide][docs]\n\n[docs]: https://example.com/guide",
        )

        assertEquals(1, snapshot.document.blocks.size)
        val paragraph = assertIs<BlockNode.Paragraph>(snapshot.document.blocks.single())
        val link = paragraph.children.filterIsInstance<InlineNode.Link>().single()
        assertEquals("https://example.com/guide", link.destination)
        assertFalse(snapshot.toDebugText().contains("[docs]: https://example.com/guide"))
    }
}
