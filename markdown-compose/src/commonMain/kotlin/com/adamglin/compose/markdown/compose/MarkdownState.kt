package com.adamglin.compose.markdown.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.MarkdownDocument
import com.adamglin.compose.markdown.core.model.MarkdownSnapshot

/**
 * Holds a streamed Markdown document.
 *
 * [append] and [finish] feed the owned [MarkdownEngine] and publish the new [document]
 * synchronously. Blocks that did not change keep their previous instances, so the renderer only
 * recomposes the blocks a chunk touched. The class is plain observable state: it can live in
 * composition ([rememberMarkdownState]) or in a view model.
 */
@Stable
class MarkdownState(dialect: MarkdownDialect = MarkdownDialect.ChatFast) {
    private val engine = MarkdownEngine(dialect)

    var document: MarkdownDocument by mutableStateOf(engine.snapshot().document)
        private set

    /** Appends a chunk of Markdown source. Invalid after [finish] until [reset]. */
    fun append(chunk: String) {
        publish(engine.append(chunk).snapshot)
    }

    /** Marks the end of input so open constructs (fences, math) close. */
    fun finish() {
        publish(engine.finish().snapshot)
    }

    /** Drops all content so a new document can be streamed. */
    fun reset() {
        engine.reset()
        document = engine.snapshot().document
    }

    private fun publish(snapshot: MarkdownSnapshot) {
        val next = snapshot.document
        document = next.copy(blocks = reuseUnchangedBlocks(previous = document.blocks, next = next.blocks))
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

/**
 * Returns [next] with every block that is equal to the previous block of the same id replaced by
 * that previous instance. Preserved blocks are usually the same instance already, so the equality
 * check short-circuits; only re-parsed tail blocks are compared structurally.
 */
internal fun reuseUnchangedBlocks(previous: List<BlockNode>, next: List<BlockNode>): List<BlockNode> {
    if (previous.isEmpty()) return next
    val previousById = previous.associateBy { it.id }
    return next.map { block ->
        val old = previousById[block.id]
        if (old != null && old == block) old else block
    }
}
