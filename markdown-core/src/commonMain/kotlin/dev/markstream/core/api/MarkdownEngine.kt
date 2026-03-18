package dev.markstream.core.api

import dev.markstream.core.engine.PlaceholderMarkdownEngine
import dev.markstream.core.model.ParseDelta
import dev.markstream.core.model.MarkdownSnapshot

interface MarkdownEngine {
    fun append(chunk: String): ParseDelta

    fun finish(): ParseDelta

    fun snapshot(): MarkdownSnapshot

    fun reset()
}

fun MarkdownEngine(): MarkdownEngine = PlaceholderMarkdownEngine()
