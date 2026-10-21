package com.adamglin.compose.markdown.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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

/**
 * Renders [block] through its [MarkdownComponents] slot. Each slot is invoked from its own
 * composable, so a new components instance re-runs only the slots, never this function.
 */
@Composable
internal fun MarkdownBlock(
    block: BlockNode,
    context: MarkdownRenderContext,
    listDepth: Int,
) {
    when (block) {
        is BlockNode.Paragraph -> ParagraphSlot(block, context)
        is BlockNode.Heading -> HeadingSlot(block, context)
        is BlockNode.BlockQuote -> BlockQuoteSlot(block, context, listDepth)
        is BlockNode.ListBlock -> ListSlot(block, context, listDepth)
        is BlockNode.FencedCodeBlock, is BlockNode.IndentedCodeBlock -> CodeBlockSlot(block, context)
        is BlockNode.MathBlock -> MathBlockSlot(block, context)
        is BlockNode.TableBlock -> TableSlot(block, context)
        is BlockNode.ThematicBreak -> ThematicBreakSlot(block, context)
        // Rendered by their parents.
        is BlockNode.ListItem, is BlockNode.TableRow, is BlockNode.TableCell -> Unit
    }
}

@Composable
private fun ParagraphSlot(block: BlockNode.Paragraph, context: MarkdownRenderContext) {
    val scope = remember(block, context) {
        BlockScope(block) { modifier ->
            MarkdownInlineText(block.children, context.styles.body, context, modifier.fillMaxWidth())
        }
    }
    context.components.paragraph(scope)
}

@Composable
private fun HeadingSlot(block: BlockNode.Heading, context: MarkdownRenderContext) {
    val scope = remember(block, context) {
        BlockScope(block) { modifier ->
            MarkdownInlineText(
                nodes = block.children,
                style = context.styles.heading(block.level),
                context = context,
                modifier = modifier.fillMaxWidth().semantics { heading() },
            )
        }
    }
    context.components.heading(scope)
}

@Composable
private fun BlockQuoteSlot(block: BlockNode.BlockQuote, context: MarkdownRenderContext, listDepth: Int) {
    val scope = remember(block, context, listDepth) {
        ContainerScope(
            block = block,
            children = { modifier -> MarkdownBlocks(block.children, context, listDepth, context.blockSpacing, modifier) },
            content = { modifier -> QuoteBlock(block, context, listDepth, modifier) },
        )
    }
    context.components.blockQuote(scope)
}

@Composable
private fun ListSlot(block: BlockNode.ListBlock, context: MarkdownRenderContext, listDepth: Int) {
    val scope = remember(block, context, listDepth) {
        BlockScope(block) { modifier -> ListBlock(block, context, listDepth, modifier.fillMaxWidth()) }
    }
    context.components.list(scope)
}

@Composable
private fun CodeBlockSlot(block: BlockNode, context: MarkdownRenderContext) {
    val scope = remember(block, context) {
        when (block) {
            is BlockNode.FencedCodeBlock -> CodeBlockScope(block.literal, block.languageHint, block.infoString, block.isClosed, context)
            is BlockNode.IndentedCodeBlock -> CodeBlockScope(block.literal, null, null, true, context)
            else -> error("not a code block: $block")
        }
    }
    context.components.codeBlock(scope)
}

@Composable
private fun MathBlockSlot(block: BlockNode.MathBlock, context: MarkdownRenderContext) {
    val scope = remember(block, context) { BlockScope(block) { modifier -> MathBlock(block, context, modifier) } }
    context.components.mathBlock(scope)
}

@Composable
private fun TableSlot(block: BlockNode.TableBlock, context: MarkdownRenderContext) {
    val scope = remember(block, context) { BlockScope(block) { modifier -> TableBlock(block, context, modifier) } }
    context.components.table(scope)
}

@Composable
private fun ThematicBreakSlot(block: BlockNode.ThematicBreak, context: MarkdownRenderContext) {
    val scope = remember(block, context) {
        BlockScope(block) { modifier ->
            Box(
                modifier = modifier
                    .padding(vertical = 4.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(context.colors.border),
            )
        }
    }
    context.components.thematicBreak(scope)
}

internal class BlockScope<out T : BlockNode>(
    override val block: T,
    private val content: @Composable (Modifier) -> Unit,
) : MarkdownBlockScope<T> {
    @Composable
    override fun DefaultContent(modifier: Modifier) = content(modifier)
}

internal class ContainerScope<out T : BlockNode>(
    override val block: T,
    private val children: @Composable (Modifier) -> Unit,
    private val content: @Composable (Modifier) -> Unit = children,
) : MarkdownContainerScope<T> {
    @Composable
    override fun DefaultContent(modifier: Modifier) = content(modifier)

    @Composable
    override fun Children(modifier: Modifier) = children(modifier)
}

private class CodeBlockScope(
    override val code: String,
    override val language: String?,
    override val infoString: String?,
    override val isClosed: Boolean,
    private val context: MarkdownRenderContext,
) : MarkdownCodeBlockScope {
    @Composable
    override fun DefaultContent(modifier: Modifier) = CodeBlock(this, context, modifier)

    @Composable
    override fun HighlightedCode(modifier: Modifier, contentPadding: PaddingValues) =
        CodeText(this, context, contentPadding, modifier)
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
    scope: MarkdownCodeBlockScope,
    context: MarkdownRenderContext,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val label = scope.infoString?.trim().orEmpty()
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
        CodeText(scope, context, PaddingValues(horizontal = 14.dp))
    }
}

/**
 * The code of [scope], highlighted once closed: a streaming fence changes every chunk. [padding]
 * sits inside the horizontal scroll, so scrolled code reaches the edges.
 */
@Composable
private fun CodeText(
    scope: MarkdownCodeBlockScope,
    context: MarkdownRenderContext,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val code = if (scope.isClosed) {
        rememberHighlightedCode(scope.code, scope.language, context.codeHighlighter, context.colors)
    } else {
        remember(scope.code) { AnnotatedString(scope.code) }
    }
    BasicText(
        text = code,
        style = context.styles.code,
        softWrap = false,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(padding),
    )
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
