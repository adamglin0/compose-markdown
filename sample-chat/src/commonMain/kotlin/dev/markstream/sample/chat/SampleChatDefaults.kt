package dev.markstream.sample.chat

import dev.markstream.core.api.MarkdownEngine
import dev.markstream.core.model.MarkdownSnapshot

object SampleChatDefaults {
    val initialMessage: String = """
        # Welcome to markstream

        Stage 3 block parser debug snapshot:
        - heading
        - paragraph
        - list item

        > quoted tail
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
