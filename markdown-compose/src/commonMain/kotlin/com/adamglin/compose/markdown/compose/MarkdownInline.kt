package com.adamglin.compose.markdown.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.em
import com.adamglin.compose.markdown.core.model.InlineNode

/** An image referenced by the document, handed to the `imageContent` slot of the renderer. */
@Immutable
data class MarkdownImage(
    val url: String,
    val altText: String,
    val title: String?,
)

/** A composable embedded in a line of text (inline math or an image). */
@Immutable
internal sealed interface InlineEmbed {
    val key: String

    data class Math(override val key: String, val latex: String) : InlineEmbed

    data class Image(override val key: String, val image: MarkdownImage) : InlineEmbed
}

@Immutable
internal data class InlineRenderModel(
    val text: AnnotatedString,
    val embeds: List<InlineEmbed>,
)

@Composable
internal fun rememberInlineRenderModel(
    nodes: List<InlineNode>,
    context: MarkdownRenderContext,
): InlineRenderModel {
    val embedMath = context.mathRenderer != null
    val embedImages = context.imageContent != null
    return remember(nodes, context.styles, context.linkListener, embedMath, embedImages) {
        nodes.toInlineRenderModel(context.styles.inline, context.linkListener, embedMath, embedImages)
    }
}

internal fun List<InlineNode>.toInlineRenderModel(
    styles: MarkdownInlineStyles,
    linkListener: LinkInteractionListener?,
    embedMath: Boolean,
    embedImages: Boolean,
): InlineRenderModel {
    val embeds = mutableListOf<InlineEmbed>()
    val text = buildAnnotatedString {
        InlineBuilder(this, styles, linkListener, embedMath, embedImages, embeds).append(this@toInlineRenderModel)
    }
    return InlineRenderModel(text = text, embeds = embeds)
}

private class InlineBuilder(
    private val builder: AnnotatedString.Builder,
    private val styles: MarkdownInlineStyles,
    private val linkListener: LinkInteractionListener?,
    private val embedMath: Boolean,
    private val embedImages: Boolean,
    private val embeds: MutableList<InlineEmbed>,
) {
    fun append(nodes: List<InlineNode>): Unit = with(builder) {
        nodes.forEach { node ->
            when (node) {
                is InlineNode.Text -> append(node.literal)
                is InlineNode.UnsupportedInline -> append(node.literal)
                is InlineNode.SoftBreak, is InlineNode.HardBreak -> append("\n")
                is InlineNode.CodeSpan -> withStyle(styles.code) { append(node.literal) }
                is InlineNode.Emphasis -> withStyle(styles.emphasis) { append(node.children) }
                is InlineNode.Strong -> withStyle(styles.strong) { append(node.children) }
                is InlineNode.Strikethrough -> withStyle(styles.strike) { append(node.children) }
                is InlineNode.Link -> withLink(
                    LinkAnnotation.Url(
                        url = node.destination,
                        styles = styles.link,
                        linkInteractionListener = linkListener,
                    ),
                ) { append(node.children) }

                is InlineNode.MathSpan -> if (embedMath) {
                    val key = "math:${embeds.size}"
                    embeds += InlineEmbed.Math(key, node.latex)
                    appendInlineContent(key, node.latex)
                } else {
                    withStyle(styles.code) { append(node.latex) }
                }

                is InlineNode.Image -> if (embedImages) {
                    val key = "image:${embeds.size}"
                    val altText = node.alt.plainText()
                    embeds += InlineEmbed.Image(key, MarkdownImage(node.destination, altText, node.title))
                    appendInlineContent(key, altText)
                } else {
                    append(node.alt)
                }
            }
        }
    }
}

internal fun List<InlineNode>.plainText(): String = buildString {
    fun visit(nodes: List<InlineNode>) {
        nodes.forEach { node ->
            when (node) {
                is InlineNode.Text -> append(node.literal)
                is InlineNode.UnsupportedInline -> append(node.literal)
                is InlineNode.CodeSpan -> append(node.literal)
                is InlineNode.MathSpan -> append(node.latex)
                is InlineNode.SoftBreak, is InlineNode.HardBreak -> append(' ')
                is InlineNode.Emphasis -> visit(node.children)
                is InlineNode.Strong -> visit(node.children)
                is InlineNode.Strikethrough -> visit(node.children)
                is InlineNode.Link -> visit(node.children)
                is InlineNode.Image -> visit(node.alt)
            }
        }
    }
    visit(this@plainText)
}

@Composable
internal fun MarkdownInlineText(
    nodes: List<InlineNode>,
    style: TextStyle,
    context: MarkdownRenderContext,
    modifier: Modifier = Modifier,
) {
    val model = rememberInlineRenderModel(nodes, context)
    if (model.embeds.isEmpty()) {
        BasicText(text = model.text, style = style, modifier = modifier)
    } else {
        EmbeddingText(model = model, style = style, context = context, modifier = modifier)
    }
}

/**
 * Text with embedded composables. Each embed is first composed invisibly, measured at its natural
 * size (capped to the text width), and its placeholder is then sized to exactly that measurement.
 */
@Composable
private fun EmbeddingText(
    model: InlineRenderModel,
    style: TextStyle,
    context: MarkdownRenderContext,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val measuredSizes = remember(model) { mutableStateMapOf<String, IntSize>() }
    var maxWidth by remember { mutableIntStateOf(Constraints.Infinity) }
    val sizes = measuredSizes.toMap()
    val inlineContent = remember(model, style, context, density, sizes) {
        model.embeds.associate { embed ->
            val size = sizes[embed.key]
            val placeholder = if (size == null) {
                Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)
            } else {
                with(density) { Placeholder(size.width.toSp(), size.height.toSp(), PlaceholderVerticalAlign.TextCenter) }
            }
            embed.key to InlineTextContent(placeholder) {
                MeasuredEmbed(
                    maxWidth = { maxWidth },
                    isMeasured = size != null,
                    onMeasured = { measured -> if (measuredSizes[embed.key] != measured) measuredSizes[embed.key] = measured },
                ) {
                    when (embed) {
                        is InlineEmbed.Math -> context.mathRenderer?.InlineMath(embed.latex, style)
                        is InlineEmbed.Image -> context.imageContent?.invoke(embed.image)
                    }
                }
            }
        }
    }
    BasicText(
        text = model.text,
        style = style,
        inlineContent = inlineContent,
        modifier = modifier.layout { measurable, constraints ->
            if (maxWidth != constraints.maxWidth) maxWidth = constraints.maxWidth
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        },
    )
}

@Composable
private fun MeasuredEmbed(
    maxWidth: () -> Int,
    isMeasured: Boolean,
    onMeasured: (IntSize) -> Unit,
    content: @Composable () -> Unit,
) {
    Layout(
        content = { Box(Modifier.onSizeChanged(onMeasured)) { content() } },
        modifier = if (isMeasured) Modifier else Modifier.alpha(0f),
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(Constraints(maxWidth = maxWidth()))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(
                x = (constraints.maxWidth - placeable.width) / 2,
                y = (constraints.maxHeight - placeable.height) / 2,
            )
        }
    }
}
