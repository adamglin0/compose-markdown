package dev.markstream.compose

import dev.markstream.core.model.BlockId
import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.LineRange
import dev.markstream.core.model.TaskState
import dev.markstream.core.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ListItemLeadingMarkerTest {
    @Test
    fun taskListItemsMapToCheckboxMarkers() {
        val marker = listItem(taskState = TaskState.Checked).leadingMarker()

        assertEquals(TaskState.Checked, assertIs<ListItemLeadingMarker.Task>(marker).taskState)
    }

    @Test
    fun plainListItemsKeepLiteralMarkers() {
        val marker = listItem(marker = "*").leadingMarker()

        assertEquals("*", assertIs<ListItemLeadingMarker.Literal>(marker).value)
    }
}

private fun listItem(
    marker: String = "-",
    taskState: TaskState? = null,
): BlockNode.ListItem = BlockNode.ListItem(
    id = BlockId(1L),
    range = TextRange.Empty,
    lineRange = LineRange.Empty,
    marker = marker,
    children = emptyList(),
    taskState = taskState,
)
