package com.adamglin.compose.markdown.core.block

import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.BlockId
import com.adamglin.compose.markdown.core.source.LineIndex
import com.adamglin.compose.markdown.core.source.SourceBuffer
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockParserOpenFrameTest {
    @Test
    fun nestedOpenTailKeepsNestedFrames() {
        val source = "> ```\n> code"
        val sourceBuffer = SourceBuffer().apply { append(source) }
        val lineIndex = LineIndex().apply { append(chunk = source, startOffset = 0) }

        val result = BlockParser(
            sourceBuffer = sourceBuffer,
            lineIndex = lineIndex,
            dialect = MarkdownDialect.ChatFast,
            allocateBlockId = { _, _, _ -> BlockId(1L) },
        ).parse(isFinal = false)

        assertEquals(listOf("blockquote", "fenced-code"), result.openBlockStack.map { it.marker })
    }
}
