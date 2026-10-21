package com.adamglin.compose.markdown.core.inline

/**
 * Character-level rules shared by inline parsing and link reference definitions:
 * backslash escapes, entity / numeric character references and CommonMark character classes.
 */

internal data class CharacterReference(
    val endExclusive: Int,
    val decoded: String,
)

private const val REPLACEMENT_CHARACTER = "�"
private const val MAX_ENTITY_NAME_LENGTH = 32

private val namedEntities: Map<String, String> by lazy {
    val map = HashMap<String, String>(2400)
    HTML_ENTITY_TABLE_CHUNKS.forEach { chunk ->
        chunk.split(' ').forEach { entry ->
            val separator = entry.indexOf(':')
            val decoded = StringBuilder()
            entry.substring(separator + 1).split('+').forEach { hex ->
                decoded.appendUnicodeCodePoint(hex.toInt(radix = 16))
            }
            map[entry.substring(0, separator)] = decoded.toString()
        }
    }
    map
}

/**
 * Matches an entity (`&amp;`) or numeric (`&#35;`, `&#X22;`) character reference starting at [start],
 * which must point at `&`.
 */
internal fun matchCharacterReference(text: CharSequence, start: Int, endExclusive: Int = text.length): CharacterReference? {
    var cursor = start + 1
    if (cursor >= endExclusive) {
        return null
    }
    if (text[cursor] == '#') {
        cursor += 1
        val hex = cursor < endExclusive && (text[cursor] == 'x' || text[cursor] == 'X')
        if (hex) {
            cursor += 1
        }
        val digitsStart = cursor
        val maxDigits = if (hex) 6 else 7
        var value = 0
        while (cursor < endExclusive && cursor - digitsStart < maxDigits) {
            val digit = text[cursor].asciiDigitValue(hex) ?: break
            value = value * (if (hex) 16 else 10) + digit
            cursor += 1
        }
        if (cursor == digitsStart || cursor >= endExclusive || text[cursor] != ';') {
            return null
        }
        val decoded = if (value == 0 || value > 0x10FFFF || value in 0xD800..0xDFFF) {
            REPLACEMENT_CHARACTER
        } else {
            StringBuilder(2).appendUnicodeCodePoint(value).toString()
        }
        return CharacterReference(endExclusive = cursor + 1, decoded = decoded)
    }

    val nameStart = cursor
    while (cursor < endExclusive && cursor - nameStart < MAX_ENTITY_NAME_LENGTH && text[cursor].isAsciiAlphanumeric()) {
        cursor += 1
    }
    if (cursor == nameStart || cursor >= endExclusive || text[cursor] != ';' || !text[nameStart].isAsciiLetter()) {
        return null
    }
    val decoded = namedEntities[text.substring(nameStart, cursor)] ?: return null
    return CharacterReference(endExclusive = cursor + 1, decoded = decoded)
}

/**
 * Resolves backslash escapes and character references, as required for link destinations,
 * link titles and code-free literal text.
 */
internal fun unescapeString(text: CharSequence, start: Int = 0, endExclusive: Int = text.length): String {
    val builder = StringBuilder(endExclusive - start)
    var cursor = start
    while (cursor < endExclusive) {
        val char = text[cursor]
        if (char == '\\' && cursor + 1 < endExclusive && text[cursor + 1].isAsciiPunctuation()) {
            builder.append(text[cursor + 1])
            cursor += 2
            continue
        }
        if (char == '&') {
            val reference = matchCharacterReference(text, cursor, endExclusive)
            if (reference != null) {
                builder.append(reference.decoded)
                cursor = reference.endExclusive
                continue
            }
        }
        builder.append(char)
        cursor += 1
    }
    return builder.toString()
}

internal fun Char.isAsciiPunctuation(): Boolean =
    this in '!'..'/' || this in ':'..'@' || this in '['..'`' || this in '{'..'~'

internal fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

internal fun Char.isAsciiAlphanumeric(): Boolean = isAsciiLetter() || this in '0'..'9'

/** CommonMark "Unicode whitespace character": Zs, tab, line feed, form feed, carriage return. */
internal fun Char.isUnicodeWhitespace(): Boolean =
    this == ' ' || this == '\t' || this == '\n' || this == '\u000C' || this == '\r' ||
        category == CharCategory.SPACE_SEPARATOR

/** CommonMark "Unicode punctuation character": general categories P and S. */
internal fun Char.isUnicodePunctuation(): Boolean = when (category) {
    CharCategory.CONNECTOR_PUNCTUATION,
    CharCategory.DASH_PUNCTUATION,
    CharCategory.START_PUNCTUATION,
    CharCategory.END_PUNCTUATION,
    CharCategory.INITIAL_QUOTE_PUNCTUATION,
    CharCategory.FINAL_QUOTE_PUNCTUATION,
    CharCategory.OTHER_PUNCTUATION,
    CharCategory.MATH_SYMBOL,
    CharCategory.CURRENCY_SYMBOL,
    CharCategory.MODIFIER_SYMBOL,
    CharCategory.OTHER_SYMBOL,
    -> true

    else -> false
}

private fun Char.asciiDigitValue(hex: Boolean): Int? = when {
    this in '0'..'9' -> this - '0'
    hex && this in 'a'..'f' -> this - 'a' + 10
    hex && this in 'A'..'F' -> this - 'A' + 10
    else -> null
}

private fun StringBuilder.appendUnicodeCodePoint(codePoint: Int): StringBuilder {
    if (codePoint < 0x10000) {
        append(codePoint.toChar())
    } else {
        val offset = codePoint - 0x10000
        append((0xD800 + (offset shr 10)).toChar())
        append((0xDC00 + (offset and 0x3FF)).toChar())
    }
    return this
}
