package com.adamglin.compose.markdown.core

import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.ListType

/** Marker text of the item at [index], numbering ordered items from the list's start number. */
internal fun BlockNode.ListBlock.itemMarker(index: Int): String = when (val type = type) {
    is ListType.Bullet -> type.bulletChar.toString()
    is ListType.Ordered -> "${type.startNumber + index}${type.delimiter}"
}

internal val BlockNode.ListBlock.styleName: String
    get() = if (type is ListType.Ordered) "ordered" else "unordered"
