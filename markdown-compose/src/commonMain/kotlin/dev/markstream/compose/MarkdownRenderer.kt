package dev.markstream.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import dev.markstream.core.model.TaskState

@Composable
fun Markdown(
    snapshot: MarkdownSnapshot,
    modifier: Modifier = Modifier,
    state: MarkdownRendererState = rememberMarkdownRendererState(snapshot = snapshot),
    codeHighlighter: CodeHighlighter? = null,
    onLinkClick: (String) -> Unit = {},
) {
    LaunchedEffect(snapshot) {
        state.render(snapshot)
    }
    Markdown(
        blocks = state.blocks,
        modifier = modifier,
        codeHighlighter = codeHighlighter,
        onLinkClick = onLinkClick,
    )
}

@Composable
fun Markdown(
    document: MarkdownDocument,
    modifier: Modifier = Modifier,
    codeHighlighter: CodeHighlighter? = null,
    onLinkClick: (String) -> Unit = {},
) {
    val blocks = remember(document) {
        document.blocks.map(::RenderedMarkdownBlock)
    }
    Markdown(
        blocks = blocks,
        modifier = modifier,
        codeHighlighter = codeHighlighter,
        onLinkClick = onLinkClick,
    )
}

@Composable
fun Markdown(
    state: MarkdownRendererState,
    modifier: Modifier = Modifier,
    codeHighlighter: CodeHighlighter? = null,
    onLinkClick: (String) -> Unit = {},
) {
    Markdown(
        blocks = state.blocks,
        modifier = modifier,
        codeHighlighter = codeHighlighter,
        onLinkClick = onLinkClick,
    )
}

@Composable
private fun Markdown(
    blocks: List<RenderedMarkdownBlock>,
    modifier: Modifier,
    codeHighlighter: CodeHighlighter?,
    onLinkClick: (String) -> Unit,
) {
    val styles = rememberMarkdownBlockStyles()
    val defaultCodeHighlighter = if (codeHighlighter == null) {
        rememberMarkdownCodeHighlighter()
    } else {
        null
    }
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
                        codeHighlighter = codeHighlighter ?: defaultCodeHighlighter!!,
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
    codeHighlighter: CodeHighlighter,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is BlockNode.BlockQuote -> QuoteBlock(
            block = block,
            styles = styles,
            codeHighlighter = codeHighlighter,
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
                    codeHighlighter = codeHighlighter,
                    onLinkClick = onLinkClick,
                )
            }
        }

        is BlockNode.FencedCodeBlock -> CodeBlock(
            block = block,
            styles = styles,
            codeHighlighter = codeHighlighter,
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
            codeHighlighter = codeHighlighter,
            onLinkClick = onLinkClick,
            modifier = modifier,
        )

        is BlockNode.ListItem -> ListItemBlock(
            block = block,
            styles = styles,
            codeHighlighter = codeHighlighter,
            onLinkClick = onLinkClick,
            modifier = modifier,
        )

        is BlockNode.TableBlock -> TableBlock(
            block = block,
            styles = styles,
            codeHighlighter = codeHighlighter,
            onLinkClick = onLinkClick,
            modifier = modifier,
        )

        is BlockNode.TableRow -> SelectableAnnotatedText(
            text = block.cells.joinToString(separator = " | ") { cell ->
                cell.children.toAnnotatedString(styles.inline, onLinkClick).text
            }.let(::AnnotatedString),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )

        is BlockNode.TableCell -> SelectableAnnotatedText(
            text = block.children.toAnnotatedString(styles.inline, onLinkClick),
            style = MaterialTheme.typography.bodyMedium,
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
    codeHighlighter: CodeHighlighter,
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
                    codeHighlighter = codeHighlighter,
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
    codeHighlighter: CodeHighlighter,
    modifier: Modifier = Modifier,
) {
    val annotatedCode = rememberHighlightedCodeBlock(
        block = block,
        highlighter = codeHighlighter,
    )

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
                text = annotatedCode,
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
    codeHighlighter: CodeHighlighter,
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
                codeHighlighter = codeHighlighter,
                onLinkClick = onLinkClick,
            )
        }
    }
}

@Composable
private fun ListItemBlock(
    block: BlockNode.ListItem,
    styles: MarkdownBlockStyles,
    codeHighlighter: CodeHighlighter,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        when (val leadingMarker = block.leadingMarker()) {
            is ListItemLeadingMarker.Literal -> Text(
                text = leadingMarker.value,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(top = 1.dp),
            )

            is ListItemLeadingMarker.Task -> TaskListMarker(taskState = leadingMarker.taskState)
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            block.children.forEach { child ->
                MarkdownBlock(
                    block = child,
                    styles = styles,
                    codeHighlighter = codeHighlighter,
                    onLinkClick = onLinkClick,
                )
            }
        }
    }
}

@Composable
private fun TaskListMarker(
    taskState: TaskState,
    modifier: Modifier = Modifier,
) {
    val isChecked = taskState == TaskState.Checked
    val borderColor = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val fillColor = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent

    Box(
        modifier = modifier
            .padding(top = 3.dp)
            .size(18.dp)
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(4.dp),
            )
            .background(
                color = fillColor,
                shape = RoundedCornerShape(4.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isChecked) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
            ) {
                val strokeWidth = size.minDimension * 0.18f
                drawLine(
                    color = borderColor,
                    start = Offset(x = size.width * 0.14f, y = size.height * 0.54f),
                    end = Offset(x = size.width * 0.4f, y = size.height * 0.8f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = borderColor,
                    start = Offset(x = size.width * 0.4f, y = size.height * 0.8f),
                    end = Offset(x = size.width * 0.86f, y = size.height * 0.2f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

internal sealed interface ListItemLeadingMarker {
    data class Literal(val value: String) : ListItemLeadingMarker

    data class Task(val taskState: TaskState) : ListItemLeadingMarker
}

internal fun BlockNode.ListItem.leadingMarker(): ListItemLeadingMarker =
    taskState?.let(ListItemLeadingMarker::Task) ?: ListItemLeadingMarker.Literal(marker)

@Composable
private fun TableBlock(
    block: BlockNode.TableBlock,
    styles: MarkdownBlockStyles,
    codeHighlighter: CodeHighlighter,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TableRowBlock(block.header, styles, onLinkClick, isHeader = true)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        block.rows.forEach { row ->
            TableRowBlock(row, styles, onLinkClick, isHeader = false)
        }
    }
}

@Composable
private fun TableRowBlock(
    row: BlockNode.TableRow,
    styles: MarkdownBlockStyles,
    onLinkClick: (String) -> Unit,
    isHeader: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        row.cells.forEach { cell ->
            Box(modifier = Modifier.weight(1f)) {
                SelectableAnnotatedText(
                    text = cell.children.toAnnotatedString(styles.inline, onLinkClick),
                    style = if (isHeader) {
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
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

            is InlineNode.Image -> appendInlineNodes(node.alt, styles, onLinkClick)

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
