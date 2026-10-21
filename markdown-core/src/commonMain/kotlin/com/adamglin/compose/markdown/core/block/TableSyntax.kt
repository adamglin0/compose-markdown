package com.adamglin.compose.markdown.core.block

import com.adamglin.compose.markdown.core.model.TableAlignment

/**
 * GFM table row syntax, following cmark-gfm's table extension: a row is
 * `pipe? cell (pipe cell)* pipe?`, where a cell is any run of characters other than a `|` that is not
 * preceded by a backslash. As in cmark-gfm, `\|` is always an escaped pipe, even after another backslash.
 */

/** One cell of a row: [start, endExclusive) is the trimmed cell text in the scanned line. */
internal data class TableCellSpan(
    val start: Int,
    val endExclusive: Int,
    /** Cell text with `\|` unescaped to `|` (other escapes are left to the inline parser). */
    val text: String,
)

/** Splits `line[start, line.length)` into cells, or returns null when the line holds no cell. */
internal fun scanTableRow(line: String, start: Int): List<TableCellSpan>? {
    val cells = mutableListOf<TableCellSpan>()
    var cursor = skipCellSeparator(line, start)
    while (cursor < line.length) {
        val cellStart = cursor
        while (cursor < line.length && line[cursor] != '|') {
            cursor += if (line[cursor] == '\\' && line.getOrNull(cursor + 1) == '|') 2 else 1
        }
        val cellEnd = cursor
        cells += trimmedCell(line, cellStart, cellEnd)
        cursor = skipCellSeparator(line, cursor)
    }
    return cells.takeIf { it.isNotEmpty() }
}

/** Alignments of a delimiter row (`| :--- | ---: |`), or null when the line is not a delimiter row. */
internal fun scanTableDelimiterRow(line: String, start: Int): List<TableAlignment>? {
    val cells = scanTableRow(line, start) ?: return null
    return cells.map { cell -> delimiterCellAlignment(cell.text) ?: return null }
}

/** A delimiter cell is one or more hyphens, optionally wrapped by alignment colons. */
private fun delimiterCellAlignment(text: String): TableAlignment? {
    val left = text.startsWith(':')
    val right = text.length > 1 && text.endsWith(':')
    val hyphens = text.substring(if (left) 1 else 0, text.length - if (right) 1 else 0)
    if (hyphens.isEmpty() || hyphens.any { it != '-' }) {
        return null
    }
    return when {
        left && right -> TableAlignment.Center
        left -> TableAlignment.Left
        right -> TableAlignment.Right
        else -> TableAlignment.Default
    }
}

/** Skips an optional `|` and the spaces or tabs after it. */
private fun skipCellSeparator(line: String, start: Int): Int {
    if (start >= line.length || line[start] != '|') {
        return start
    }
    var cursor = start + 1
    while (cursor < line.length && line[cursor].isSpaceOrTab()) {
        cursor += 1
    }
    return cursor
}

private fun trimmedCell(line: String, start: Int, endExclusive: Int): TableCellSpan {
    var trimmedStart = start
    var trimmedEnd = endExclusive
    while (trimmedStart < trimmedEnd && line[trimmedStart].isSpaceOrTab()) {
        trimmedStart += 1
    }
    while (trimmedEnd > trimmedStart && line[trimmedEnd - 1].isSpaceOrTab()) {
        trimmedEnd -= 1
    }
    return TableCellSpan(
        start = trimmedStart,
        endExclusive = trimmedEnd,
        text = line.substring(trimmedStart, trimmedEnd).replace("\\|", "|"),
    )
}
