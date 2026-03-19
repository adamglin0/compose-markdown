package com.adamglin.compose.markdown.core.api

import com.adamglin.compose.markdown.core.engine.IncrementalMarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.MarkdownSnapshot
import com.adamglin.compose.markdown.core.model.ParseDelta

interface MarkdownEngine {
    val dialect: MarkdownDialect

    /**
     * Appends source text to the current session tail.
     *
     * Calling [append] after [finish] is invalid until [reset] is called or a new engine is created.
     */
    fun append(chunk: String): ParseDelta

    /**
     * Marks the current source as end-of-input for this session.
     */
    fun finish(): ParseDelta

    fun snapshot(): MarkdownSnapshot

    fun reset()
}

fun MarkdownEngine(dialect: MarkdownDialect = MarkdownDialect.ChatFast): MarkdownEngine =
    IncrementalMarkdownEngine(dialect = dialect)
