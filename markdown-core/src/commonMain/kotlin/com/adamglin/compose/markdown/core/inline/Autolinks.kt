package com.adamglin.compose.markdown.core.inline

/**
 * CommonMark URI autolink content (between `<` and `>`): a scheme of 2-32 characters, `:`, then the rest.
 * The caller guarantees the content has no spaces, controls, `<` or `>`.
 */
internal fun isUriAutolink(content: String): Boolean {
    val colon = content.indexOf(':')
    if (colon !in 2..32 || !content[0].isAsciiLetter()) {
        return false
    }
    for (index in 1 until colon) {
        val char = content[index]
        if (!char.isAsciiAlphanumeric() && char != '+' && char != '.' && char != '-') {
            return false
        }
    }
    return true
}

/** CommonMark email autolink content (between `<` and `>`), per the spec's HTML5-derived grammar. */
internal fun isEmailAutolink(content: String): Boolean {
    val at = content.indexOf('@')
    if (at <= 0) {
        return false
    }
    for (index in 0 until at) {
        val char = content[index]
        if (!char.isAsciiAlphanumeric() && char !in EMAIL_LOCAL_PUNCTUATION) {
            return false
        }
    }
    val labels = content.substring(at + 1).split('.')
    return labels.all { label ->
        label.length in 1..63 &&
            label.first().isAsciiAlphanumeric() &&
            label.last().isAsciiAlphanumeric() &&
            label.all { it.isAsciiAlphanumeric() || it == '-' }
    }
}

private const val EMAIL_LOCAL_PUNCTUATION = ".!#$%&'*+/=?^_`{|}~-"

/**
 * GFM extended autolinks ("Autolinks (extension)" in the GFM spec, behaviour of cmark-gfm).
 *
 * `www.` and `http://` / `https://` / `ftp://` links are recognised while scanning text (so their
 * paths are not split by emphasis delimiters); email addresses are recognised afterwards in the
 * finished text nodes, see [findEmailAutolinks].
 */
internal class ExtendedUrlAutolinkScanner(private val text: String) {
    data class Match(
        val endExclusive: Int,
        val destination: String,
    )

    private var lastQuery = -1
    private var lastResult: Match? = null

    // A domain run is a maximal run of domain characters. Queries starting inside the last run reuse it,
    // which keeps repeated attempts such as `_www.a_www.a_...` linear.
    private var runStart = -1
    private var runEnd = -1
    private var runLastDot = -1
    private var runSecondLastDot = -1
    private var runLastUnderscore = -1

    /** Returns the www/url autolink starting at [start], if any. Repeated queries for one position are cached. */
    fun matchAt(start: Int): Match? {
        if (start != lastQuery) {
            lastQuery = start
            lastResult = computeMatch(start)
        }
        return lastResult
    }

    private fun computeMatch(start: Int): Match? {
        if (start > 0) {
            val previous = text[start - 1]
            if (!previous.isUnicodeWhitespace() && previous != '*' && previous != '_' && previous != '~' && previous != '(') {
                return null
            }
        }
        val isWww = text.startsWith("www.", startIndex = start, ignoreCase = true)
        val domainStart = when {
            isWww -> start
            text.startsWith("http://", startIndex = start, ignoreCase = true) -> start + 7
            text.startsWith("https://", startIndex = start, ignoreCase = true) -> start + 8
            text.startsWith("ftp://", startIndex = start, ignoreCase = true) -> start + 6
            else -> return null
        }
        val domainEnd = validDomainEnd(domainStart, requirePeriod = isWww) ?: return null
        var end = domainEnd
        while (end < text.length && !text[end].isUnicodeWhitespace() && text[end] != '<') {
            end += 1
        }
        end = trimTrailingPunctuation(start, end)
        if (end <= (if (isWww) start + 4 else domainStart)) {
            return null
        }
        val literal = text.substring(start, end)
        return Match(
            endExclusive = end,
            destination = if (isWww) "http://$literal" else literal,
        )
    }

