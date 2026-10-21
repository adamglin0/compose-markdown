package com.adamglin.compose.markdown.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.TableAlignment

/**
 * A GFM table laid out as a grid. Column widths come from the cells' intrinsic widths: columns keep
 * their preferred width when everything fits, shrink (never below their longest word) when it does
 * not, and the table scrolls horizontally when even the narrowest layout is wider than the space.
 *
 * Plain [Layout] with intrinsic queries on the cells, so every cell is composed once and the table
 * can sit inside any parent (including ones that ask for intrinsics). The grid sits in a regular
 * `horizontalScroll`, which gives it unbounded width, so the viewport width is captured in front of
 * the scroll and read by the grid's measurement.
 */
@Composable
internal fun TableBlock(
    block: BlockNode.TableBlock,
    context: MarkdownRenderContext,
    modifier: Modifier = Modifier,
) {
    val columnCount = block.header.cells.size
    if (columnCount == 0) return
    val rows = listOf(block.header) + block.rows
    val borderColor = context.colors.border
    var viewportWidth by remember { mutableIntStateOf(Constraints.Infinity) }

    Layout(
        content = {
            rows.forEachIndexed { rowIndex, row ->
                key(row.id.raw) {
                    for (columnIndex in 0 until columnCount) {
                        val cell = row.cells.getOrNull(columnIndex)
                        val alignment = block.alignments.getOrElse(columnIndex) { TableAlignment.Default }
                        val style = if (rowIndex == 0) context.styles.tableHeader else context.styles.table
                        if (cell == null) {
                            Box(Modifier)
                        } else {
                            MarkdownInlineText(
                                nodes = cell.children,
                                style = style.merge(TextStyle(textAlign = alignment.toTextAlign())),
                                context = context,
                            )
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(borderColor))
        },
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(10.dp)
            .layout { measurable, constraints ->
                if (viewportWidth != constraints.maxWidth) viewportWidth = constraints.maxWidth
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .horizontalScroll(rememberScrollState()),
    ) { measurables, constraints ->
        val cellMeasurables = measurables.subList(0, measurables.size - 1)
        val dividerMeasurable = measurables.last()
        val rowCount = rows.size
        val columnSpacing = 12.dp.roundToPx()
        val rowSpacing = 6.dp.roundToPx()
        val totalSpacing = columnSpacing * (columnCount - 1)

        val preferred = IntArray(columnCount)
        val minimum = IntArray(columnCount)
        cellMeasurables.forEachIndexed { index, cell ->
            val column = index % columnCount
            preferred[column] = maxOf(preferred[column], cell.maxIntrinsicWidth(Constraints.Infinity))
            minimum[column] = maxOf(minimum[column], cell.minIntrinsicWidth(Constraints.Infinity))
        }
        val available = if (viewportWidth != Constraints.Infinity) viewportWidth else preferred.sum() + totalSpacing
        val widths = resolveTableColumnWidths(preferred, minimum, (available - totalSpacing).coerceAtLeast(0))
        val contentWidth = widths.sum() + totalSpacing

        val placeables = cellMeasurables.mapIndexed { index, cell ->
            val width = widths[index % columnCount]
            cell.measure(Constraints(minWidth = width, maxWidth = width))
        }
        val divider = dividerMeasurable.measure(Constraints.fixedWidth(contentWidth))
        val rowHeights = IntArray(rowCount) { row ->
            (0 until columnCount).maxOf { column -> placeables[row * columnCount + column].height }
        }
        val height = rowHeights.sum() + rowSpacing * (rowCount - 1) + rowSpacing + divider.height
        layout(contentWidth, height) {
            var y = 0
            for (row in 0 until rowCount) {
                var x = 0
                for (column in 0 until columnCount) {
                    placeables[row * columnCount + column].placeRelative(x, y)
                    x += widths[column] + columnSpacing
                }
                y += rowHeights[row] + rowSpacing
                if (row == 0) {
                    divider.placeRelative(0, y)
                    y += divider.height + rowSpacing
                }
            }
        }
    }
}

/**
 * Resolves column widths from each column's [preferred] (single-line) and [minimum] (longest word)
 * width. The result sums to [available] when the minimum widths fit; otherwise it is wider and the
 * table scrolls.
 */
internal fun resolveTableColumnWidths(
    preferred: IntArray,
    minimum: IntArray,
    available: Int,
): IntArray {
    val count = preferred.size
    if (count == 0) return IntArray(0)
    val minimumWidths = IntArray(count) { minOf(minimum[it], preferred[it]) }
    if (preferred.sum() <= available) {
        // Everything fits on one line; the trailing column takes the remaining space.
        return preferred.copyOf().also { it[count - 1] += available - preferred.sum() }
    }
    if (minimumWidths.sum() > available) {
        // Cannot fit without breaking words: give each column a fair share, at least its minimum.
        val share = available / count
        return IntArray(count) { maxOf(minimumWidths[it], minOf(preferred[it], share)) }
    }
    // Cap the widest columns first: find the largest cap whose capped widths still fit.
    fun widthsFor(cap: Int) = IntArray(count) { maxOf(minimumWidths[it], minOf(preferred[it], cap)) }
    var low = 0
    var high = preferred.max()
    while (low < high) {
        val mid = (low + high + 1) ushr 1
        if (widthsFor(mid).sum() <= available) low = mid else high = mid - 1
    }
    val widths = widthsFor(low)
    var leftover = available - widths.sum()
    // Give the rounding remainder to columns that still want more, left to right.
    var index = 0
    while (leftover > 0 && index < count) {
        val grow = minOf(leftover, preferred[index] - widths[index]).coerceAtLeast(0)
        widths[index] += grow
        leftover -= grow
        index++
    }
    widths[count - 1] += leftover
    return widths
}

private fun TableAlignment.toTextAlign(): TextAlign = when (this) {
    TableAlignment.Center -> TextAlign.Center
    TableAlignment.Right -> TextAlign.End
    TableAlignment.Default, TableAlignment.Left -> TextAlign.Start
}
