package dev.markstream.sample.chat

import dev.markstream.core.api.MarkdownEngine
import dev.markstream.core.model.MarkdownSnapshot

object SampleChatDefaults {
    val initialMessage: String = """
        # Welcome to markstream

        Stage 5 incremental delta/stats debug surface:
        - append text to inspect dirty region
        - watch preserved vs reparsed blocks
        - compare inline cache hits and delta output

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
