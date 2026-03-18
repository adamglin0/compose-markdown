package dev.markstream.sample.chat

import dev.markstream.core.api.MarkdownEngine
import dev.markstream.core.model.MarkdownSnapshot

object SampleChatDefaults {
    val initialMessage: String = """
        Welcome to markstream.

        This Stage 2 placeholder checkpoint proves the current snapshot pipeline:
        - sample-chat depends on markdown-compose
        - markdown-compose depends on markdown-core
        - finish() produces the stable final snapshot shown in the preview
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
