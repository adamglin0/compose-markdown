package com.adamglin.compose.markdown.core.inline

/**
 * Scanners for the link syntax shared by inline links and link reference definitions
 * (CommonMark 0.31.2, "Links" and "Link reference definitions").
 *
 * Every scanner reads `text[start, endExclusive)` and never looks past [endExclusive].
 */

internal data class ScannedLinkPart(
    val endExclusive: Int,
    val value: String,
)

internal const val MAX_LINK_LABEL_LENGTH = 999

/** Parentheses nesting limit for bare destinations; the spec allows a limit to keep scanning linear. */
private const val MAX_DESTINATION_PAREN_DEPTH = 32

/** Skips spaces and tabs, including at most one line ending. */
internal fun skipSpacesAndOneLineEnding(text: CharSequence, start: Int, endExclusive: Int): Int {
    var cursor = skipSpacesAndTabs(text, start, endExclusive)
    if (cursor < endExclusive && text[cursor] == '\n') {
        cursor = skipSpacesAndTabs(text, cursor + 1, endExclusive)
    }
    return cursor
}

internal fun skipSpacesAndTabs(text: CharSequence, start: Int, endExclusive: Int): Int {
    var cursor = start
    while (cursor < endExclusive && (text[cursor] == ' ' || text[cursor] == '\t')) {
        cursor += 1
    }
    return cursor
}

/**
 * Scans a link destination: either `<...>` without line endings or unescaped `<`/`>`, or a run of
 * non-space, non-control characters with balanced unescaped parentheses. An empty bare destination
 * is only valid when [allowEmptyBare] is set (inline links, where `[a]()` is a link).
 */
internal fun scanLinkDestination(
    text: CharSequence,
    start: Int,
    endExclusive: Int,
    allowEmptyBare: Boolean,
): ScannedLinkPart? {
    if (start < endExclusive && text[start] == '<') {
        var cursor = start + 1
        while (cursor < endExclusive) {
            when (text[cursor]) {
                '>' -> return ScannedLinkPart(
                    endExclusive = cursor + 1,
                    value = unescapeString(text, start + 1, cursor),
                )

                '<', '\n' -> return null
                '\\' -> cursor += if (cursor + 1 < endExclusive && text[cursor + 1].isAsciiPunctuation()) 2 else 1
                else -> cursor += 1
            }
        }
        return null
    }

    var cursor = start
    var depth = 0
    while (cursor < endExclusive) {
        val char = text[cursor]
        when {
            char == '\\' && cursor + 1 < endExclusive && text[cursor + 1].isAsciiPunctuation() -> cursor += 2
            char == '(' -> {
                depth += 1
                if (depth > MAX_DESTINATION_PAREN_DEPTH) {
                    return null
                }
                cursor += 1
            }

            char == ')' -> {
                if (depth == 0) {
                    break
                }
                depth -= 1
                cursor += 1
            }

            char == ' ' || char.isAsciiControl() -> break
            else -> cursor += 1
        }
    }
    if (depth != 0 || (cursor == start && !allowEmptyBare)) {
        return null
    }
    return ScannedLinkPart(endExclusive = cursor, value = unescapeString(text, start, cursor))
}

/** Scans a link title delimited by `"..."`, `'...'` or `(...)`. */
internal fun scanLinkTitle(text: CharSequence, start: Int, endExclusive: Int): ScannedLinkPart? {
    if (start >= endExclusive) {
        return null
    }
    val closer = when (text[start]) {
        '"' -> '"'
        '\'' -> '\''
        '(' -> ')'
        else -> return null
    }
    var cursor = start + 1
    while (cursor < endExclusive) {
        val char = text[cursor]
        when {
            char == '\\' && cursor + 1 < endExclusive && text[cursor + 1].isAsciiPunctuation() -> cursor += 2
            char == closer -> return ScannedLinkPart(
                endExclusive = cursor + 1,
                value = unescapeString(text, start + 1, cursor),
            )

            closer == ')' && char == '(' -> return null
            else -> cursor += 1
        }
    }
    return null
}

/**
 * Scans a link label `[...]` starting at [start] (which must be `[`) and returns the index after `]`.
 * Labels hold at most [MAX_LINK_LABEL_LENGTH] characters and no unescaped brackets.
 */
internal fun scanLinkLabel(text: CharSequence, start: Int, endExclusive: Int): Int? {
    if (start >= endExclusive || text[start] != '[') {
        return null
    }
    var cursor = start + 1
    while (cursor < endExclusive && cursor - start - 1 <= MAX_LINK_LABEL_LENGTH) {
        when (text[cursor]) {
            ']' -> return cursor + 1
            '[' -> return null
            '\\' -> cursor += if (cursor + 1 < endExclusive && text[cursor + 1].isAsciiPunctuation()) 2 else 1
            else -> cursor += 1
        }
    }
    return null
}

private fun Char.isAsciiControl(): Boolean = this < ' ' || this == '\u007F'
