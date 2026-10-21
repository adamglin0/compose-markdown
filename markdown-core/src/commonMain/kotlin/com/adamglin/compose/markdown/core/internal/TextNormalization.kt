package com.adamglin.compose.markdown.core.internal

/**
 * CommonMark link label normalization: strip surrounding whitespace, collapse internal runs of spaces,
 * tabs and line endings to one space, and Unicode case fold (approximated as lowercase-then-uppercase,
 * which maps `ẞ` and `SS` to the same key). Returns `null` for labels without non-whitespace content.
 */
internal fun normalizeReferenceLabel(raw: CharSequence): String? {
    val builder = StringBuilder(raw.length)
    var pendingSpace = false
    for (char in raw) {
        if (char == ' ' || char == '\t' || char == '\n' || char == '\r') {
            pendingSpace = builder.isNotEmpty()
            continue
        }
        if (pendingSpace) {
            builder.append(' ')
            pendingSpace = false
        }
        builder.append(char)
    }
    if (builder.isEmpty()) {
        return null
    }
    return builder.toString().lowercase().uppercase()
}