    /**
     * A valid domain is a run of alphanumerics, `_` and `-` separated by `.`, without underscores in
     * its last two segments; `www.` domains additionally need at least one period.
     */
    private fun validDomainEnd(start: Int, requirePeriod: Boolean): Int? {
        if (start !in runStart until runEnd) {
            scanDomainRun(start)
        }
        if (runEnd == start) {
            return null
        }
        if (requirePeriod && runLastDot < start) {
            return null
        }
        val lastTwoSegmentsStart = maxOf(start, runSecondLastDot + 1)
        if (runLastUnderscore >= lastTwoSegmentsStart) {
            return null
        }
        return runEnd
    }

    private fun scanDomainRun(start: Int) {
        runStart = start
        runLastDot = -1
        runSecondLastDot = -1
        runLastUnderscore = -1
        var cursor = start
        while (cursor < text.length) {
            when (val char = text[cursor]) {
                '.' -> {
                    runSecondLastDot = runLastDot
                    runLastDot = cursor
                }

                '_' -> runLastUnderscore = cursor
                '-' -> Unit
                else -> if (!char.isLetterOrDigit()) break
            }
            cursor += 1
        }
        runEnd = cursor
    }

    /** cmark-gfm `autolink_delim`: drops trailing punctuation, unbalanced `)` and entity-like `&name;` suffixes. */
    private fun trimTrailingPunctuation(start: Int, endExclusive: Int): Int {
        var opening = 0
        var closing = 0
        for (index in start until endExclusive) {
            when (text[index]) {
                '(' -> opening += 1
                ')' -> closing += 1
            }
        }
        var end = endExclusive
        while (end > start) {
            when (text[end - 1]) {
                '?', '!', '.', ',', ':', '*', '_', '~', '\'', '"' -> end -= 1
                ';' -> {
                    var cursor = end - 2
                    while (cursor > start && text[cursor].isAsciiAlphanumeric()) {
                        cursor -= 1
                    }
                    end = if (cursor < end - 2 && text[cursor] == '&') cursor else end - 1
                }

                ')' -> {
                    if (closing <= opening) {
                        break
                    }
                    closing -= 1
                    end -= 1
                }

                else -> break
            }
        }
        return end
    }
}

/**
 * Finds GFM extended email autolinks in [text]: a local part of alphanumerics and `.-_+`, `@`, then a
 * domain of alphanumeric, `-` and `_` segments separated by periods (at least one), not ending in `-`/`_`.
 * Returns `[start, endExclusive)` pairs in ascending order.
 */
internal fun findEmailAutolinks(text: String): List<IntRange> {
    val matches = mutableListOf<IntRange>()
    var searchFrom = 0
    while (true) {
        val at = text.indexOf('@', startIndex = searchFrom)
        if (at < 0) {
            break
        }
        val lowerBound = matches.lastOrNull()?.let { it.last + 1 } ?: 0
        var start = at
        while (start > lowerBound && text[start - 1].isEmailLocalChar()) {
            start -= 1
        }
        var end = at + 1
        var periods = 0
        while (end < text.length) {
            val char = text[end]
            if (char.isAsciiAlphanumeric() || char == '-' || char == '_') {
                end += 1
            } else if (char == '.' && end + 1 < text.length && text[end + 1].isAsciiAlphanumeric()) {
                periods += 1
                end += 1
            } else {
                break
            }
        }
        val valid = start < at &&
            periods > 0 &&
            text[end - 1] != '-' &&
            text[end - 1] != '_' &&
            (end == text.length || text[end] != '@')
        if (valid) {
            matches += start until end
            searchFrom = end
        } else {
            searchFrom = at + 1
        }
    }
    return matches
}

private fun Char.isEmailLocalChar(): Boolean = isAsciiAlphanumeric() || this == '.' || this == '-' || this == '_' || this == '+'
