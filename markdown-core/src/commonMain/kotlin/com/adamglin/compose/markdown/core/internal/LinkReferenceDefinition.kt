package com.adamglin.compose.markdown.core.internal

import com.adamglin.compose.markdown.core.model.TextRange

/** A link reference definition with its normalized [label] and the source lines it spans. */
internal data class LinkReferenceDefinition(
    val label: String,
    val destination: String,
    val title: String?,
    val range: TextRange,
)
