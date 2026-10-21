package com.adamglin.compose.markdown.core.inline

import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.internal.LinkReferenceDefinition
import com.adamglin.compose.markdown.core.internal.normalizeReferenceLabel
import com.adamglin.compose.markdown.core.model.InlineId
import com.adamglin.compose.markdown.core.model.InlineNode
import com.adamglin.compose.markdown.core.model.MathInlineDelimiter
import com.adamglin.compose.markdown.core.model.TextRange

/**
 * Inline parser following the CommonMark 0.31.2 reference algorithm (appendix "A parsing strategy"):
 * a single left-to-right scan that emits text, code spans, math spans, autolinks and breaks, while
 * recording emphasis delimiter runs on a delimiter stack and `[` / `![` openers on a bracket stack.
 * Closing brackets resolve links ("look for link or image") and the delimiter stack is resolved by
 * "process emphasis". GFM strikethrough and extended autolinks, plus inline math, are gated by
 * [MarkdownDialect.inlineFeatures].
 *
 * Every scan is bounded so that parsing stays linear on pathological input: closers for code spans
 * and math spans are looked up through forward-only indexes, link openers are never re-scanned, and
 * the emphasis pass uses the spec's `openers_bottom` table.
 */
internal class InlineParser(
    private val dialect: MarkdownDialect,
    private val referenceDefinitions: Map<String, LinkReferenceDefinition>,
) {
    fun parse(literal: String, range: TextRange): InlineParseResult {
        if (literal.isEmpty()) {
            return InlineParseResult(
                nodes = emptyList(),
                referenceLabels = emptySet(),
                unresolvedReferenceLabels = emptySet(),
            )
        }
        return InlineRun(text = literal, baseOffset = range.start).parse()
    }

    private inner class InlineRun(
        private val text: String,
        private val baseOffset: Int,
    ) {
        private val features = dialect.inlineFeatures
        private val root = ContainerDraft(kind = ContainerKind.Root, start = 0, end = text.length)
        private var pos = 0
        private var delimiters: Delimiter? = null
        private var brackets: Bracket? = null
        private var openBracketCount = 0
        private val referenceLabels: MutableSet<String> = linkedSetOf()
        private val unresolvedReferenceLabels: MutableSet<String> = linkedSetOf()
        private val urlAutolinks = if (features.bareAutolinks) ExtendedUrlAutolinkScanner(text) else null
        private val backtickRuns by lazy(LazyThreadSafetyMode.NONE) { BacktickRunIndex(text) }
        private val dollarMathClosers = ForwardFinder(text.length, ::isDollarMathCloser)
        private val parenMathClosers = ForwardFinder(text.length, ::isParenMathCloser)

        fun parse(): InlineParseResult {
            while (pos < text.length) {
                parseInline()
            }
            processEmphasis(stackBottom = null)
            return InlineParseResult(
                nodes = convertChildren(root, insideLink = false, depth = 0),
                referenceLabels = referenceLabels,
                unresolvedReferenceLabels = unresolvedReferenceLabels,
            )
        }

        private fun parseInline() {
            val handled = when (text[pos]) {
                '\n' -> parseLineEnding()
                ' ' -> parseSpacesBeforeLineEnding()
                '\\' -> parseBackslash()
                '`' -> features.inlineCode && parseCodeSpan()
                '*', '_' -> (features.emphasis || features.strong) && parseDelimiterRun()
                '~' -> features.strikethrough && parseDelimiterRun()
                '$' -> features.inlineMath && parseDollarMath()
                '[' -> (features.inlineLinks || features.referenceLinks) && openBracket(image = false)
                '!' -> features.images && text.getOrNull(pos + 1) == '[' && openBracket(image = true)
                ']' -> closeBracket()
                '<' -> features.autolinks && parseAngleAutolink()
                '&' -> parseCharacterReference()
                else -> parseExtendedUrlAutolink()
            }
            if (!handled) {
                parseText()
            }
        }

        // region Text, breaks and escapes

        private fun parseText() {
            val start = pos
            if (text[pos] != ' ') {
                pos += 1
            }
            while (pos < text.length) {
                val char = text[pos]
                if (char == ' ') {
                    val runEnd = skipSpaces(pos)
                    if (runEnd == text.length || text[runEnd] == '\n') {
                        break
                    }
                    pos = runEnd
                    continue
                }
                if (char in SPECIAL_CHARS || extendedUrlAutolinkAt(pos) != null) {
                    break
                }
                pos += 1
            }
            appendText(start, pos, text.substring(start, pos))
        }

        /** Line ending: soft break, then the next line's leading spaces are dropped. */
        private fun parseLineEnding(): Boolean {
            val start = pos
            appendBreakOrNewline(start)
            pos = skipSpacesAndTabs(text, start + 1, text.length)
            return true
        }

        /** Spaces before a line ending form a hard break (two or more) or are dropped; trailing spaces are dropped. */
        private fun parseSpacesBeforeLineEnding(): Boolean {
            val start = pos
            val runEnd = skipSpaces(start)
            if (runEnd == text.length) {
                pos = runEnd
                return true
            }
            if (text[runEnd] != '\n') {
                return false
            }
            if (runEnd - start >= 2 && features.hardBreaks) {
                appendLeaf(
                    InlineNode.HardBreak(
                        id = inlineId(kind = "hard-break", range = toRange(start, runEnd + 1), salt = 0L),
                        range = toRange(start, runEnd + 1),
                    ),
                )
                pos = skipSpacesAndTabs(text, runEnd + 1, text.length)
            } else {
                pos = runEnd
            }
            return true
        }

        private fun parseBackslash(): Boolean {
            val start = pos
            val next = text.getOrNull(start + 1)
            when {
                next == '\n' && features.hardBreaks -> {
                    appendLeaf(
                        InlineNode.HardBreak(
                            id = inlineId(kind = "hard-break", range = toRange(start, start + 2), salt = 1L),
                            range = toRange(start, start + 2),
                        ),
                    )
                    pos = skipSpacesAndTabs(text, start + 2, text.length)
                }

                next == '(' && features.inlineMath && parseParenMath() -> Unit

                next != null && next.isAsciiPunctuation() -> {
                    pos = start + 2
                    appendText(start, pos, next.toString())
                }

                else -> {
                    pos = start + 1
                    appendText(start, pos, "\\")
                }
            }
            return true
        }

        private fun parseCharacterReference(): Boolean {
            val reference = matchCharacterReference(text, pos) ?: return false
            appendText(pos, reference.endExclusive, reference.decoded)
            pos = reference.endExclusive
            return true
        }

        private fun appendBreakOrNewline(index: Int) {
            val range = toRange(index, index + 1)
            appendLeaf(
                if (features.softBreaks) {
                    InlineNode.SoftBreak(id = inlineId(kind = "soft-break", range = range, salt = 0L), range = range)
                } else {
                    InlineNode.Text(id = inlineId(kind = "text", range = range, salt = '\n'.code.toLong()), range = range, literal = "\n")
                },
            )
        }

        private fun skipSpaces(start: Int): Int {
            var cursor = start
            while (cursor < text.length && text[cursor] == ' ') {
                cursor += 1
            }
            return cursor
        }

        // endregion

        // region Code spans and math spans

        private fun parseCodeSpan(): Boolean {
            val start = pos
            var openerEnd = start
            while (openerEnd < text.length && text[openerEnd] == '`') {
                openerEnd += 1
            }
            val length = openerEnd - start
            val closer = backtickRuns.nextRun(length = length, from = openerEnd)
            if (closer < 0) {
                pos = openerEnd
                appendText(start, openerEnd, text.substring(start, openerEnd))
                return true
            }
            val literal = normalizeCodeSpan(text.substring(openerEnd, closer))
            val range = toRange(start, closer + length)
            appendLeaf(
                InlineNode.CodeSpan(
                    id = inlineId(kind = "code", range = range, salt = literal.hashCode().toLong()),
                    range = range,
                    literal = literal,
                ),
            )
            pos = closer + length
            return true
        }

        /** `$...$`: the opener is not followed by whitespace or `$`, see [isDollarMathCloser] for the closer. */
        private fun parseDollarMath(): Boolean {
            val start = pos
            val first = text.getOrNull(start + 1) ?: return false
            if (first.isWhitespace() || first == '$') {
                return false
            }
            val closer = dollarMathClosers.firstAtOrAfter(start + 2)
            if (closer < 0) {
                return false
            }
            appendMathSpan(start = start, end = closer + 1, latex = text.substring(start + 1, closer), delimiter = MathInlineDelimiter.Dollar)
            return true
        }

        /** `\(...\)`, entered from [parseBackslash]. */
        private fun parseParenMath(): Boolean {
            val start = pos
            val closer = parenMathClosers.firstAtOrAfter(start + 2)
            if (closer < 0) {
                return false
            }
            val latex = text.substring(start + 2, closer).trim()
            if (latex.isEmpty()) {
                return false
            }
            appendMathSpan(start = start, end = closer + 2, latex = latex, delimiter = MathInlineDelimiter.Paren)
            return true
        }

        private fun appendMathSpan(start: Int, end: Int, latex: String, delimiter: MathInlineDelimiter) {
            val range = toRange(start, end)
            appendLeaf(
                InlineNode.MathSpan(
                    id = inlineId(kind = "math", range = range, salt = latex.hashCode().toLong()),
                    range = range,
                    latex = latex,
                    delimiter = delimiter,
                ),
            )
            pos = end
        }

        /**
         * A closing `$` is not escaped, not preceded by whitespace and not followed by a digit,
         * so currency such as `$5 and $10` stays text.
         */
        private fun isDollarMathCloser(index: Int): Boolean =
            text[index] == '$' &&
                !text[index - 1].isWhitespace() &&
                text.getOrNull(index + 1)?.let { it in '0'..'9' } != true &&
                backslashRunLength(endInclusive = index - 1) % 2 == 0

        private fun isParenMathCloser(index: Int): Boolean =
            text[index] == '\\' &&
                text.getOrNull(index + 1) == ')' &&
                backslashRunLength(endInclusive = index) % 2 == 1

        private fun backslashRunLength(endInclusive: Int): Int {
            var cursor = endInclusive
            while (cursor >= 0 && text[cursor] == '\\') {
                cursor -= 1
            }
            return endInclusive - cursor
        }

        // endregion

        // region Autolinks

        private fun parseAngleAutolink(): Boolean {
            val start = pos
            var cursor = start + 1
            while (cursor < text.length) {
                val char = text[cursor]
                if (char == '>' || char == '<' || char <= ' ' || char == '\u007F') {
                    break
                }
                cursor += 1
            }
            if (cursor >= text.length || text[cursor] != '>') {
                return false
            }
            val content = text.substring(start + 1, cursor)
            val destination = when {
                isUriAutolink(content) -> content
                isEmailAutolink(content) -> "mailto:$content"
                else -> return false
            }
            pos = cursor + 1
            appendLeaf(autolinkNode(start = start, end = pos, contentStart = start + 1, contentEnd = cursor, destination = destination))
            return true
        }

        private fun parseExtendedUrlAutolink(): Boolean {
            val match = extendedUrlAutolinkAt(pos) ?: return false
            val start = pos
            pos = match.endExclusive
            appendLeaf(autolinkNode(start = start, end = pos, contentStart = start, contentEnd = pos, destination = match.destination))
            return true
        }

        /** Extended autolinks are not recognised inside link text, where they would swallow `](...)`. */
        private fun extendedUrlAutolinkAt(index: Int): ExtendedUrlAutolinkScanner.Match? =
            if (urlAutolinks == null || openBracketCount > 0) null else urlAutolinks.matchAt(index)

        private fun autolinkNode(start: Int, end: Int, contentStart: Int, contentEnd: Int, destination: String): InlineNode.Link {
            val range = toRange(start, end)
            val literal = text.substring(contentStart, contentEnd)
            return InlineNode.Link(
                id = inlineId(kind = "autolink", range = range, salt = destination.hashCode().toLong()),
                range = range,
                destination = destination,
                title = null,
                children = listOf(textNode(toRange(contentStart, contentEnd), literal)),
            )
        }

        // endregion

        // region Emphasis delimiters

        private fun parseDelimiterRun(): Boolean {
            val char = text[pos]
            val start = pos
            var end = start
            while (end < text.length && text[end] == char) {
                end += 1
            }
            pos = end
            val node = appendText(start, end, text.substring(start, end))
            val count = end - start
            if (char == '~' && count > 2) {
                return true
            }

            val before = if (start == 0) '\n' else text[start - 1]
            val after = if (end == text.length) '\n' else text[end]
            val beforeWhitespace = before.isUnicodeWhitespace()
            val afterWhitespace = after.isUnicodeWhitespace()
            val beforePunctuation = before.isUnicodePunctuation()
            val afterPunctuation = after.isUnicodePunctuation()
            val leftFlanking = !afterWhitespace && (!afterPunctuation || beforeWhitespace || beforePunctuation)
            val rightFlanking = !beforeWhitespace && (!beforePunctuation || afterWhitespace || afterPunctuation)
            val canOpen: Boolean
            val canClose: Boolean
            if (char == '_') {
                canOpen = leftFlanking && (!rightFlanking || beforePunctuation)
                canClose = rightFlanking && (!leftFlanking || afterPunctuation)
            } else {
                canOpen = leftFlanking
                canClose = rightFlanking
            }
            if (canOpen || canClose) {
                val delimiter = Delimiter(
                    char = char,
                    count = count,
                    originalCount = count,
                    node = node,
                    canOpen = canOpen,
                    canClose = canClose,
                )
                delimiter.previous = delimiters
                delimiters?.next = delimiter
                delimiters = delimiter
            }
            return true
        }

        /** CommonMark "process emphasis", extended with GFM strikethrough (equal-length `~`/`~~` runs). */
        private fun processEmphasis(stackBottom: Delimiter?) {
            // stackBottom is always still on the stack; returning early when nothing lies above it keeps
            // the walk below proportional to the delimiters it is about to consume.
            if (delimiters == stackBottom) {
                return
            }
            val openersBottom = HashMap<Int, Delimiter?>()
            var closer = delimiters
            while (closer != null && closer.previous != stackBottom) {
                closer = closer.previous
            }
            while (closer != null) {
                if (!closer.canClose) {
                    closer = closer.next
                    continue
                }
                val bottomKey = openersBottomKey(closer)
                val bottom = if (openersBottom.containsKey(bottomKey)) openersBottom[bottomKey] else stackBottom
                var opener = closer.previous
                var openerFound = false
                while (opener != null && opener != stackBottom && opener != bottom) {
                    if (opener.char == closer.char && opener.canOpen && !isOddMatch(opener, closer)) {
                        openerFound = true
                        break
                    }
                    opener = opener.previous
                }
                val oldCloser = closer
                if (opener != null && openerFound) {
                    closer = if (closer.char == '~') {
                        matchStrikethrough(opener, closer)
                    } else {
                        matchEmphasis(opener, closer)
                    }
                } else {
                    closer = closer.next
                    openersBottom[bottomKey] = oldCloser.previous
                    if (!oldCloser.canOpen) {
                        removeDelimiter(oldCloser)
                    }
                }
            }
            while (delimiters != null && delimiters != stackBottom) {
                removeDelimiter(delimiters!!)
            }
        }

        private fun openersBottomKey(closer: Delimiter): Int = if (closer.char == '~') {
            closer.char.code shl 3
        } else {
            (closer.char.code shl 3) or (if (closer.canOpen) 4 else 0) or (closer.originalCount % 3)
        }

        /** The "rule of 3": a run that can both open and close cannot match when the lengths sum to a multiple of 3. */
        private fun isOddMatch(opener: Delimiter, closer: Delimiter): Boolean =
            closer.char != '~' &&
                (closer.canOpen || opener.canClose) &&
                closer.originalCount % 3 != 0 &&
                (opener.originalCount + closer.originalCount) % 3 == 0

        /** Returns the next closer to examine. */
        private fun matchEmphasis(opener: Delimiter, closer: Delimiter): Delimiter? {
            var used = if (closer.count >= 2 && opener.count >= 2) 2 else 1
            if (used == 2 && !features.strong) {
                used = 1
            }
            if (used == 1 && !features.emphasis) {
                return closer.next
            }
            val openerNode = opener.node
            val closerNode = closer.node
            opener.count -= used
            closer.count -= used
            openerNode.literal = openerNode.literal.substring(0, opener.count)
            openerNode.end -= used
            closerNode.literal = closerNode.literal.substring(used)
            closerNode.start += used

            val container = ContainerDraft(
                kind = if (used == 2) ContainerKind.Strong else ContainerKind.Emphasis,
                start = openerNode.end,
                end = closerNode.start,
            )
            wrapBetween(openerNode, closerNode, container)
            removeDelimitersBetween(opener, closer)
            if (opener.count == 0) {
                openerNode.unlink()
                removeDelimiter(opener)
            }
            if (closer.count == 0) {
                closerNode.unlink()
                val next = closer.next
                removeDelimiter(closer)
                return next
            }
            return closer
        }

        private fun matchStrikethrough(opener: Delimiter, closer: Delimiter): Delimiter? {
            if (opener.count != closer.count) {
                return closer.next
            }
            val openerNode = opener.node
            val closerNode = closer.node
            val container = ContainerDraft(kind = ContainerKind.Strikethrough, start = openerNode.start, end = closerNode.end)
            wrapBetween(openerNode, closerNode, container)
            removeDelimitersBetween(opener, closer)
            openerNode.unlink()
            closerNode.unlink()
            removeDelimiter(opener)
            val next = closer.next
            removeDelimiter(closer)
            return next
        }

        /** Moves the siblings strictly between [first] and [last] into [container], placed after [first]. */
        private fun wrapBetween(first: Draft, last: Draft, container: ContainerDraft) {
            var child = first.next
            while (child != null && child != last) {
                val next = child.next
                child.unlink()
                container.append(child)
                child = next
            }
            first.insertAfter(container)
        }

        private fun removeDelimitersBetween(opener: Delimiter, closer: Delimiter) {
            opener.next = closer
            closer.previous = opener
        }

        private fun removeDelimiter(delimiter: Delimiter) {
            delimiter.previous?.next = delimiter.next
            val next = delimiter.next
            if (next == null) {
                delimiters = delimiter.previous
            } else {
                next.previous = delimiter.previous
            }
        }

        // endregion

        // region Links and images

        private fun openBracket(image: Boolean): Boolean {
            val start = pos
            pos += if (image) 2 else 1
            val node = appendText(start, pos, text.substring(start, pos))
            val below = brackets
            below?.bracketAfter = true
            brackets = Bracket(
                node = node,
                previous = below,
                previousLink = if (below == null || !below.image) below else below.previousLink,
                previousDelimiter = delimiters,
                start = start,
                labelStart = pos,
                image = image,
            )
            openBracketCount += 1
            return true
        }

        private fun removeBracket() {
            brackets = brackets?.previous
            openBracketCount -= 1
        }

        /** CommonMark "look for link or image". */
        private fun closeBracket(): Boolean {
            val opener = brackets ?: return false
            val closeStart = pos
            pos += 1
            if (!opener.active) {
                removeBracket()
                appendText(closeStart, pos, "]")
                return true
            }
            val target = matchInlineLinkTarget(opener) ?: matchReferenceTarget(opener, closeStart)
            if (target == null) {
                removeBracket()
                pos = closeStart + 1
                appendText(closeStart, pos, "]")
                return true
            }

            val container = ContainerDraft(
                kind = if (opener.image) ContainerKind.Image else ContainerKind.Link,
                start = opener.start,
                end = pos,
            )
            container.target = target
            var child = opener.node.next
            while (child != null) {
                val next = child.next
                child.unlink()
                container.append(child)
                child = next
            }
            root.append(container)
            processEmphasis(stackBottom = opener.previousDelimiter)
            removeBracket()
            opener.node.unlink()
            if (!opener.image) {
                // Links may not contain links: deactivate earlier link openers. Openers below an
                // already inactive one were deactivated together with it, so each is visited once.
                var earlier = opener.previousLink
                while (earlier != null && earlier.active) {
                    earlier.active = false
                    earlier = earlier.previousLink
                }
            }
            return true
        }

        /** `(destination "title")` after `]`. */
        private fun matchInlineLinkTarget(opener: Bracket): LinkTarget? {
            val enabled = if (opener.image) features.images else features.inlineLinks
            if (!enabled || pos >= text.length || text[pos] != '(') {
                return null
            }
            var cursor = skipSpacesAndOneLineEnding(text, pos + 1, text.length)
            val destination = scanLinkDestination(text, cursor, text.length, allowEmptyBare = true) ?: return null
            cursor = destination.endExclusive
            val titleStart = skipSpacesAndOneLineEnding(text, cursor, text.length)
            var title: String? = null
            cursor = titleStart
            if (titleStart > destination.endExclusive) {
                val scannedTitle = scanLinkTitle(text, titleStart, text.length)
                if (scannedTitle != null) {
                    title = scannedTitle.value
                    cursor = skipSpacesAndOneLineEnding(text, scannedTitle.endExclusive, text.length)
                }
            }
            if (cursor >= text.length || text[cursor] != ')') {
                return null
            }
            pos = cursor + 1
            return LinkTarget(destination = destination.value, title = title, referenceLabel = null)
        }

        /** Full `[text][label]`, collapsed `[label][]` and shortcut `[label]` references. */
        private fun matchReferenceTarget(opener: Bracket, closeStart: Int): LinkTarget? {
            if (!features.referenceLinks) {
                return null
            }
            val afterClose = closeStart + 1
            val labelEnd = scanLinkLabel(text, afterClose, text.length)
            val rawLabel: String
            val end: Int
            if (labelEnd != null && labelEnd - afterClose > 2) {
                rawLabel = text.substring(afterClose + 1, labelEnd - 1)
                end = labelEnd
            } else if (!opener.bracketAfter && closeStart - opener.labelStart <= MAX_LINK_LABEL_LENGTH) {
                rawLabel = text.substring(opener.labelStart, closeStart)
                end = if (labelEnd != null) labelEnd else afterClose
            } else {
                return null
            }
            val label = normalizeReferenceLabel(rawLabel) ?: return null
            referenceLabels += label
            val definition = referenceDefinitions[label]
            if (definition == null) {
                unresolvedReferenceLabels += label
                return null
            }
            pos = end
            return LinkTarget(destination = definition.destination, title = definition.title, referenceLabel = definition.label)
        }

        // endregion

        // region Draft tree

        private fun appendText(start: Int, end: Int, literal: String): TextDraft {
            val node = TextDraft(start = start, end = end, literal = literal)
            root.append(node)
            return node
        }

        private fun appendLeaf(node: InlineNode) {
            root.append(LeafDraft(node))
        }

        /**
         * Converts the draft tree into immutable nodes. Containers nested deeper than [MAX_INLINE_NESTING]
         * stay literal source text, which bounds the depth of the resulting tree (and of every recursive
         * consumer such as renderers and structural equality) on pathological input.
         */
        private fun convertChildren(container: ContainerDraft, insideLink: Boolean, depth: Int): List<InlineNode> {
            val nodes = mutableListOf<InlineNode>()
            val pendingText = mutableListOf<TextDraft>()
            fun flushText() {
                if (pendingText.isNotEmpty()) {
                    appendTextNodes(nodes, pendingText, insideLink)
                    pendingText.clear()
                }
            }

            fun addText(segment: TextDraft) {
                if (pendingText.isNotEmpty() && pendingText.last().end != segment.start) {
                    flushText()
                }
                pendingText += segment
            }

            var child = container.first
            while (child != null) {
                when (child) {
                    is TextDraft -> if (child.literal.isNotEmpty()) {
                        addText(child)
                    }

                    is LeafDraft -> {
                        flushText()
                        nodes += child.node
                    }

                    is ContainerDraft -> if (depth >= MAX_INLINE_NESTING) {
                        addText(TextDraft(start = child.start, end = child.end, literal = text.substring(child.start, child.end)))
                    } else {
                        flushText()
                        nodes += convertContainer(child, insideLink, depth + 1)
                    }
                }
                child = child.next
            }
            flushText()
            return nodes
        }

        private fun convertContainer(container: ContainerDraft, insideLink: Boolean, depth: Int): InlineNode {
            val range = toRange(container.start, container.end)
            return when (container.kind) {
                ContainerKind.Emphasis -> {
                    val children = convertChildren(container, insideLink, depth)
                    InlineNode.Emphasis(id = inlineId("emphasis", range, children.size.toLong()), range = range, children = children)
                }

                ContainerKind.Strong -> {
                    val children = convertChildren(container, insideLink, depth)
                    InlineNode.Strong(id = inlineId("strong", range, children.size.toLong()), range = range, children = children)
                }

                ContainerKind.Strikethrough -> {
                    val children = convertChildren(container, insideLink, depth)
                    InlineNode.Strikethrough(id = inlineId("strikethrough", range, children.size.toLong()), range = range, children = children)
                }

                ContainerKind.Link -> {
                    val target = requireNotNull(container.target)
                    InlineNode.Link(
                        id = inlineId(
                            kind = if (target.referenceLabel == null) "link" else "ref-link",
                            range = range,
                            salt = target.destination.hashCode().toLong(),
                        ),
                        range = range,
                        destination = target.destination,
                        title = target.title,
                        children = convertChildren(container, insideLink = true, depth = depth),
                        referenceLabel = target.referenceLabel,
                    )
                }

                ContainerKind.Image -> {
                    val target = requireNotNull(container.target)
                    InlineNode.Image(
                        id = inlineId(
                            kind = if (target.referenceLabel == null) "image" else "ref-image",
                            range = range,
                            salt = target.destination.hashCode().toLong(),
                        ),
                        range = range,
                        destination = target.destination,
                        title = target.title,
                        alt = convertChildren(container, insideLink = true, depth = depth),
                        referenceLabel = target.referenceLabel,
                    )
                }

                ContainerKind.Root -> error("The root draft is never nested")
            }
        }

        /**
         * Emits contiguous text segments as one text node, splitting out GFM email autolinks.
         * Escapes and character references make a segment's literal differ from its source; offsets
         * inside such a segment map to the segment's boundaries.
         */
        private fun appendTextNodes(nodes: MutableList<InlineNode>, segments: List<TextDraft>, insideLink: Boolean) {
            val literal = if (segments.size == 1) segments[0].literal else segments.joinToString(separator = "") { it.literal }
            val emails = if (features.bareAutolinks && !insideLink) findEmailAutolinks(literal) else emptyList()
            if (emails.isEmpty()) {
                nodes += textNode(toRange(segments.first().start, segments.last().end), literal)
                return
            }

            val literalStarts = IntArray(segments.size)
            var offset = 0
            segments.forEachIndexed { index, segment ->
                literalStarts[index] = offset
                offset += segment.literal.length
            }
            fun sourceOffset(literalOffset: Int): Int {
                if (literalOffset >= literal.length) {
                    return segments.last().end
                }
                var index = literalStarts.size - 1
                while (literalStarts[index] > literalOffset) {
                    index -= 1
                }
                val segment = segments[index]
                val inner = literalOffset - literalStarts[index]
                return when {
                    segment.literal.length == segment.end - segment.start -> segment.start + inner
                    inner == 0 -> segment.start
                    else -> segment.end
                }
            }

            var literalCursor = 0
            emails.forEach { email ->
                if (email.first > literalCursor) {
                    nodes += textNode(toRange(sourceOffset(literalCursor), sourceOffset(email.first)), literal.substring(literalCursor, email.first))
                }
                val address = literal.substring(email.first, email.last + 1)
                val range = toRange(sourceOffset(email.first), sourceOffset(email.last + 1))
                nodes += InlineNode.Link(
                    id = inlineId(kind = "autolink", range = range, salt = address.hashCode().toLong()),
                    range = range,
                    destination = "mailto:$address",
                    title = null,
                    children = listOf(textNode(range, address)),
                )
                literalCursor = email.last + 1
            }
            if (literalCursor < literal.length) {
                nodes += textNode(toRange(sourceOffset(literalCursor), segments.last().end), literal.substring(literalCursor))
            }
        }

        private fun textNode(range: TextRange, literal: String): InlineNode.Text = InlineNode.Text(
            id = inlineId(kind = "text", range = range, salt = literal.hashCode().toLong()),
            range = range,
            literal = literal,
        )

        private fun toRange(start: Int, endExclusive: Int): TextRange = TextRange(
            start = baseOffset + start,
            endExclusive = baseOffset + endExclusive,
        )

        // endregion
    }

    private fun inlineId(kind: String, range: TextRange, salt: Long): InlineId {
        val kindSalt = kind.hashCode().toLong() shl 17
        val raw = (range.start.toLong() shl 32) xor range.endExclusive.toLong() xor kindSalt xor salt
        return InlineId(raw = raw and Long.MAX_VALUE)
    }

    private companion object {
        /** Characters that may start an inline construct and therefore end a plain text run. */
        val SPECIAL_CHARS: Set<Char> = setOf('\n', '\\', '`', '*', '_', '~', '$', '[', ']', '!', '<', '&')

        /** Maximum depth of nested emphasis / link / image nodes; deeper constructs stay literal text. */
        const val MAX_INLINE_NESTING = 32
    }
}

