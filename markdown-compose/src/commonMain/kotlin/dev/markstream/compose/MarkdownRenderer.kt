package dev.markstream.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.InlineNode
import dev.markstream.core.model.MarkdownDocument
import dev.markstream.core.model.MarkdownSnapshot

@Composable
fun Markdown(
    snapshot: MarkdownSnapshot,
    modifier: Modifier = Modifier,
    state: MarkdownRendererState = rememberMarkdownRendererState(snapshot = snapshot),
    onLinkClick: (String) -> Unit = {},
) {
    LaunchedEffect(snapshot) {
        state.render(snapshot)
    }
    Markdown(
        blocks = state.blocks,
        modifier = modifier,
        onLinkClick = onLinkClick,
    )
}

@Composable
fun Markdown(
    document: MarkdownDocument,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
) {
    val blocks = remember(document) {
        document.blocks.map(::RenderedMarkdownBlock)
    }
    Markdown(
        blocks = blocks,
        modifier = modifier,
        onLinkClick = onLinkClick,
    )
}

@Composable
fun Markdown(
    state: MarkdownRendererState,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
) {
    Markdown(
        blocks = state.blocks,
        modifier = modifier,
        onLinkClick = onLinkClick,
    )
}

@Composable
private fun Markdown(
    blocks: List<RenderedMarkdownBlock>,
    modifier: Modifier,
    onLinkClick: (String) -> Unit,
) {
    val styles = rememberMarkdownBlockStyles()
    val currentOnLinkClick = rememberUpdatedState(onLinkClick)

    Surface(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            blocks.forEach { renderedBlock ->
                key(renderedBlock.id.raw) {
                    MarkdownBlock(
                        block = renderedBlock.block,
                        styles = styles,
                        onLinkClick = { currentOnLinkClick.value(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownBlock(
    block: BlockNode,
    styles: MarkdownBlockStyles,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is BlockNode.BlockQuote -> QuoteBlock(
            block = block,
            styles = styles,
            onLinkClick = onLinkClick,
            modifier = modifier,
        )

        is BlockNode.Document -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            block.children.forEach { child ->
                MarkdownBlock(
                    block = child,
                    styles = styles,
                    onLinkClick = onLinkClick,
                )
            }
        }

        is BlockNode.FencedCodeBlock -> CodeBlock(
            block = block,
            styles = styles,
            modifier = modifier,
        )

        is BlockNode.Heading -> SelectableAnnotatedText(
            text = block.children.toAnnotatedString(styles.inline, onLinkClick),
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                2 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                3 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                4 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                5 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            },
            modifier = modifier,
        )

        is BlockNode.ListBlock -> ListBlock(
            block = block,
            styles = styles,
            onLinkClick = onLinkClick,
            modifier = modifier,
        )

        is BlockNode.ListItem -> ListItemBlock(
            block = block,
            styles = styles,
            onLinkClick = onLinkClick,
            modifier = modifier,
        )

        is BlockNode.Paragraph -> SelectableAnnotatedText(
            text = block.children.toAnnotatedString(styles.inline, onLinkClick),
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier,
        )

        is BlockNode.RawTextBlock -> SelectableAnnotatedText(
            text = AnnotatedString(block.literal),
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier,
        )

        is BlockNode.ThematicBreak -> HorizontalDivider(
            modifier = modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
        )

        is BlockNode.UnsupportedBlock -> SelectableAnnotatedText(
            text = AnnotatedString(block.literal),
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier,
        )
    }
}

@Composable
private fun QuoteBlock(
    block: BlockNode.BlockQuote,
    styles: MarkdownBlockStyles,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(999.dp),
                )
                .align(Alignment.Top)
                .padding(vertical = 4.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.children.forEach { child ->
                MarkdownBlock(
                    block = child,
                    styles = styles,
                    onLinkClick = onLinkClick,
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(
    block: BlockNode.FencedCodeBlock,
    styles: MarkdownBlockStyles,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!block.infoString.isNullOrBlank() || !block.isClosed) {
            Text(
                text = buildString {
                    append(block.infoString ?: "code")
                    if (!block.isClosed) {
                        append("  streaming...")
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SelectionContainer {
            Text(
                text = AnnotatedString(block.literal),
                style = styles.codeBlockTextStyle,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ListBlock(
    block: BlockNode.ListBlock,
    styles: MarkdownBlockStyles,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (block.isLoose) 10.dp else 6.dp),
    ) {
        block.items.forEach { item ->
            ListItemBlock(
                block = item,
                styles = styles,
                onLinkClick = onLinkClick,
            )
        }
    }
}

@Composable
private fun ListItemBlock(
    block: BlockNode.ListItem,
    styles: MarkdownBlockStyles,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = block.marker,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(top = 1.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            block.children.forEach { child ->
                MarkdownBlock(
                    block = child,
                    styles = styles,
                    onLinkClick = onLinkClick,
                )
            }
        }
    }
}

@Composable
private fun SelectableAnnotatedText(
    text: AnnotatedString,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    SelectionContainer {
        Text(
            text = text,
            style = style,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

@Stable
internal data class MarkdownBlockStyles(
    val inline: MarkdownInlineStyles,
    val codeBlockTextStyle: androidx.compose.ui.text.TextStyle,
)

@Stable
internal data class MarkdownInlineStyles(
    val emphasis: SpanStyle,
    val strong: SpanStyle,
    val strike: SpanStyle,
    val code: SpanStyle,
    val link: TextLinkStyles,
)

@Composable
internal fun rememberMarkdownBlockStyles(): MarkdownBlockStyles {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    return remember(colorScheme, typography) {
        MarkdownBlockStyles(
            inline = MarkdownInlineStyles(
                emphasis = SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                strong = SpanStyle(fontWeight = FontWeight.Bold),
                strike = SpanStyle(textDecoration = TextDecoration.LineThrough),
                code = SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    fontSize = typography.bodyLarge.fontSize,
                ),
                link = TextLinkStyles(
                    style = SpanStyle(
                        color = colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
            ),
            codeBlockTextStyle = typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp,
            ),
        )
    }
}

internal fun List<InlineNode>.toAnnotatedString(
    styles: MarkdownInlineStyles = MarkdownInlineStyles(
        emphasis = SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
        strong = SpanStyle(fontWeight = FontWeight.Bold),
        strike = SpanStyle(textDecoration = TextDecoration.LineThrough),
        code = SpanStyle(fontFamily = FontFamily.Monospace),
        link = TextLinkStyles(
            style = SpanStyle(textDecoration = TextDecoration.Underline),
        ),
    ),
    onLinkClick: (String) -> Unit = {},
): AnnotatedString = buildAnnotatedString {
    appendInlineNodes(
        nodes = this@toAnnotatedString,
        styles = styles,
        onLinkClick = onLinkClick,
    )
}

private fun AnnotatedString.Builder.appendInlineNodes(
    nodes: List<InlineNode>,
    styles: MarkdownInlineStyles,
    onLinkClick: (String) -> Unit,
) {
    nodes.forEach { node ->
        when (node) {
            is InlineNode.CodeSpan -> withStyle(styles.code) {
                append(node.literal)
            }

            is InlineNode.Emphasis -> withStyle(styles.emphasis) {
                appendInlineNodes(node.children, styles, onLinkClick)
            }

            is InlineNode.HardBreak -> append("\n")

            is InlineNode.Link -> withLink(
                LinkAnnotation.Url(
                    url = node.destination,
                    styles = styles.link,
                    linkInteractionListener = { onLinkClick(node.destination) },
                ),
            ) {
                appendInlineNodes(node.children, styles, onLinkClick)
            }

            is InlineNode.SoftBreak -> append("\n")

            is InlineNode.Strikethrough -> withStyle(styles.strike) {
                appendInlineNodes(node.children, styles, onLinkClick)
            }

            is InlineNode.Strong -> withStyle(styles.strong) {
                appendInlineNodes(node.children, styles, onLinkClick)
            }

            is InlineNode.Text -> append(node.literal)

            is InlineNode.UnsupportedInline -> append(node.literal)
        }
    }
}
