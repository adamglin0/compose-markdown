package com.adamglin.compose.markdown.core.block

/**
 * Pure line scanners for block starts (CommonMark 0.31.2). Each scanner reads `line` from [start]
 * (the first non-space character after container markers) to the end of the line.
 */

internal fun Char.isSpaceOrTab(): Boolean = this == ' ' || this == '\t'

/** A blank line contains only spaces and tabs. */
internal fun isBlankFrom(line: String, start: Int): Boolean {
    for (index in start until line.length) {
        if (!line[index].isSpaceOrTab()) {
            return false
        }
    }
    return true
}

/** Index after the last character that is not a space or tab, but not before [start]. */
internal fun trimmedEnd(line: String, start: Int): Int {
    var end = line.length
    while (end > start && line[end - 1].isSpaceOrTab()) {
        end -= 1
    }
    return end
}

/** `***`, `---` or `___`: three or more matching characters, optionally separated by spaces or tabs. */
internal fun isThematicBreak(line: String, start: Int): Boolean {
    val marker = line.getOrNull(start) ?: return false
    if (marker != '*' && marker != '-' && marker != '_') {
        return false
    }
    var count = 0
    for (index in start until line.length) {
        when (line[index]) {
            marker -> count += 1
            ' ', '\t' -> Unit
            else -> return false
        }
    }
    return count >= 3
}

/** Length of an ATX opening sequence (`#` to `######` followed by a space, tab or line end), or null. */
internal fun atxHeadingMarkerLength(line: String, start: Int): Int? {
    var cursor = start
    while (cursor < line.length && line[cursor] == '#') {
        cursor += 1
    }
    val level = cursor - start
    if (level !in 1..6 || (cursor < line.length && !line[cursor].isSpaceOrTab())) {
        return null
    }
    return level
}

/**
 * ATX heading content after the opening sequence: drops an optional closing sequence of `#` that is
 * preceded by a space or tab (or makes up the whole content), and surrounding spaces and tabs.
 */
internal fun atxHeadingContent(line: String, start: Int): IntRange {
    var contentStart = start
    while (contentStart < line.length && line[contentStart].isSpaceOrTab()) {
        contentStart += 1
    }
    var end = trimmedEnd(line, contentStart)
    var hashStart = end
    while (hashStart > contentStart && line[hashStart - 1] == '#') {
        hashStart -= 1
    }
    if (hashStart < end && (hashStart == contentStart || line[hashStart - 1].isSpaceOrTab())) {
        end = hashStart
        while (end > contentStart && line[end - 1].isSpaceOrTab()) {
            end -= 1
        }
    }
    return contentStart until end
}

internal data class FenceOpener(
    val fenceChar: Char,
    val fenceLength: Int,
)

/** A code fence opener: three or more backticks (with no backtick in the info string) or tildes. */
internal fun scanFenceOpener(line: String, start: Int): FenceOpener? {
    val fenceChar = line.getOrNull(start) ?: return null
    if (fenceChar != '`' && fenceChar != '~') {
        return null
    }
    var cursor = start
    while (cursor < line.length && line[cursor] == fenceChar) {
        cursor += 1
    }
    val fenceLength = cursor - start
    if (fenceLength < 3 || (fenceChar == '`' && line.indexOf('`', cursor) >= 0)) {
        return null
    }
    return FenceOpener(fenceChar = fenceChar, fenceLength = fenceLength)
}

/** A closing fence: at least [minLength] [fenceChar]s followed only by spaces or tabs. */
internal fun isClosingFence(line: String, start: Int, fenceChar: Char, minLength: Int): Boolean {
    var cursor = start
    while (cursor < line.length && line[cursor] == fenceChar) {
        cursor += 1
    }
    return cursor - start >= minLength && isBlankFrom(line, cursor)
}

/** Setext underline level: `=`s give 1, `-`s give 2; only trailing spaces or tabs may follow. */
internal fun setextUnderlineLevel(line: String, start: Int): Int? {
    val marker = line.getOrNull(start) ?: return null
    if (marker != '=' && marker != '-') {
        return null
    }
    var cursor = start
    while (cursor < line.length && line[cursor] == marker) {
        cursor += 1
    }
    if (!isBlankFrom(line, cursor)) {
        return null
    }
    return if (marker == '=') 1 else 2
}

internal data class ListMarker(
    /** `-`, `+` or `*` for bullet items, `null` for ordered items. */
    val bulletChar: Char?,
    /** Start number of an ordered item. */
    val startNumber: Int,
    /** `.` or `)` for ordered items, `null` for bullet items. */
    val delimiter: Char?,
    val length: Int,
)

/** A bullet marker or an ordered marker of one to nine digits followed by `.` or `)`. */
internal fun scanListMarker(line: String, start: Int): ListMarker? {
    val first = line.getOrNull(start) ?: return null
    if (first == '-' || first == '+' || first == '*') {
        return ListMarker(bulletChar = first, startNumber = 0, delimiter = null, length = 1)
    }
    var cursor = start
    while (cursor < line.length && cursor - start < 10 && line[cursor] in '0'..'9') {
        cursor += 1
    }
    val digits = cursor - start
    val delimiter = line.getOrNull(cursor)
    if (digits !in 1..9 || (delimiter != '.' && delimiter != ')')) {
        return null
    }
    return ListMarker(
        bulletChar = null,
        startNumber = line.substring(start, cursor).toInt(),
        delimiter = delimiter,
        length = digits + 1,
    )
}
