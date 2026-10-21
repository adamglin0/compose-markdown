package com.adamglin.compose.markdown.core.internal

import com.adamglin.compose.markdown.core.model.TextRange

/** A block that later input can still change; [startOffset] is the start of the line the block starts on. */
internal data class OpenBlockFrame(
    val marker: String,
    val startOffset: Int,
)

/** A link reference definition with its normalized [label] and the source lines it spans. */
internal data class LinkReferenceDefinition(
    val label: String,
    val destination: String,
    val title: String?,
    val range: TextRange,
)
