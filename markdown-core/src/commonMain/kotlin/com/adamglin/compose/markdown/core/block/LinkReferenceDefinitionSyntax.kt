package com.adamglin.compose.markdown.core.block

import com.adamglin.compose.markdown.core.inline.scanLinkDestination
import com.adamglin.compose.markdown.core.inline.scanLinkLabel
import com.adamglin.compose.markdown.core.inline.scanLinkTitle
import com.adamglin.compose.markdown.core.inline.skipSpacesAndOneLineEnding
import com.adamglin.compose.markdown.core.inline.skipSpacesAndTabs
import com.adamglin.compose.markdown.core.internal.normalizeReferenceLabel

internal data class ScannedDefinition(
    /** Index after the definition, including its line ending. */
    val endExclusive: Int,
    val label: String,
    val destination: String,
    val title: String?,
)

/**
 * Scans a link reference definition at [start] of paragraph [text] (lines joined by `\n`): a link label,
 * `:`, a destination and an optional title, each separated by spaces or tabs including at most one line
 * ending, and nothing but spaces or tabs after it on its last line. When a title is followed by other text
 * the definition ends after the destination instead, provided that is a line end.
 */
internal fun scanLinkReferenceDefinition(text: String, start: Int): ScannedDefinition? {
    val labelEnd = scanLinkLabel(text, start, text.length) ?: return null
    if (text.getOrNull(labelEnd) != ':') {
        return null
    }
    val label = normalizeReferenceLabel(text.subSequence(start + 1, labelEnd - 1)) ?: return null
    val destinationStart = skipSpacesAndOneLineEnding(text, labelEnd + 1, text.length)
    val destination = scanLinkDestination(text, destinationStart, text.length, allowEmptyBare = false) ?: return null
    val beforeTitle = destination.endExclusive
    val titleStart = skipSpacesAndOneLineEnding(text, beforeTitle, text.length)
    val title = if (titleStart > beforeTitle) scanLinkTitle(text, titleStart, text.length) else null
    val titleEnd = title?.let { lineEndAfter(text, it.endExclusive) }
    if (title != null && titleEnd != null) {
        return ScannedDefinition(endExclusive = titleEnd, label = label, destination = destination.value, title = title.value)
    }
    val destinationEnd = lineEndAfter(text, beforeTitle) ?: return null
    return ScannedDefinition(endExclusive = destinationEnd, label = label, destination = destination.value, title = null)
}

/** Index after trailing spaces/tabs and the line ending at [start], or null when other text follows. */
private fun lineEndAfter(text: String, start: Int): Int? {
    val cursor = skipSpacesAndTabs(text, start, text.length)
    return when {
        cursor == text.length -> cursor
        text[cursor] == '\n' -> cursor + 1
        else -> null
    }
}
