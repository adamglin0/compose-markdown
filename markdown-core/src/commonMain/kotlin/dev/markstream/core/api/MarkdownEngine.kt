package dev.markstream.core.api

import dev.markstream.core.engine.PlaceholderMarkdownEngine
import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.model.MarkdownSnapshot
import dev.markstream.core.model.ParseDelta

interface MarkdownEngine {
    val dialect: MarkdownDialect

    fun append(chunk: String): ParseDelta

    fun finish(): ParseDelta

    fun snapshot(): MarkdownSnapshot

    fun reset()
}

fun MarkdownEngine(dialect: MarkdownDialect = MarkdownDialect.ChatFast): MarkdownEngine =
    PlaceholderMarkdownEngine(dialect = dialect)
