package com.adamglin.compose.markdown.core.dialect

/**
 * The Markdown syntax a parser session accepts. Every dialect parses CommonMark block structure; they differ
 * in the GFM and math extensions they enable and in whether link reference definitions are resolved.
 */
public sealed class MarkdownDialect(
    internal val blockFeatures: MarkdownBlockFeatures,
    internal val inlineFeatures: MarkdownInlineFeatures,
) {
    /**
     * GFM without link reference definitions: `[label]: url` lines stay paragraph text, so a streamed
     * reference never rewrites links that were already rendered.
     */
    public data object ChatFast : MarkdownDialect(
        blockFeatures = MarkdownBlockFeatures(taskListItems = true, tables = true),
        inlineFeatures = MarkdownInlineFeatures(
            strikethrough = true,
            referenceLinks = false,
            bareAutolinks = true,
        ),
    )

    /** CommonMark only: no tables, task list items, strikethrough or bare autolinks. */
    public data object CommonMarkCore : MarkdownDialect(
        blockFeatures = MarkdownBlockFeatures(taskListItems = false, tables = false),
        inlineFeatures = MarkdownInlineFeatures(
            strikethrough = false,
            referenceLinks = true,
            bareAutolinks = false,
        ),
    )

    /** CommonMark plus the GFM tables, task list items, strikethrough and extended autolinks. */
    public data object GfmCompat : MarkdownDialect(
        blockFeatures = MarkdownBlockFeatures(taskListItems = true, tables = true),
        inlineFeatures = MarkdownInlineFeatures(
            strikethrough = true,
            referenceLinks = true,
            bareAutolinks = true,
        ),
    )

    /** [GfmCompat] plus LaTeX math: `$$…$$` / `\[…\]` blocks and `$…$` / `\(…\)` spans. */
    public data object GfmMath : MarkdownDialect(
        blockFeatures = MarkdownBlockFeatures(taskListItems = true, tables = true, mathBlocks = true),
        inlineFeatures = MarkdownInlineFeatures(
            strikethrough = true,
            referenceLinks = true,
            bareAutolinks = true,
            inlineMath = true,
        ),
    )
}

/**
 * Block constructs that differ between dialects. Paragraphs, headings, code blocks, block quotes, lists and
 * thematic breaks are part of every dialect.
 */
internal data class MarkdownBlockFeatures(
    val taskListItems: Boolean,
    val tables: Boolean,
    val mathBlocks: Boolean = false,
)

/**
 * Inline constructs that differ between dialects. Code spans, emphasis, inline links, images, autolinks and
 * line breaks are part of every dialect.
 */
internal data class MarkdownInlineFeatures(
    val strikethrough: Boolean,
    val referenceLinks: Boolean,
    val bareAutolinks: Boolean,
    val inlineMath: Boolean = false,
)
