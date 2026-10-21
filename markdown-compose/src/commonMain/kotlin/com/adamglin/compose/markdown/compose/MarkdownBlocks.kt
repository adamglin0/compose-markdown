package com.adamglin.compose.markdown.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.adamglin.compose.markdown.core.model.BlockNode

/** Sibling blocks stacked with [spacing], each keyed by its stable block id. */
@Composable
internal fun MarkdownBlocks(
    blocks: List<BlockNode>,
    context: MarkdownRenderContext,
    listDepth: Int,
    spacing: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        blocks.forEach { block ->
            key(block.id.raw) {
                MarkdownBlock(block = block, context = context, listDepth = listDepth)
            }
        }
    }
}

@Composable
internal fun MarkdownBlock(
    block: BlockNode,
    context: MarkdownRenderContext,
    listDepth: Int = 0,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is BlockNode.Paragraph -> MarkdownInlineText(
            nodes = block.children,
            style = context.styles.body,
            context = context,
            modifier = modifier.fillMaxWidth(),
        )

        is BlockNode.Heading -> MarkdownInlineText(
            nodes = block.children,
            style = context.styles.heading(block.level),
            context = context,
            modifier = modifier.fillMaxWidth().semantics { heading() },
        )

        is BlockNode.BlockQuote -> QuoteBlock(block, context, listDepth, modifier)
        is BlockNode.ListBlock -> ListBlock(block, context, listDepth, modifier.fillMaxWidth())
        is BlockNode.FencedCodeBlock -> CodeBlock(
            code = if (block.isClosed) {
                // Highlight only closed fences: a streaming fence changes every chunk.
                rememberHighlightedCode(block.literal, block.languageHint, context.codeHighlighter, context.colors)
            } else {
                remember(block.literal) { AnnotatedString(block.literal) }
            },
            label = block.infoString?.trim().orEmpty(),
            context = context,
            modifier = modifier,
        )

        is BlockNode.IndentedCodeBlock -> CodeBlock(
            code = remember(block.literal) { AnnotatedString(block.literal) },
            label = "",
            context = context,
            modifier = modifier,
        )
        is BlockNode.MathBlock -> MathBlock(block, context, modifier)
        is BlockNode.TableBlock -> TableBlock(block, context, modifier)
        is BlockNode.ThematicBreak -> Box(
            modifier = modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(context.colors.border),
        )

        is BlockNode.Document -> MarkdownBlocks(block.children, context, listDepth, context.blockSpacing, modifier)
        // Rendered by their parents.
        is BlockNode.ListItem, is BlockNode.TableRow, is BlockNode.TableCell -> Unit
    }
}

@Composable
private fun QuoteBlock(
    block: BlockNode.BlockQuote,
    context: MarkdownRenderContext,
    listDepth: Int,
    modifier: Modifier,
) {
    val barColor = context.colors.accent.copy(alpha = 0.55f)
    // The bar is drawn rather than laid out, so the quote never asks its children for intrinsics.
    MarkdownBlocks(
        blocks = block.children,
        context = context,
        listDepth = listDepth,
        spacing = context.blockSpacing,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(context.colors.surface.copy(alpha = 0.45f))
            .drawBehind {
                val inset = 12.dp.toPx()
                val barWidth = 4.dp.toPx()
                val x = if (layoutDirection == LayoutDirection.Ltr) inset else size.width - inset - barWidth
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, inset),
                    size = Size(barWidth, (size.height - 2 * inset).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
            }
            .padding(start = 28.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
    )
}

@Composable
private fun CodeBlock(
    code: AnnotatedString,
    label: String,
    context: MarkdownRenderContext,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(context.colors.surface.copy(alpha = 0.55f))
            .border(1.dp, context.colors.border, shape)
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (label.isNotEmpty()) {
            BasicText(label, Modifier.padding(horizontal = 14.dp), context.styles.label)
        }
        BasicText(
            text = code,
            style = context.styles.code,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        )
    }
}

@Composable
private fun MathBlock(
    block: BlockNode.MathBlock,
    context: MarkdownRenderContext,
    modifier: Modifier,
) {
    val renderer = context.mathRenderer
    if (renderer != null && block.isClosed) {
        renderer.BlockMath(latex = block.latex, modifier = modifier.fillMaxWidth())
    } else {
        // Unclosed math is incomplete LaTeX; show its source until the closing delimiter arrives.
        BasicText(block.latex, modifier.fillMaxWidth(), context.styles.code)
    }
}
