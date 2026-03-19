package com.adamglin.compose.markdown.compose

import com.adamglin.compose.markdown.core.model.BlockId
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.LineRange
import com.adamglin.compose.markdown.core.model.TaskState
import com.adamglin.compose.markdown.core.model.TextRange
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
