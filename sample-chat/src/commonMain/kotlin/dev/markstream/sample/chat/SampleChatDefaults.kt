package dev.markstream.sample.chat

import dev.markstream.core.api.MarkdownEngine
import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.model.MarkdownSnapshot

object SampleChatDefaults {
    private val exampleDefinitions: List<SampleScriptDefinition> = listOf(
        SampleScriptDefinition(
            id = "full-markdown",
            title = "Full Markdown",
            summary = "完整长文档，适合检查长滚动浏览与综合渲染表现。",
            resourcePath = "markdown-examples/full-markdown.md",
        ),
        SampleScriptDefinition(
            id = "chat-streaming",
            title = "Chat Streaming",
            summary = "模拟聊天回复逐 chunk 生成，覆盖段落、列表、引用与链接。",
            resourcePath = "markdown-examples/chat-streaming.md",
        ),
        SampleScriptDefinition(
            id = "quotes-and-lists",
            title = "Quotes and Lists",
            summary = "覆盖引用、嵌套列表与长段落，验证结构稳定性。",
            resourcePath = "markdown-examples/quotes-and-lists.md",
        ),
        SampleScriptDefinition(
            id = "tables-and-tasks",
            title = "Tables and Tasks",
            summary = "覆盖表格、task list 与结构化信息块。",
            resourcePath = "markdown-examples/tables-and-tasks.md",
        ),
        SampleScriptDefinition(
            id = "reference-links",
            title = "Reference Links",
            summary = "链接定义延后出现，用来验证 reference-style links。",
            resourcePath = "markdown-examples/reference-links.md",
        ),
        SampleScriptDefinition(
            id = "engineering-deep-dive",
            title = "Engineering Deep Dive",
            summary = "真实文章型示例，覆盖常见 Markdown 语法与验收场景。",
            resourcePath = "markdown-examples/engineering-deep-dive.md",
        ),
        SampleScriptDefinition(
            id = "progressive-code-fence",
            title = "Progressive Code Fence",
            summary = "代码块在流末尾才闭合，确认后续段落仍能继续渲染。",
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
