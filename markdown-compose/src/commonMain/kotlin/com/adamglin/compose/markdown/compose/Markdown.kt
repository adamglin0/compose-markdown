package com.adamglin.compose.markdown.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.MarkdownDocument

/**
 * Renders a parsed Markdown [document], e.g. [MarkdownState.document] while streaming.
 *
 * Styling comes from the enclosing [MarkdownTheme]. Wrap in a `SelectionContainer` to make the
 * text selectable.
 *
 * All top-level blocks share one `Column`: blocks a streamed chunk did not change skip
 * recomposition, but every update still checks, measures and draws each top-level block. For long
 * documents use [LazyListScope.markdown], which costs only the visible changed blocks.
 *
 * @param codeHighlighter colors closed fenced code blocks; `null` disables highlighting. Pass a
 *   long-lived instance: highlight results are cached per highlighter instance.
 * @param mathRenderer renders LaTeX; `null` shows math source text. Pass a long-lived instance
 *   (e.g. `remember`ed): a new instance recomposes every block.
 * @param components renders each kind of element; the defaults are the built-in rendering.
 * @param onLinkClick handles link clicks; `null` opens links with `LocalUriHandler`.
 */
@Composable
public fun Markdown(
    document: MarkdownDocument,
    modifier: Modifier = Modifier,
    codeHighlighter: CodeHighlighter? = DefaultCodeHighlighter,
    mathRenderer: MathRenderer? = null,
    components: MarkdownComponents = MarkdownComponents(),
    onLinkClick: ((url: String) -> Unit)? = null,
) {
    val context = rememberMarkdownRenderContext(codeHighlighter, mathRenderer, components, onLinkClick)
    MarkdownBlocks(
        blocks = document.blocks,
        context = context,
        listDepth = 0,
        spacing = context.blockSpacing,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Parses [text] (once per distinct text) and renders it. See the [MarkdownDocument] overload. */
@Composable
public fun Markdown(
    text: String,
    modifier: Modifier = Modifier,
    dialect: MarkdownDialect = MarkdownDialect.ChatFast,
    codeHighlighter: CodeHighlighter? = DefaultCodeHighlighter,
    mathRenderer: MathRenderer? = null,
    components: MarkdownComponents = MarkdownComponents(),
    onLinkClick: ((url: String) -> Unit)? = null,
) {
    Markdown(
        document = rememberMarkdownDocument(text, dialect),
        modifier = modifier,
        codeHighlighter = codeHighlighter,
        mathRenderer = mathRenderer,
        components = components,
        onLinkClick = onLinkClick,
    )
}

/**
 * Adds the top-level blocks of [document] as lazy items keyed by their stable block ids, so only
 * visible blocks compose and streamed updates recompose only the blocks that changed.
 *
 * @param key identifies [document] among the other items of the list (e.g. a message id); item
 *   keys are `"$key:<block id>"`, so every document in one list needs a distinct key.
 * @see Markdown for the other parameters.
 */
public fun LazyListScope.markdown(
    document: MarkdownDocument,
    key: Any,
    codeHighlighter: CodeHighlighter? = DefaultCodeHighlighter,
    mathRenderer: MathRenderer? = null,
    components: MarkdownComponents = MarkdownComponents(),
    onLinkClick: ((url: String) -> Unit)? = null,
) {
    itemsIndexed(
        items = document.blocks,
        key = { _, block -> "$key:${block.id.raw}" },
        contentType = { _, block -> block::class },
    ) { index, block ->
        val context = rememberMarkdownRenderContext(codeHighlighter, mathRenderer, components, onLinkClick)
        Box(Modifier.padding(top = if (index == 0) 0.dp else context.blockSpacing)) {
            MarkdownBlock(block = block, context = context, listDepth = 0)
        }
    }
}
