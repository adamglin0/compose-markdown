package com.adamglin.compose.markdown.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp

/** Resolved text styles: [MarkdownTypography] with [MarkdownColors] applied. */
@Immutable
internal class MarkdownStyles(colors: MarkdownColors, typography: MarkdownTypography) {
    private val headings = (1..6).map { level -> typography.heading(level).withColor(colors.text) }
    val body: TextStyle = typography.body.withColor(colors.text)
    val table: TextStyle = typography.table.withColor(colors.text)
    val tableHeader: TextStyle = table.merge(TextStyle(fontWeight = FontWeight.SemiBold))
    val code: TextStyle = typography.code.withColor(colors.text)
    val label: TextStyle = typography.label.withColor(colors.textSecondary)
    val inline: MarkdownInlineStyles = MarkdownInlineStyles(
        emphasis = SpanStyle(fontStyle = FontStyle.Italic),
        strong = SpanStyle(fontWeight = FontWeight.Bold),
        strike = SpanStyle(textDecoration = TextDecoration.LineThrough),
        // No fontSize: inline code inherits the size of the heading, paragraph or cell around it.
        code = SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surface),
        link = TextLinkStyles(style = SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline)),
    )

    fun heading(level: Int): TextStyle = headings[level.coerceIn(1, 6) - 1]
}

private fun TextStyle.withColor(color: Color): TextStyle = TextStyle(color = color).merge(this)

@Immutable
internal data class MarkdownInlineStyles(
    val emphasis: SpanStyle,
    val strong: SpanStyle,
    val strike: SpanStyle,
    val code: SpanStyle,
    val link: TextLinkStyles,
)

/** Everything a block renderer needs; created once per [Markdown] call or lazy item. */
@Stable
internal class MarkdownRenderContext(
    val colors: MarkdownColors,
    val styles: MarkdownStyles,
    val blockSpacing: Dp,
    val codeHighlighter: CodeHighlighter?,
    val mathRenderer: MathRenderer?,
    val imageContent: (@Composable (MarkdownImage) -> Unit)?,
    /** `null` keeps the platform default: links open through `LocalUriHandler`. */
    val linkListener: LinkInteractionListener?,
)

@Composable
internal fun rememberMarkdownRenderContext(
    codeHighlighter: CodeHighlighter?,
    mathRenderer: MathRenderer?,
    imageContent: (@Composable (MarkdownImage) -> Unit)?,
    onLinkClick: ((String) -> Unit)?,
): MarkdownRenderContext {
    val colors = MarkdownTheme.colors
    val typography = MarkdownTheme.typography
    val blockSpacing = MarkdownTheme.blockSpacing
    val currentOnLinkClick = rememberUpdatedState(onLinkClick)
    val hasLinkHandler = onLinkClick != null
    val linkListener = remember(hasLinkHandler) {
        if (hasLinkHandler) {
            LinkInteractionListener { link ->
                (link as? LinkAnnotation.Url)?.let { currentOnLinkClick.value?.invoke(it.url) }
            }
        } else {
            null
        }
    }
    val styles = remember(colors, typography) { MarkdownStyles(colors, typography) }
    return remember(colors, styles, blockSpacing, codeHighlighter, mathRenderer, imageContent, linkListener) {
        MarkdownRenderContext(
            colors = colors,
            styles = styles,
            blockSpacing = blockSpacing,
            codeHighlighter = codeHighlighter,
            mathRenderer = mathRenderer,
            imageContent = imageContent,
            linkListener = linkListener,
        )
    }
}
