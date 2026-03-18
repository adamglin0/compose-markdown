package dev.markstream.sample.chat

import dev.markstream.core.api.MarkdownEngine
import dev.markstream.core.model.MarkdownSnapshot

object SampleChatDefaults {
    val initialMessage: String = """
        Welcome to markstream.

        This Stage 1 scaffold checkpoint only proves the module wiring:
        - sample-chat depends on markdown-compose
        - markdown-compose depends on markdown-core
        - the preview shows one plain-text placeholder block
    """.trimIndent()

    fun placeholderSnapshot(
        message: String = initialMessage,
        engineFactory: () -> MarkdownEngine = { MarkdownEngine() },
    ): MarkdownSnapshot {
        val engine = engineFactory()

        if (message.isNotEmpty()) {
            engine.append(message)
        }

        return engine.finish().snapshot
    }
}
