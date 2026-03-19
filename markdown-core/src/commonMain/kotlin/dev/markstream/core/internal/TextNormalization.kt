package dev.markstream.core.internal

internal fun normalizeReferenceLabel(raw: CharSequence): String? {
    if (raw.isEmpty()) {
        return null
    }

    var start = 0
    var endExclusive = raw.length
    while (start < endExclusive && raw[start].isWhitespace()) {
        start += 1
    }
    while (endExclusive > start && raw[endExclusive - 1].isWhitespace()) {
        endExclusive -= 1
    }
    if (start >= endExclusive) {
        return null
    }

    val builder = StringBuilder(endExclusive - start)
    var pendingSpace = false
    var hasContent = false
    for (index in start until endExclusive) {
        val char = raw[index]
        if (char.isWhitespace()) {
            pendingSpace = hasContent
            continue
        }
        if (pendingSpace) {
            builder.append(' ')
            pendingSpace = false
        }
        builder.append(char.lowercaseChar())
        hasContent = true
    }
    return builder.toString()
}
