package dev.markstream.core.inline

import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.model.InlineId
import dev.markstream.core.model.InlineNode
import dev.markstream.core.model.TextRange

internal class InlineParser(
    // TODO(stage-4): ChatFast currently uses a single inline rule set; keep dialect for future multi-dialect dispatch.
    private val dialect: MarkdownDialect,
) {
    fun parse(literal: String, range: TextRange): List<InlineNode> {
        if (literal.isEmpty()) {
            return emptyList()
        }
        return InlineScanner(
            text = literal,
            baseOffset = range.start,
        ).parseSegment(start = 0, endExclusive = literal.length)
    }

    private inner class InlineScanner(
        private val text: String,
        private val baseOffset: Int,
    ) {
        fun parseSegment(start: Int, endExclusive: Int): List<InlineNode> {
            if (start >= endExclusive) {
                return emptyList()
            }

            val nodes = mutableListOf<InlineNode>()
            var index = start
            var textStart = start

            fun flushText(end: Int) {
                if (end <= textStart) {
                    return
                }
                val literal = text.substring(textStart, end)
                nodes += InlineNode.Text(
                    id = inlineId(kind = "text", range = toRange(textStart, end), salt = literal.hashCode().toLong()),
                    range = toRange(textStart, end),
                    literal = literal,
                )
            }

            while (index < endExclusive) {
                val char = text[index]

                if (char == '\\') {
                    val hardBreak = parseBackslashHardBreak(index = index, endExclusive = endExclusive)
                    if (hardBreak != null) {
                        flushText(index)
                        nodes += hardBreak.node
                        index = hardBreak.nextIndex
                        textStart = index
                        continue
                    }

                    if (index + 1 < endExclusive && isEscapable(text[index + 1])) {
                        flushText(index)
                        val literal = text[index + 1].toString()
                        nodes += InlineNode.Text(
                            id = inlineId(kind = "text", range = toRange(index, index + 2), salt = literal.hashCode().toLong()),
                            range = toRange(index, index + 2),
                            literal = literal,
                        )
                        index += 2
                        textStart = index
                        continue
                    }
                }

                if (char == '\n') {
                    val breakNode = parseLineBreak(index = index, start = start)
                    flushText(breakNode.flushEnd)
                    nodes += breakNode.node
                    index += 1
                    textStart = index
                    continue
                }

                if (char == '`') {
                    val parsed = parseCodeSpan(index = index, endExclusive = endExclusive)
                    if (parsed != null) {
                        flushText(index)
                        nodes += parsed.node
                        index = parsed.nextIndex
                        textStart = index
                        continue
                    }
                }

                if (char == '[') {
                    val parsed = parseInlineLink(index = index, endExclusive = endExclusive)
                    if (parsed != null) {
                        flushText(index)
                        nodes += parsed.node
                        index = parsed.nextIndex
                        textStart = index
                        continue
                    }
                }

                if (char == '<') {
                    val parsed = parseAutolink(index = index, endExclusive = endExclusive)
                    if (parsed != null) {
                        flushText(index)
                        nodes += parsed.node
                        index = parsed.nextIndex
                        textStart = index
                        continue
                    }
                }

                val bareAutolink = parseBareAutolink(index = index, start = start, endExclusive = endExclusive)
                if (bareAutolink != null) {
                    flushText(index)
                    nodes += bareAutolink.node
                    index = bareAutolink.nextIndex
                    textStart = index
                    continue
                }

                val strike = parseDelimitedRun(
                    index = index,
                    endExclusive = endExclusive,
                    marker = "~~",
                    kind = DelimitedKind.Strikethrough,
                )
                if (strike != null) {
                    flushText(index)
                    nodes += strike.node
                    index = strike.nextIndex
                    textStart = index
                    continue
                }

                val strong = parseDelimitedRun(
                    index = index,
                    endExclusive = endExclusive,
                    marker = "**",
                    kind = DelimitedKind.Strong,
                ) ?: parseDelimitedRun(
                    index = index,
                    endExclusive = endExclusive,
                    marker = "__",
                    kind = DelimitedKind.Strong,
                )
                if (strong != null) {
                    flushText(index)
                    nodes += strong.node
                    index = strong.nextIndex
                    textStart = index
                    continue
                }

                val emphasis = parseDelimitedRun(
                    index = index,
                    endExclusive = endExclusive,
                    marker = "*",
                    kind = DelimitedKind.Emphasis,
                ) ?: parseDelimitedRun(
                    index = index,
                    endExclusive = endExclusive,
                    marker = "_",
                    kind = DelimitedKind.Emphasis,
                )
                if (emphasis != null) {
                    flushText(index)
                    nodes += emphasis.node
                    index = emphasis.nextIndex
                    textStart = index
                    continue
                }

                index += 1
            }

            flushText(endExclusive)
            return mergeAdjacentText(nodes)
        }

        private fun parseLineBreak(index: Int, start: Int): ParsedBreakNode {
            var trailingSpaces = 0
            var cursor = index - 1
            while (cursor >= start && text[cursor] == ' ') {
                trailingSpaces += 1
                cursor -= 1
            }

            if (trailingSpaces >= 2) {
                val flushEnd = index - trailingSpaces
                return ParsedBreakNode(
                    flushEnd = flushEnd,
                    node = InlineNode.HardBreak(
                        id = inlineId(kind = "hard-break", range = toRange(flushEnd, index + 1), salt = 0L),
                        range = toRange(flushEnd, index + 1),
                    ),
                )
            }

            return ParsedBreakNode(
                flushEnd = index,
                node = InlineNode.SoftBreak(
                    id = inlineId(kind = "soft-break", range = toRange(index, index + 1), salt = 0L),
                    range = toRange(index, index + 1),
                ),
            )
        }

        private fun parseBackslashHardBreak(index: Int, endExclusive: Int): ParsedInlineNode? {
            if (index + 1 >= endExclusive || text[index + 1] != '\n') {
                return null
            }
            val range = toRange(index, index + 2)
            return ParsedInlineNode(
                node = InlineNode.HardBreak(
                    id = inlineId(kind = "hard-break", range = range, salt = 1L),
                    range = range,
                ),
                nextIndex = index + 2,
            )
        }

        private fun parseCodeSpan(index: Int, endExclusive: Int): ParsedInlineNode? {
            val delimiterLength = backtickRunLength(index = index, endExclusive = endExclusive)
            if (delimiterLength == 0) {
                return null
            }
            val closeIndex = findBacktickRun(
                from = index + delimiterLength,
                endExclusive = endExclusive,
                length = delimiterLength,
            ) ?: return null

            val rawContent = text.substring(index + delimiterLength, closeIndex)
            val normalized = normalizeCodeSpan(rawContent)
            val range = toRange(index, closeIndex + delimiterLength)
            return ParsedInlineNode(
                node = InlineNode.CodeSpan(
                    id = inlineId(kind = "code", range = range, salt = normalized.hashCode().toLong()),
                    range = range,
                    literal = normalized,
                ),
                nextIndex = closeIndex + delimiterLength,
            )
        }

        private fun parseInlineLink(index: Int, endExclusive: Int): ParsedInlineNode? {
            val closingBracket = findMatchingBracket(from = index + 1, endExclusive = endExclusive) ?: return null
            if (closingBracket + 1 >= endExclusive || text[closingBracket + 1] != '(') {
                return null
            }
            val closingParen = findMatchingParen(from = closingBracket + 2, endExclusive = endExclusive) ?: return null
            val destinationRaw = text.substring(closingBracket + 2, closingParen).trim()
            if (destinationRaw.isEmpty()) {
                return null
            }

            val labelChildren = parseSegment(start = index + 1, endExclusive = closingBracket)
            val destination = unescapeDestination(destinationRaw)
            val range = toRange(index, closingParen + 1)
            return ParsedInlineNode(
                node = InlineNode.Link(
                    id = inlineId(kind = "link", range = range, salt = destination.hashCode().toLong()),
                    range = range,
                    destination = destination,
                    title = null,
                    children = labelChildren,
                ),
                nextIndex = closingParen + 1,
            )
        }

        private fun parseAutolink(index: Int, endExclusive: Int): ParsedInlineNode? {
            val closing = text.indexOf('>', startIndex = index + 1)
            if (closing == -1 || closing >= endExclusive) {
                return null
            }
            val destination = text.substring(index + 1, closing)
            if (!isAutolinkDestination(destination)) {
                return null
            }
            val range = toRange(index, closing + 1)
            return ParsedInlineNode(
                node = InlineNode.Link(
                    id = inlineId(kind = "autolink", range = range, salt = destination.hashCode().toLong()),
                    range = range,
                    destination = destination,
                    title = null,
                    children = listOf(
                        InlineNode.Text(
                            id = inlineId(kind = "text", range = toRange(index + 1, closing), salt = destination.hashCode().toLong()),
                            range = toRange(index + 1, closing),
                            literal = destination,
                        ),
                    ),
                ),
                nextIndex = closing + 1,
            )
        }

        private fun parseBareAutolink(index: Int, start: Int, endExclusive: Int): ParsedInlineNode? {
            if (!text.startsWith(prefix = "http://", startIndex = index) && !text.startsWith(prefix = "https://", startIndex = index)) {
                return null
            }
            if (index > start) {
                val previous = text[index - 1]
                if (!previous.isWhitespace() && previous != '(' && previous != '[') {
                    return null
                }
            }

            var end = index
            while (end < endExclusive && !text[end].isWhitespace() && text[end] !in BARE_AUTOLINK_STOP_CHARS) {
                end += 1
            }
            if (end <= index) {
                return null
            }

            var trimmedEnd = end
            while (trimmedEnd > index && text[trimmedEnd - 1] in BARE_AUTOLINK_TRAILING_PUNCTUATION) {
                trimmedEnd -= 1
            }
            if (trimmedEnd <= index) {
                return null
            }

            val destination = text.substring(index, trimmedEnd)
            val range = toRange(index, trimmedEnd)
            return ParsedInlineNode(
                node = InlineNode.Link(
                    id = inlineId(kind = "autolink", range = range, salt = destination.hashCode().toLong()),
                    range = range,
                    destination = destination,
                    title = null,
                    children = listOf(
                        InlineNode.Text(
                            id = inlineId(kind = "text", range = range, salt = destination.hashCode().toLong()),
                            range = range,
                            literal = destination,
                        ),
                    ),
                ),
                nextIndex = trimmedEnd,
            )
        }

        private fun parseDelimitedRun(
            index: Int,
            endExclusive: Int,
            marker: String,
            kind: DelimitedKind,
        ): ParsedInlineNode? {
            if (!text.startsWith(prefix = marker, startIndex = index)) {
                return null
            }
            val contentStart = index + marker.length
            val closingIndex = findClosingMarker(
                marker = marker,
                from = contentStart,
                endExclusive = endExclusive,
            ) ?: return null
            val children = parseSegment(start = contentStart, endExclusive = closingIndex)
            val range = toRange(index, closingIndex + marker.length)
            val node: InlineNode = when (kind) {
                DelimitedKind.Emphasis -> InlineNode.Emphasis(
                    id = inlineId(kind = "emphasis", range = range, salt = children.size.toLong()),
                    range = range,
                    children = children,
                )

                DelimitedKind.Strong -> InlineNode.Strong(
                    id = inlineId(kind = "strong", range = range, salt = children.size.toLong()),
                    range = range,
                    children = children,
                )

                DelimitedKind.Strikethrough -> InlineNode.Strikethrough(
                    id = inlineId(kind = "strikethrough", range = range, salt = children.size.toLong()),
                    range = range,
                    children = children,
                )
            }
            return ParsedInlineNode(node = node, nextIndex = closingIndex + marker.length)
        }

        private fun findClosingMarker(marker: String, from: Int, endExclusive: Int): Int? {
            var cursor = from
            while (cursor < endExclusive) {
                if (text[cursor] == '\\' && cursor + 1 < endExclusive) {
                    cursor += 2
                    continue
                }

                if (text[cursor] == '`') {
                    val delimiterLength = backtickRunLength(index = cursor, endExclusive = endExclusive)
                    if (delimiterLength > 0) {
                        val closingCode = findBacktickRun(
                            from = cursor + delimiterLength,
                            endExclusive = endExclusive,
                            length = delimiterLength,
                        )
                        if (closingCode != null) {
                            cursor = closingCode + delimiterLength
                            continue
                        }
                    }
                }

                if (text.startsWith(prefix = marker, startIndex = cursor)) {
                    return cursor
                }

                cursor += 1
            }
            return null
        }

        private fun findMatchingBracket(from: Int, endExclusive: Int): Int? {
            var depth = 0
            var cursor = from
            while (cursor < endExclusive) {
                if (text[cursor] == '\\' && cursor + 1 < endExclusive) {
                    cursor += 2
                    continue
                }
                when (text[cursor]) {
                    '[' -> depth += 1
                    ']' -> if (depth == 0) {
                        return cursor
                    } else {
                        depth -= 1
                    }
                }
                cursor += 1
            }
            return null
        }

        private fun findMatchingParen(from: Int, endExclusive: Int): Int? {
            var depth = 0
            var cursor = from
            while (cursor < endExclusive) {
                if (text[cursor] == '\\' && cursor + 1 < endExclusive) {
                    cursor += 2
                    continue
                }
                when (text[cursor]) {
                    '(' -> depth += 1
                    ')' -> if (depth == 0) {
                        return cursor
                    } else {
                        depth -= 1
                    }
                }
                cursor += 1
            }
            return null
        }

        private fun backtickRunLength(index: Int, endExclusive: Int): Int {
            var cursor = index
            while (cursor < endExclusive && text[cursor] == '`') {
                cursor += 1
            }
            return cursor - index
        }

        private fun findBacktickRun(from: Int, endExclusive: Int, length: Int): Int? {
            var cursor = from
            while (cursor < endExclusive) {
                if (text[cursor] != '`') {
                    cursor += 1
                    continue
                }
                val runLength = backtickRunLength(index = cursor, endExclusive = endExclusive)
                if (runLength == length) {
                    return cursor
                }
                cursor += runLength
            }
            return null
        }

        private fun normalizeCodeSpan(content: String): String {
            if (content.isEmpty()) {
                return content
            }
            val collapsed = content.replace('\n', ' ')
            if (collapsed.length >= 2 && collapsed.first() == ' ' && collapsed.last() == ' ' && collapsed.any { it != ' ' }) {
                return collapsed.substring(1, collapsed.length - 1)
            }
            return collapsed
        }

        private fun unescapeDestination(raw: String): String {
            val builder = StringBuilder(raw.length)
            var cursor = 0
            while (cursor < raw.length) {
                val current = raw[cursor]
                if (current == '\\' && cursor + 1 < raw.length) {
                    builder.append(raw[cursor + 1])
                    cursor += 2
                } else {
                    builder.append(current)
                    cursor += 1
                }
            }
            return builder.toString()
        }

        private fun isAutolinkDestination(destination: String): Boolean {
            val lowered = destination.lowercase()
            return lowered.startsWith("http://") ||
                lowered.startsWith("https://") ||
                lowered.startsWith("mailto:")
        }

        private fun isEscapable(char: Char): Boolean = char in ESCAPABLE_CHARS

        private fun toRange(start: Int, endExclusive: Int): TextRange = TextRange(
            start = baseOffset + start,
            endExclusive = baseOffset + endExclusive,
        )
    }

    private fun inlineId(kind: String, range: TextRange, salt: Long): InlineId {
        val kindSalt = kind.hashCode().toLong() shl 17
        val raw = (range.start.toLong() shl 32) xor range.endExclusive.toLong() xor kindSalt xor salt
        return InlineId(raw = raw and Long.MAX_VALUE)
    }

    private fun mergeAdjacentText(nodes: List<InlineNode>): List<InlineNode> {
        if (nodes.size < 2) {
            return nodes
        }
        val merged = mutableListOf<InlineNode>()
        for (node in nodes) {
            val previous = merged.lastOrNull()
            if (previous is InlineNode.Text && node is InlineNode.Text && previous.range.endExclusive == node.range.start) {
                val combinedRange = TextRange(
                    start = previous.range.start,
                    endExclusive = node.range.endExclusive,
                )
                val combinedLiteral = previous.literal + node.literal
                merged[merged.lastIndex] = InlineNode.Text(
                    id = inlineId(kind = "text", range = combinedRange, salt = combinedLiteral.hashCode().toLong()),
                    range = combinedRange,
                    literal = combinedLiteral,
                )
            } else {
                merged += node
            }
        }
        return merged
    }

    private enum class DelimitedKind {
        Emphasis,
        Strong,
        Strikethrough,
    }

    private data class ParsedInlineNode(
        val node: InlineNode,
        val nextIndex: Int,
    )

    private data class ParsedBreakNode(
        val flushEnd: Int,
        val node: InlineNode,
    )

    private companion object {
        val ESCAPABLE_CHARS: Set<Char> = setOf(
            '\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!', '~', '>', '<',
        )
        val BARE_AUTOLINK_STOP_CHARS: Set<Char> = setOf('<', '>', '"', '\'', ')', ']')
        val BARE_AUTOLINK_TRAILING_PUNCTUATION: Set<Char> = setOf('.', ',', ':', ';', '!', '?')
    }
}
