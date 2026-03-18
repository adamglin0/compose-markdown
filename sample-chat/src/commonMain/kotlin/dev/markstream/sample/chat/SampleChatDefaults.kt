package dev.markstream.sample.chat

import dev.markstream.core.api.MarkdownEngine
import dev.markstream.core.model.MarkdownSnapshot

object SampleChatDefaults {
    val scripts: List<SampleScript> = listOf(
        SampleScript(
            id = "streaming-basics",
            title = "Streaming basics",
            message = """
                # Compose streaming demo

                This renderer updates per block instead of rebuilding one giant text node.

                - Paragraphs keep growing as chunks arrive.
                - Quotes and lists preserve stable block keys.
                - Links such as [markstream docs](https://example.com/docs) stay clickable.

                > The quoted tail should remain readable while the paragraph above changes.

                ```kotlin
                suspend fun streamReply() {
                    emit("hello")
                }
                ```
            """.trimIndent(),
        ),
        SampleScript(
            id = "quote-and-list",
            title = "Quote and list",
            message = """
                ## Incremental quote

                > Streaming text can open a quote first
                > and keep extending it over time.

                1. First item lands.
                2. Second item includes `inline code`.
                3. Third item points to [a link](https://example.com/list).
            """.trimIndent(),
        ),
        SampleScript(
            id = "open-code-fence",
            title = "Open code fence",
            message = """
                ### Fence closure

                The code block below starts open and closes near the end of the stream.

                ```text
                chunk 1
                chunk 2
                chunk 3
                ```

                Final paragraph after the fence proves later blocks still render correctly.
            """.trimIndent(),
        ),
    )

    val initialScript: SampleScript = scripts.first()

    fun createStreamingChunks(
        message: String,
        targetChunkSize: Int = 18,
    ): List<String> {
        if (message.isEmpty()) {
            return emptyList()
        }

        val normalizedTarget = targetChunkSize.coerceAtLeast(4)
        val chunks = mutableListOf<String>()
        var index = 0

        while (index < message.length) {
            val requestedEnd = (index + normalizedTarget).coerceAtMost(message.length)
            var split = requestedEnd

            while (split > index + 1 && message[split - 1] !in setOf(' ', '\n', '.', ',', ')', ']', '`')) {
                split -= 1
            }

            if (split == index + 1) {
                split = requestedEnd
            }

            chunks += message.substring(index, split)
            index = split
        }

        return chunks
    }

    fun finalSnapshot(
        message: String = initialScript.message,
        engineFactory: () -> MarkdownEngine = { MarkdownEngine() },
    ): MarkdownSnapshot {
        val engine = engineFactory()
        createStreamingChunks(message).forEach(engine::append)
        return engine.finish().snapshot
    }
}

data class SampleScript(
    val id: String,
    val title: String,
    val message: String,
)