internal data class InlineParseResult(
    val nodes: List<InlineNode>,
    val referenceLabels: Set<String>,
    val unresolvedReferenceLabels: Set<String>,
)

/** Code span content: line endings become spaces, and one surrounding space is stripped from both sides. */
private fun normalizeCodeSpan(content: String): String {
    val collapsed = content.replace('\n', ' ')
    if (collapsed.length >= 2 && collapsed.first() == ' ' && collapsed.last() == ' ' && collapsed.any { it != ' ' }) {
        return collapsed.substring(1, collapsed.length - 1)
    }
    return collapsed
}

private data class LinkTarget(
    val destination: String,
    val title: String?,
    val referenceLabel: String?,
)

private enum class ContainerKind {
    Root,
    Emphasis,
    Strong,
    Strikethrough,
    Link,
    Image,
}

/** Mutable sibling-linked node used while delimiters and brackets are still unresolved. */
private sealed class Draft {
    var parent: ContainerDraft? = null
    var previous: Draft? = null
    var next: Draft? = null

    fun unlink() {
        val previousSibling = previous
        val nextSibling = next
        if (previousSibling != null) previousSibling.next = nextSibling else parent?.first = nextSibling
        if (nextSibling != null) nextSibling.previous = previousSibling else parent?.last = previousSibling
        parent = null
        previous = null
        next = null
    }

