package com.adamglin.compose.markdown.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.ListType
import com.adamglin.compose.markdown.core.model.TaskState

/**
 * A list: one shared marker column (as wide as the widest marker, numbers end-aligned) next to the
 * item bodies. Each marker sits on the first baseline of its item when both have one.
 */
@Composable
internal fun ListBlock(
    block: BlockNode.ListBlock,
    context: MarkdownRenderContext,
    listDepth: Int,
    modifier: Modifier = Modifier,
) {
    val itemSpacing = if (block.isLoose) context.blockSpacing else context.blockSpacing / 2
    val markerGap = 8.dp
    Layout(
        contents = listOf(
            {
                block.items.forEachIndexed { index, item ->
                    key(item.id.raw) {
                        // A box per marker and per body: the layout needs exactly one node for each.
                        Box { ListMarkerSlot(block.type, item, index, listDepth, context) }
                    }
                }
            },
            {
                block.items.forEach { item ->
                    key(item.id.raw) {
                        Box { ListItemSlot(item, context, listDepth) }
                    }
                }
            },
        ),
        modifier = modifier,
    ) { (markerMeasurables, bodyMeasurables), constraints ->
        val markerPlaceables = markerMeasurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val markerWidth = markerPlaceables.maxOfOrNull { it.width } ?: 0
        val bodyX = markerWidth + markerGap.roundToPx()
        val bodyMaxWidth = if (constraints.hasBoundedWidth) (constraints.maxWidth - bodyX).coerceAtLeast(0) else Constraints.Infinity
        val bodyPlaceables = bodyMeasurables.map { it.measure(Constraints(maxWidth = bodyMaxWidth)) }
        val spacing = itemSpacing.roundToPx()

        val markerTops = IntArray(bodyPlaceables.size)
        val bodyTops = IntArray(bodyPlaceables.size)
        var y = 0
        bodyPlaceables.forEachIndexed { index, body ->
            val marker = markerPlaceables[index]
            val bodyBaseline = body[FirstBaseline]
            val markerBaseline = marker[FirstBaseline]
            // Marker top relative to body top: baseline-aligned when both have text, else top-aligned.
            val markerOffset = if (bodyBaseline != AlignmentLine.Unspecified && markerBaseline != AlignmentLine.Unspecified) {
                bodyBaseline - markerBaseline
            } else {
                0
            }
            bodyTops[index] = y + (-markerOffset).coerceAtLeast(0)
            markerTops[index] = bodyTops[index] + markerOffset
            y = maxOf(bodyTops[index] + body.height, markerTops[index] + marker.height)
            if (index != bodyPlaceables.lastIndex) y += spacing
        }
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            bodyX + (bodyPlaceables.maxOfOrNull { it.width } ?: 0)
        }
        layout(width, y.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            bodyPlaceables.forEachIndexed { index, body ->
                val marker = markerPlaceables[index]
                marker.placeRelative(markerWidth - marker.width, markerTops[index])
                body.placeRelative(bodyX, bodyTops[index])
            }
        }
    }
}

@Composable
private fun ListMarkerSlot(listType: ListType, item: BlockNode.ListItem, index: Int, depth: Int, context: MarkdownRenderContext) {
    if (item.taskState != null) {
        val scope = remember(item, context) { BlockScope(item) { modifier -> TaskCheckbox(item.taskState == TaskState.Checked, context, modifier) } }
        context.components.taskCheckbox(scope)
    } else {
        val scope = remember(listType, index, depth, context) { ListMarkerScope(listType, index, depth, context) }
        context.components.listMarker(scope)
    }
}

@Composable
private fun ListItemSlot(item: BlockNode.ListItem, context: MarkdownRenderContext, listDepth: Int) {
    val scope = remember(item, context, listDepth) {
        ContainerScope(item, children = { modifier ->
            MarkdownBlocks(item.children, context, listDepth + 1, context.blockSpacing / 2, modifier)
        })
    }
    context.components.listItem(scope)
}

private class ListMarkerScope(
    override val listType: ListType,
    override val index: Int,
    override val depth: Int,
    private val context: MarkdownRenderContext,
) : MarkdownListMarkerScope {
    @Composable
    override fun DefaultContent(modifier: Modifier) = when (listType) {
        is ListType.Ordered -> BasicText(text = orderedMarker(listType, index), modifier = modifier, style = context.styles.body)
        is ListType.Bullet -> BulletMarker(bulletForDepth(depth), context, modifier)
    }
}

/**
 * Task-list checkbox: read-only, exposed to accessibility as a checkbox with its state, centered
 * in the item's first line.
 */
@Composable
private fun TaskCheckbox(isChecked: Boolean, context: MarkdownRenderContext, modifier: Modifier) {
    val colors = context.colors
    val borderColor = if (isChecked) colors.accent else colors.border
    FirstLineBox(
        context = context,
        modifier = modifier.semantics {
            role = Role.Checkbox
            toggleableState = ToggleableState(isChecked)
        },
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(1.5.dp, borderColor, RoundedCornerShape(4.dp))
                .background(if (isChecked) colors.accent.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(4.dp)),
        ) {
            if (isChecked) {
                Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                    val strokeWidth = size.minDimension * 0.18f
                    val mid = Offset(size.width * 0.4f, size.height * 0.8f)
                    drawLine(borderColor, Offset(size.width * 0.14f, size.height * 0.54f), mid, strokeWidth, StrokeCap.Round)
                    drawLine(borderColor, mid, Offset(size.width * 0.86f, size.height * 0.2f), strokeWidth, StrokeCap.Round)
                }
            }
        }
    }
}

/** Number text of the item at [index] of an ordered list, counted from its start. */
internal fun orderedMarker(type: ListType.Ordered, index: Int): String = "${type.startNumber + index}${type.delimiter}"

internal enum class Bullet { Disc, Circle, Square }

/** Bullet shape for a list nested [depth] lists deep, cycling disc, circle, square. */
internal fun bulletForDepth(depth: Int): Bullet = Bullet.entries[depth % Bullet.entries.size]

/** A drawn bullet (glyph bullets vary too much between fonts), centered in the first body line. */
@Composable
private fun BulletMarker(bullet: Bullet, context: MarkdownRenderContext, modifier: Modifier) {
    val color = context.colors.text
    FirstLineBox(context, modifier.width(14.dp)) {
        Canvas(Modifier.size(6.dp)) {
            when (bullet) {
                Bullet.Disc -> drawCircle(color)
                Bullet.Circle -> drawCircle(color, radius = size.minDimension / 2 - 0.6.dp.toPx(), style = Stroke(1.2.dp.toPx()))
                Bullet.Square -> drawRect(color)
            }
        }
    }
}

/**
 * A box as tall as one line of body text, centering [content] in it. An empty body-styled text
 * sets the height (whatever unit the line height uses) and gives the box a first baseline, so the
 * list aligns it with the item's first line.
 */
@Composable
private fun FirstLineBox(context: MarkdownRenderContext, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        BasicText(text = "", style = context.styles.body, modifier = Modifier.clearAndSetSemantics {})
        content()
    }
}
