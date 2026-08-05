package com.adamglin.compose.markdown.compose

import com.adamglin.compose.markdown.core.model.TableAlignment
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MarkdownTableLayoutTest {
    @Test
    fun shortLeadingColumnKeepsPreferredWidthWhenSpaceRemains() {
        val widths = resolveTableColumnWidths(
            preferredWidths = intArrayOf(80, 240),
            availableWidth = 800,
        )

        assertContentEquals(intArrayOf(80, 720), widths)
    }

    @Test
    fun overflowShrinksTallestColumnsFirst() {
        val widths = resolveTableColumnWidths(
            preferredWidths = intArrayOf(100, 300),
            availableWidth = 200,
        )

        assertContentEquals(intArrayOf(100, 100), widths)
    }

    @Test
    fun overflowKeepsShortTrailingHeaderAtPreferredWidth() {
        val widths = resolveTableColumnWidths(
            preferredWidths = intArrayOf(500, 20),
            availableWidth = 400,
        )

        assertContentEquals(intArrayOf(380, 20), widths)
    }

    @Test
    fun overflowKeepsShortLeadingColumnAtPreferredWidth() {
        val widths = resolveTableColumnWidths(
            preferredWidths = intArrayOf(30, 3000),
            availableWidth = 100,
        )

        assertContentEquals(intArrayOf(30, 70), widths)
    }

    @Test
    fun zeroPreferredWidthsSplitAvailableSpaceEvenly() {
        val widths = resolveTableColumnWidths(
            preferredWidths = intArrayOf(0, 0, 0),
            availableWidth = 100,
        )

        assertContentEquals(intArrayOf(33, 33, 34), widths)
    }

    @Test
    fun equalPreferredColumnsShareDeficitEvenly() {
        val widths = distributeTableColumnWidths(
            preferredWidths = intArrayOf(10, 10, 10),
            availableWidth = 20,
        )

        assertEquals(20, widths.sum())
        assertContentEquals(intArrayOf(7, 7, 6), widths)
    }

    @Test
    fun zeroAvailableWidthYieldsZeroColumns() {
        val widths = distributeTableColumnWidths(
            preferredWidths = intArrayOf(30, 3000),
            availableWidth = 0,
        )

        assertContentEquals(intArrayOf(0, 0), widths)
    }

    @Test
    fun tableCellHorizontalOffsetRespectsAlignment() {
        assertEquals(0, tableCellHorizontalOffset(TableAlignment.Left, 100, 40))
        assertEquals(0, tableCellHorizontalOffset(TableAlignment.Default, 100, 40))
        assertEquals(30, tableCellHorizontalOffset(TableAlignment.Center, 100, 40))
        assertEquals(60, tableCellHorizontalOffset(TableAlignment.Right, 100, 40))
    }
}
