package com.adamglin.compose.markdown.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.MarkdownDocument

/**
 * Holds a streamed Markdown document.
 *
 * [append] and [finish] feed the owned [MarkdownEngine] and publish its snapshot document
 * synchronously, without copying it: top-level blocks a chunk did not change are the same instances
 * as before, so the renderer only recomposes the blocks the chunk touched, and an append costs the
 * engine's work on the new text, not the length of the document. The class is plain observable
 * state: it can live in composition ([rememberMarkdownState]) or in a view model.
 */
@Stable
class MarkdownState(dialect: MarkdownDialect = MarkdownDialect.ChatFast) {
    private val engine = MarkdownEngine(dialect)

    // Snapshots are immutable and a call that changes nothing returns the same document, so identity
    // is equality here; structural equality would compare every block on each append.
    var document: MarkdownDocument by mutableStateOf(engine.snapshot().document, referentialEqualityPolicy())
        private set

    /** Appends a chunk of Markdown source. Invalid after [finish] until [reset]. */
    fun append(chunk: String) {
        document = engine.append(chunk).snapshot.document
    }

    /** Marks the end of input so open constructs (fences, math) close. */
    fun finish() {
        document = engine.finish().snapshot.document
    }

    /** Drops all content so a new document can be streamed. */
    fun reset() {
        engine.reset()
        document = engine.snapshot().document
    }
}

@Composable
fun rememberMarkdownState(dialect: MarkdownDialect = MarkdownDialect.ChatFast): MarkdownState =
    remember(dialect) { MarkdownState(dialect) }

/** Parses [text] once per distinct [text]/[dialect] pair. */
@Composable
fun rememberMarkdownDocument(
    text: String,
    dialect: MarkdownDialect = MarkdownDialect.ChatFast,
): MarkdownDocument = remember(text, dialect) { parseMarkdown(text, dialect) }

internal fun parseMarkdown(text: String, dialect: MarkdownDialect): MarkdownDocument {
    val engine = MarkdownEngine(dialect)
    engine.append(text)
    return engine.finish().snapshot.document
}
