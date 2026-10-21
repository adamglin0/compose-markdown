package com.adamglin.compose.markdown.compose

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
 * text selectable. For long documents inside a `LazyColumn`, use [LazyListScope.markdown].
 *
 * @param codeHighlighter colors closed fenced code blocks; `null` disables highlighting.
 * @param mathRenderer renders LaTeX; `null` shows math source text.
 * @param imageContent renders images; `null` shows their alt text.
 * @param onLinkClick handles link clicks; `null` opens links with `LocalUriHandler`.
 */
@Composable
fun Markdown(
    document: MarkdownDocument,
    modifier: Modifier = Modifier,
    codeHighlighter: CodeHighlighter? = DefaultCodeHighlighter,
    mathRenderer: MathRenderer? = null,
    imageContent: (@Composable (MarkdownImage) -> Unit)? = null,
    onLinkClick: ((url: String) -> Unit)? = null,
) {
    val context = rememberMarkdownRenderContext(codeHighlighter, mathRenderer, imageContent, onLinkClick)
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
fun Markdown(
    text: String,
    modifier: Modifier = Modifier,
    dialect: MarkdownDialect = MarkdownDialect.ChatFast,
    codeHighlighter: CodeHighlighter? = DefaultCodeHighlighter,
    mathRenderer: MathRenderer? = null,
    imageContent: (@Composable (MarkdownImage) -> Unit)? = null,
    onLinkClick: ((url: String) -> Unit)? = null,
) {
    Markdown(
        document = rememberMarkdownDocument(text, dialect),
        modifier = modifier,
        codeHighlighter = codeHighlighter,
        mathRenderer = mathRenderer,
        imageContent = imageContent,
        onLinkClick = onLinkClick,
    )
}

/**
 * Adds the top-level blocks of [document] as lazy items keyed by their stable block ids, so only
 * visible blocks compose and streamed updates recompose only the blocks that changed.
 *
 * @param key prefix of the item keys; give each document in the same list a distinct key.
 * @see Markdown for the other parameters.
 */
fun LazyListScope.markdown(
    document: MarkdownDocument,
    key: String = "markdown",
    codeHighlighter: CodeHighlighter? = DefaultCodeHighlighter,
    mathRenderer: MathRenderer? = null,
    imageContent: (@Composable (MarkdownImage) -> Unit)? = null,
    onLinkClick: ((url: String) -> Unit)? = null,
) {
    itemsIndexed(
        items = document.blocks,
        key = { _, block -> "$key:${block.id.raw}" },
        contentType = { _, block -> block::class },
    ) { index, block ->
        val context = rememberMarkdownRenderContext(codeHighlighter, mathRenderer, imageContent, onLinkClick)
        MarkdownBlock(
            block = block,
            context = context,
            modifier = Modifier.padding(top = if (index == 0) 0.dp else context.blockSpacing),
        )
    }
}
