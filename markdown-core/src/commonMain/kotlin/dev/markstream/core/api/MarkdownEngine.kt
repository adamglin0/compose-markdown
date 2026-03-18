package dev.markstream.core.api

import dev.markstream.core.engine.PlaceholderMarkdownEngine
import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.model.MarkdownSnapshot
import dev.markstream.core.model.ParseDelta

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
    PlaceholderMarkdownEngine(dialect = dialect)
