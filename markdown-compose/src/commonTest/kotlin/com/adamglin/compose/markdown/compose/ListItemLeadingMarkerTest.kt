package com.adamglin.compose.markdown.compose

import com.adamglin.compose.markdown.core.model.BlockId
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.LineRange
import com.adamglin.compose.markdown.core.model.ListType
import com.adamglin.compose.markdown.core.model.TaskState
import com.adamglin.compose.markdown.core.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ListItemLeadingMarkerTest {
    @Test
    fun taskListItemsMapToCheckboxMarkers() {
        val marker = listBlock(ListType.Bullet(bulletChar = '-'), listItem(taskState = TaskState.Checked)).leadingMarker(0)

        assertEquals(TaskState.Checked, assertIs<ListItemLeadingMarker.Task>(marker).taskState)
    }

    @Test
    fun bulletItemsShowTheBulletCharacter() {
        val marker = listBlock(ListType.Bullet(bulletChar = '*'), listItem()).leadingMarker(0)

        assertEquals("*", assertIs<ListItemLeadingMarker.Literal>(marker).value)
    }

    @Test
    fun orderedItemsCountFromTheListStartNumber() {
        val list = listBlock(ListType.Ordered(startNumber = 3, delimiter = ')'), listItem(), listItem())

        assertEquals("3)", assertIs<ListItemLeadingMarker.Literal>(list.leadingMarker(0)).value)
        assertEquals("4)", assertIs<ListItemLeadingMarker.Literal>(list.leadingMarker(1)).value)
    }
}

private fun listBlock(type: ListType, vararg items: BlockNode.ListItem): BlockNode.ListBlock = BlockNode.ListBlock(
    id = BlockId(1L),
    range = TextRange.Empty,
    lineRange = LineRange.Empty,
    type = type,
    items = items.toList(),
    isLoose = false,
)

private fun listItem(taskState: TaskState? = null): BlockNode.ListItem = BlockNode.ListItem(
    id = BlockId(2L),
    range = TextRange.Empty,
    lineRange = LineRange.Empty,
    children = emptyList(),
    taskState = taskState,
)
