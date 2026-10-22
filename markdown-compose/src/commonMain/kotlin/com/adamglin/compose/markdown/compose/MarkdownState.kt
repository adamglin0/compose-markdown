package com.adamglin.compose.markdown.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.MarkdownDocument
import com.adamglin.compose.markdown.core.model.MarkdownSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Holds a streamed Markdown document.
 *
 * [append] and [finish] feed the owned [MarkdownEngine] and publish its snapshot document
 * synchronously, without copying it: top-level blocks a chunk did not change are the same instances
 * as before, so the renderer only recomposes the blocks the chunk touched, and an append costs the
 * engine's work on the new text, not the length of the document. The class is plain observable
 * state: it can live in composition ([rememberMarkdownState]) or in a view model.
 *
 * With `holdUnsettledLines`, a line appears only once its block structure is settled, so streamed tables do
 * not reflow on every chunk and a lone `-` or `|` line does not briefly restyle the content above it; see
 * [MarkdownEngine]. While held text grows, [document] stays the same instance, so nothing recomposes.
 * [releaseHeldLinesOnTimeout] shows held lines that wait too long; [rememberMarkdownState] runs it, and a
 * state created elsewhere, such as in a view model, runs it in its own scope.
 *
 * Call [append], [finish], [reset] and [releaseHeldLinesOnTimeout] from one thread at a time: the engine is
 * not thread-safe. [rememberMarkdownState] runs the timeout in composition, on the main thread, so with
 * `holdUnsettledLines` call the others on the main thread too. A published [document] never changes
 * afterwards, so it can be read from any thread.
 */
@Stable
public class MarkdownState(
    dialect: MarkdownDialect = MarkdownDialect.ChatFast,
    holdUnsettledLines: Boolean = false,
) {
    private val engine = MarkdownEngine(dialect, holdUnsettledLines)

    // Snapshots are immutable and a call that changes nothing returns the same document, so identity
    // is equality here; structural equality would compare every block on each append.
    public var document: MarkdownDocument by mutableStateOf(engine.snapshot().document, referentialEqualityPolicy())
        private set

    /**
     * The held lines, or null when none are held; read only by [releaseHeldLinesOnTimeout]. A new instance
     * restarts its wait, so it is published only when the held position changes or the content is reset.
     */
    private var held: HeldLines? by mutableStateOf(null)
    private var session = 0

    /** Appends a chunk of Markdown source. Invalid after [finish] until [reset]. */
    public fun append(chunk: String) {
        publish(engine.append(chunk).snapshot)
    }

    /** Marks the end of input so open constructs (fences, math) close. */
    public fun finish() {
        publish(engine.finish().snapshot)
    }

    /** Drops all content so a new document can be streamed. */
    public fun reset() {
        engine.reset()
        session += 1
        publish(engine.snapshot())
    }

    /**
     * Shows held lines once they have waited [timeout] without settling; the wait starts again whenever the
     * held lines settle and later ones are held. Runs until cancelled.
     */
    public suspend fun releaseHeldLinesOnTimeout(timeout: Duration = DefaultHoldTimeout) {
        snapshotFlow { held }.collectLatest { held ->
            if (held != null) {
                delay(timeout)
                publish(engine.releaseHeldLines().snapshot)
            }
        }
    }

    private fun publish(snapshot: MarkdownSnapshot) {
        document = snapshot.document
        held = if (snapshot.hasHeldLines) HeldLines(session = session, start = snapshot.document.sourceLength) else null
    }

    private data class HeldLines(val session: Int, val start: Int)

    public companion object {
        /** How long [releaseHeldLinesOnTimeout] lets held lines wait by default. */
        public val DefaultHoldTimeout: Duration = 500.milliseconds
    }
}

/**
 * A [MarkdownState] for composition. With [holdUnsettledLines] it also runs
 * [MarkdownState.releaseHeldLinesOnTimeout] with [holdTimeout]; changing the timeout keeps the content.
 */
@Composable
public fun rememberMarkdownState(
    dialect: MarkdownDialect = MarkdownDialect.ChatFast,
    holdUnsettledLines: Boolean = false,
    holdTimeout: Duration = MarkdownState.DefaultHoldTimeout,
): MarkdownState {
    val state = remember(dialect, holdUnsettledLines) { MarkdownState(dialect, holdUnsettledLines) }
    if (holdUnsettledLines) {
        LaunchedEffect(state, holdTimeout) { state.releaseHeldLinesOnTimeout(holdTimeout) }
    }
    return state
}

/** Parses [text] once per distinct [text]/[dialect] pair. */
@Composable
public fun rememberMarkdownDocument(
    text: String,
    dialect: MarkdownDialect = MarkdownDialect.ChatFast,
): MarkdownDocument = remember(text, dialect) { parseMarkdown(text, dialect) }

internal fun parseMarkdown(text: String, dialect: MarkdownDialect): MarkdownDocument {
    val engine = MarkdownEngine(dialect)
    engine.append(text)
    return engine.finish().snapshot.document
}