    fun insertAfter(sibling: Draft) {
        sibling.unlink()
        val nextSibling = next
        sibling.parent = parent
        sibling.previous = this
        sibling.next = nextSibling
        if (nextSibling != null) nextSibling.previous = sibling else parent?.last = sibling
        next = sibling
    }
}

/** Text with its source span; delimiter runs shrink as emphasis consumes them. */
private class TextDraft(
    var start: Int,
    var end: Int,
    var literal: String,
) : Draft()

private class LeafDraft(val node: InlineNode) : Draft()

private class ContainerDraft(
    val kind: ContainerKind,
    val start: Int,
    val end: Int,
) : Draft() {
    var first: Draft? = null
    var last: Draft? = null
    var target: LinkTarget? = null

    fun append(child: Draft) {
        child.parent = this
        child.previous = last
        child.next = null
        val lastChild = last
        if (lastChild != null) lastChild.next = child else first = child
        last = child
    }
}

private class Delimiter(
    val char: Char,
    var count: Int,
    val originalCount: Int,
    val node: TextDraft,
    val canOpen: Boolean,
    val canClose: Boolean,
) {
    var previous: Delimiter? = null
    var next: Delimiter? = null
}

private class Bracket(
    val node: TextDraft,
    val previous: Bracket?,
    /** Nearest link (non-image) opener below this one; only link openers are ever deactivated. */
    val previousLink: Bracket?,
    val previousDelimiter: Delimiter?,
    val start: Int,
    val labelStart: Int,
    val image: Boolean,
) {
    var active: Boolean = true
    var bracketAfter: Boolean = false
}

