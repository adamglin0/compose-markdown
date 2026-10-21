package com.adamglin.compose.markdown.core.internal

import com.adamglin.compose.markdown.core.model.HeadingStyle
import com.adamglin.compose.markdown.core.model.ListType
import com.adamglin.compose.markdown.core.model.TableAlignment

/** Discriminators the block parser passes with each block id request; see the engine's BlockIdAllocator. */
internal object BlockIdentity {
    const val LIST_ITEM_DISCRIMINATOR = "item"

    fun listDiscriminator(type: ListType): String = when (type) {
        is ListType.Bullet -> "bullet:${type.bulletChar}"
        is ListType.Ordered -> "ordered:${type.delimiter}"
    }

    fun headingDiscriminator(style: HeadingStyle, level: Int): String = "${style.name}:$level"

    fun tableDiscriminator(alignments: List<TableAlignment>): String = alignments.joinToString(separator = ",") { it.name }

    fun tableRowDiscriminator(isHeader: Boolean): String = if (isHeader) "header" else "row"

    fun tableCellDiscriminator(rowStart: Int, index: Int): String = "$rowStart:$index"
}
