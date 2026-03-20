package com.adamglin.compose.markdown.sample.chat

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.MarkdownSnapshot

object SampleChatDefaults {
    private val exampleDefinitions: List<SampleScriptDefinition> = listOf(
        SampleScriptDefinition(
            id = "full-markdown",
            title = "Full Markdown",
            summary = "A full long-form document for checking long-scroll browsing and overall rendering behavior.",
            resourcePath = "markdown-examples/full-markdown.md",
        ),
        SampleScriptDefinition(
            id = "chat-streaming",
            title = "Chat Streaming",
            summary = "Simulates a chat reply generated chunk by chunk, covering paragraphs, lists, quotes, and links.",
            resourcePath = "markdown-examples/chat-streaming.md",
        ),
        SampleScriptDefinition(
            id = "quotes-and-lists",
            title = "Quotes and Lists",
            summary = "Covers quotes, nested lists, and long paragraphs to verify structural stability.",
            resourcePath = "markdown-examples/quotes-and-lists.md",
        ),
        SampleScriptDefinition(
            id = "tables-and-tasks",
            title = "Tables and Tasks",
            summary = "Covers tables, task lists, and structured information blocks.",
            resourcePath = "markdown-examples/tables-and-tasks.md",
        ),
        SampleScriptDefinition(
            id = "reference-links",
            title = "Reference Links",
            summary = "Places link definitions later in the text to verify reference-style links.",
            resourcePath = "markdown-examples/reference-links.md",
        ),
        SampleScriptDefinition(
            id = "engineering-deep-dive",
            title = "Engineering Deep Dive",
            summary = "A realistic article-style example covering common Markdown syntax and acceptance scenarios.",
            resourcePath = "markdown-examples/engineering-deep-dive.md",
        ),
        SampleScriptDefinition(
            id = "progressive-code-fence",
            title = "Progressive Code Fence",
            summary = "Closes the code fence only at the end of the stream to confirm later paragraphs still render correctly.",
            resourcePath = "markdown-examples/progressive-code-fence.md",
        ),
    )

    fun createScripts(
        resourceReader: (String) -> String,
    ): List<SampleScript> = exampleDefinitions.map { definition ->
        SampleScript(
            id = definition.id,
            title = definition.title,
            summary = definition.summary,
            message = resourceReader(definition.resourcePath),
        )
    }

    fun createBundledScripts(): List<SampleScript> = createScripts(SampleChatBundledExamples::readExample)

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

    fun createMarkdownEngine(): MarkdownEngine = MarkdownEngine(
        dialect = MarkdownDialect.GfmCompat,
    )

    fun finalSnapshot(
        message: String,
        engineFactory: () -> MarkdownEngine = { createMarkdownEngine() },
    ): MarkdownSnapshot {
        val engine = engineFactory()
        createStreamingChunks(message).forEach(engine::append)
        return engine.finish().snapshot
    }
}

data class SampleScript(
    val id: String,
    val title: String,
    val summary: String,
    val message: String,
)

private data class SampleScriptDefinition(
    val id: String,
    val title: String,
    val summary: String,
    val resourcePath: String,
)
