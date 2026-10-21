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
import com.adamglin.compose.markdown.core.model.ListStyle
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
    val markers = listMarkers(block)
    val bullet = bulletForDepth(listDepth)
    Layout(
        contents = listOf(
            {
                block.items.forEachIndexed { index, item ->
                    key(item.id.raw) {
                        when (val taskState = item.taskState) {
                            null -> when (val marker = markers[index]) {
                                null -> BulletMarker(bullet, context)
                                else -> BasicText(text = marker, style = context.styles.body)
                            }

                            else -> TaskCheckbox(taskState = taskState, context = context)
                        }
                    }
                }
            },
            {
                block.items.forEach { item ->
                    key(item.id.raw) {
                        MarkdownBlocks(
                            blocks = item.children,
                            context = context,
                            listDepth = listDepth + 1,
                            spacing = context.blockSpacing / 2,
                        )
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

/**
 * Task-list checkbox: read-only, exposed to accessibility as a checkbox with its state, centered
 * in the item's first line.
 */
@Composable
private fun TaskCheckbox(taskState: TaskState, context: MarkdownRenderContext) {
    val isChecked = taskState == TaskState.Checked
    val colors = context.colors
    val borderColor = if (isChecked) colors.accent else colors.border
    FirstLineBox(
        context = context,
        modifier = Modifier.semantics {
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

/** Number text for every item of an ordered [list], counted from its start; `null`s for bullets. */
internal fun listMarkers(list: BlockNode.ListBlock): List<String?> = when (val type = list.markerType()) {
    is ListMarkerType.Ordered -> List(list.items.size) { index -> "${type.start + index}${type.delimiter}" }
    ListMarkerType.Bullet -> List(list.items.size) { null }
}

internal enum class Bullet { Disc, Circle, Square }

/** Bullet shape for a list nested [depth] lists deep, cycling disc, circle, square. */
internal fun bulletForDepth(depth: Int): Bullet = Bullet.entries[depth % Bullet.entries.size]

/** A drawn bullet (glyph bullets vary too much between fonts), centered in the first body line. */
@Composable
private fun BulletMarker(bullet: Bullet, context: MarkdownRenderContext) {
    val color = context.colors.text
    FirstLineBox(context, Modifier.width(14.dp)) {
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

internal sealed interface ListMarkerType {
    data object Bullet : ListMarkerType

    data class Ordered(val start: Int, val delimiter: Char) : ListMarkerType
}

/**
 * Reads the list kind, start number and delimiter. The core model currently exposes only the raw
 * source marker of each item, so the start number is parsed from the first item's marker.
 */
internal fun BlockNode.ListBlock.markerType(): ListMarkerType {
    if (style == ListStyle.Unordered) return ListMarkerType.Bullet
    val marker = items.firstOrNull()?.marker?.trim().orEmpty()
    val digits = marker.takeWhile(Char::isDigit)
    return ListMarkerType.Ordered(
        start = digits.toIntOrNull() ?: 1,
        delimiter = marker.getOrNull(digits.length)?.takeIf { it == '.' || it == ')' } ?: '.',
    )
}
