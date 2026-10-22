package com.adamglin.compose.markdown.core.api

import com.adamglin.compose.markdown.core.engine.IncrementalMarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.MarkdownSnapshot
import com.adamglin.compose.markdown.core.model.ParseDelta

public interface MarkdownEngine {
    public val dialect: MarkdownDialect

    /**
     * Appends source text to the current session tail.
     *
     * Calling [append] after [finish] is invalid until [reset] is called or a new engine is created.
     */
    public fun append(chunk: String): ParseDelta

    /**
     * Marks the current source as end-of-input for this session.
     */
    public fun finish(): ParseDelta

    /**
     * Shows the first line that is held back as unsettled (see `holdUnsettledLines`) right away, with what
     * follows it unless that is held for its own reason. A released line stays shown while it grows; later
     * lines can be held again. Does nothing when no line is held.
     */
    public fun releaseHeldLines(): ParseDelta

    public fun snapshot(): MarkdownSnapshot

    public fun reset()
}

/**
 * Creates a streaming engine for [dialect].
 *
 * By default every snapshot equals a one-shot parse of the source so far. With [holdUnsettledLines], lines
 * whose block structure the next characters or line commonly change are hidden until that is settled, so the
 * rendered structure does not jump while streaming:
 * - an unterminated line that is a table row stays hidden until its `\n` arrives, so a table changes once
 *   per row instead of once per chunk;
 * - an unterminated line that is still only block markers (`-`, `#`, `>`, `1.`, a fence, `$$`, …) stays
 *   hidden until it has content, so a lone `-` never shows the paragraph above as a setext heading;
 * - a paragraph line starting with `|` stays hidden until the next line tells whether it is a table header
 *   (a header written without a leading `|` still shows as a paragraph until its delimiter row ends).
 *
 * The snapshot's document is then a one-shot parse of the source before the held lines, and
 * [MarkdownSnapshot.hasHeldLines] is true.
 * [MarkdownEngine.releaseHeldLines] shows a held line early, for example when it takes too long to settle, and
 * [MarkdownEngine.finish] shows everything.
 */
public fun MarkdownEngine(
    dialect: MarkdownDialect = MarkdownDialect.ChatFast,
    holdUnsettledLines: Boolean = false,
): MarkdownEngine = IncrementalMarkdownEngine(dialect = dialect, holdUnsettledLines = holdUnsettledLines)
