package com.adamglin.compose.markdown.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    /** Read a slot only in the composable that invokes it, so a new slot re-runs just that composable. */
    val components: ComponentSlots,
    /** Whether [MarkdownComponents.image] is set: images are then embedded instead of shown as alt text. */
    val embedImages: Boolean,
    /** `null` keeps the platform default: links open through `LocalUriHandler`. */
    val linkListener: LinkInteractionListener?,
)

/**
 * The latest slots of [MarkdownComponents], each in its own state: a new [MarkdownComponents]
 * invalidates only the readers of the slots whose lambda changed.
 */
@Stable
internal class ComponentSlots(components: MarkdownComponents) {
    var paragraph by mutableStateOf(components.paragraph)
    var heading by mutableStateOf(components.heading)
    var codeBlock by mutableStateOf(components.codeBlock)
    var mathBlock by mutableStateOf(components.mathBlock)
    var blockQuote by mutableStateOf(components.blockQuote)
    var list by mutableStateOf(components.list)
    var listItem by mutableStateOf(components.listItem)
    var listMarker by mutableStateOf(components.listMarker)
    var taskCheckbox by mutableStateOf(components.taskCheckbox)
    var table by mutableStateOf(components.table)
    var thematicBreak by mutableStateOf(components.thematicBreak)
    var image by mutableStateOf(components.image)

    fun update(components: MarkdownComponents) {
        paragraph = components.paragraph
        heading = components.heading
        codeBlock = components.codeBlock
        mathBlock = components.mathBlock
        blockQuote = components.blockQuote
        list = components.list
        listItem = components.listItem
        listMarker = components.listMarker
        taskCheckbox = components.taskCheckbox
        table = components.table
        thematicBreak = components.thematicBreak
        image = components.image
    }
}

@Composable
internal fun rememberMarkdownRenderContext(
    codeHighlighter: CodeHighlighter?,
    mathRenderer: MathRenderer?,
    components: MarkdownComponents,
    onLinkClick: ((String) -> Unit)?,
): MarkdownRenderContext {
    val colors = MarkdownTheme.colors
    val typography = MarkdownTheme.typography
    val blockSpacing = MarkdownTheme.blockSpacing
    // Callers often pass fresh lambdas (always so from a LazyListScope builder, which is not
    // composable). Keying on them would rebuild the context and recompose every block on each
    // update, so they are read through state and only their presence is a key.
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
    // Like rememberUpdatedState, per slot: setting a slot to the same lambda notifies no reader.
    val slots = remember { ComponentSlots(components) }.apply { update(components) }
    val embedImages = components.image != null
    val styles = remember(colors, typography) { MarkdownStyles(colors, typography) }
    return remember(colors, styles, blockSpacing, codeHighlighter, mathRenderer, embedImages, linkListener) {
        MarkdownRenderContext(
            colors = colors,
            styles = styles,
            blockSpacing = blockSpacing,
            codeHighlighter = codeHighlighter,
            mathRenderer = mathRenderer,
            components = slots,
            embedImages = embedImages,
            linkListener = linkListener,
        )
    }
}