/** Maximal backtick runs by length, consumed by forward-only cursors (openers are visited left to right). */
private class BacktickRunIndex(text: String) {
    private val startsByLength = HashMap<Int, MutableList<Int>>()
    private val cursorsByLength = HashMap<Int, Int>()

    init {
        var index = 0
        while (index < text.length) {
            if (text[index] != '`') {
                index += 1
                continue
            }
            val start = index
            while (index < text.length && text[index] == '`') {
                index += 1
            }
            startsByLength.getOrPut(index - start) { mutableListOf() } += start
        }
    }

    /** First run of exactly [length] backticks starting at or after [from], or -1. */
    fun nextRun(length: Int, from: Int): Int {
        val starts = startsByLength[length] ?: return -1
        var cursor = cursorsByLength[length] ?: 0
        while (cursor < starts.size && starts[cursor] < from) {
            cursor += 1
        }
        cursorsByLength[length] = cursor
        return if (cursor < starts.size) starts[cursor] else -1
    }
}

/** Finds the first position matching a position-local predicate, for monotonically increasing queries. */
private class ForwardFinder(
    private val length: Int,
    private val matches: (Int) -> Boolean,
) {
    private var scannedUntil = 0
    private var found = -1

    fun firstAtOrAfter(from: Int): Int {
        if (found >= from) {
            return found
        }
        var index = maxOf(from, scannedUntil)
        while (index < length) {
            if (matches(index)) {
                found = index
                scannedUntil = index + 1
                return index
            }
            index += 1
        }
        scannedUntil = length
        found = -1
        return -1
    }
}
