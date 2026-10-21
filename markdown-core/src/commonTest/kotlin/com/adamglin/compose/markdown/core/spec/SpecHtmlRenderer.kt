package com.adamglin.compose.markdown.core.spec

import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.InlineNode
import com.adamglin.compose.markdown.core.model.ListType
import com.adamglin.compose.markdown.core.model.TableAlignment

internal data class SpecExample(
    val number: Int,
    val section: String,
    val markdown: String,
    val html: String,
)

/**
 * Test-only HTML renderer that mirrors the output format of the reference implementations
 * (commonmark.js / cmark-gfm), so spec examples can be compared verbatim.
 */
internal object SpecHtmlRenderer {
    fun render(blocks: List<BlockNode>): String {
        val out = HtmlOutput()
        blocks.forEach { out.block(it, tight = false) }
        return out.toString()
    }

    private class HtmlOutput {
        private val builder = StringBuilder()

        override fun toString(): String = builder.toString()

        private fun cr() {
            if (builder.isNotEmpty() && builder.last() != '\n') {
                builder.append('\n')
            }
        }

        fun block(block: BlockNode, tight: Boolean) {
            when (block) {
                is BlockNode.Document -> block.children.forEach { block(it, tight = false) }
                is BlockNode.Paragraph -> if (tight) {
                    inlines(block.children)
                } else {
                    cr()
                    builder.append("<p>")
                    inlines(block.children)
                    builder.append("</p>")
                    cr()
                }

                is BlockNode.Heading -> {
                    cr()
                    builder.append("<h${block.level}>")
                    inlines(block.children)
                    builder.append("</h${block.level}>")
                    cr()
                }

                is BlockNode.FencedCodeBlock -> {
                    cr()
                    val language = block.infoString?.trim()?.split(' ', '\t')?.firstOrNull()?.takeIf { it.isNotEmpty() }
                    builder.append(if (language == null) "<pre><code>" else "<pre><code class=\"language-${escape(language)}\">")
                    builder.append(escape(block.literal))
                    if (block.literal.isNotEmpty()) {
                        builder.append('\n')
                    }
                    builder.append("</code></pre>")
                    cr()
                }

                is BlockNode.IndentedCodeBlock -> {
                    cr()
                    builder.append("<pre><code>").append(escape(block.literal)).append("\n</code></pre>")
                    cr()
                }

                is BlockNode.BlockQuote -> {
                    cr()
                    builder.append("<blockquote>")
                    cr()
                    block.children.forEach { block(it, tight = false) }
                    cr()
                    builder.append("</blockquote>")
                    cr()
                }

                is BlockNode.ListBlock -> {
                    cr()
                    val type = block.type
                    builder.append(
                        when {
                            type !is ListType.Ordered -> "<ul>"
                            type.startNumber == 1 -> "<ol>"
                            else -> "<ol start=\"${type.startNumber}\">"
                        },
                    )
                    cr()
                    block.items.forEach { block(it, tight = !block.isLoose) }
                    cr()
                    builder.append(if (block.type is ListType.Ordered) "</ol>" else "</ul>")
                    cr()
                }

                is BlockNode.ListItem -> {
                    builder.append("<li>")
                    block.children.forEach { block(it, tight = tight) }
                    builder.append("</li>")
                    cr()
                }

                is BlockNode.ThematicBreak -> {
                    cr()
                    builder.append("<hr />")
                    cr()
                }

                is BlockNode.TableBlock -> {
                    cr()
                    builder.append("<table>\n<thead>\n")
                    tableRow(block.header, block.alignments, cellTag = "th")
                    builder.append("</thead>\n")
                    if (block.rows.isNotEmpty()) {
                        builder.append("<tbody>\n")
                        block.rows.forEach { tableRow(it, block.alignments, cellTag = "td") }
                        builder.append("</tbody>\n")
                    }
                    builder.append("</table>")
                    cr()
                }

                is BlockNode.TableRow -> tableRow(block, emptyList(), cellTag = "td")
                is BlockNode.TableCell -> inlines(block.children)
                is BlockNode.MathBlock -> {
                    cr()
                    builder.append("<div class=\"math\">").append(escape(block.latex)).append("</div>")
                    cr()
                }

            }
        }

        private fun tableRow(row: BlockNode.TableRow, alignments: List<TableAlignment>, cellTag: String) {
            builder.append("<tr>\n")
            row.cells.forEachIndexed { index, cell ->
                val align = when (alignments.getOrNull(index)) {
                    TableAlignment.Left -> " align=\"left\""
                    TableAlignment.Center -> " align=\"center\""
                    TableAlignment.Right -> " align=\"right\""
                    TableAlignment.Default, null -> ""
                }
                builder.append("<$cellTag$align>")
                inlines(cell.children)
                builder.append("</$cellTag>\n")
            }
            builder.append("</tr>\n")
        }

        private fun inlines(nodes: List<InlineNode>) {
            nodes.forEach { inline(it) }
        }

        private fun inline(node: InlineNode) {
            when (node) {
                is InlineNode.Text -> builder.append(escape(node.literal))
                is InlineNode.SoftBreak -> builder.append('\n')
                is InlineNode.HardBreak -> builder.append("<br />\n")
                is InlineNode.CodeSpan -> builder.append("<code>").append(escape(node.literal)).append("</code>")
                is InlineNode.Emphasis -> wrap("em", node.children)
                is InlineNode.Strong -> wrap("strong", node.children)
                is InlineNode.Strikethrough -> wrap("del", node.children)
                is InlineNode.Link -> {
                    builder.append("<a href=\"").append(escape(encodeUrl(node.destination))).append('"')
                    if (!node.title.isNullOrEmpty()) {
                        builder.append(" title=\"").append(escape(node.title)).append('"')
                    }
                    builder.append('>')
                    inlines(node.children)
                    builder.append("</a>")
                }

                is InlineNode.Image -> {
                    builder.append("<img src=\"").append(escape(encodeUrl(node.destination)))
                    builder.append("\" alt=\"").append(escape(plainText(node.alt))).append('"')
                    if (!node.title.isNullOrEmpty()) {
                        builder.append(" title=\"").append(escape(node.title)).append('"')
                    }
                    builder.append(" />")
                }

                is InlineNode.MathSpan -> builder.append("<span class=\"math\">").append(escape(node.latex)).append("</span>")
            }
        }

        private fun wrap(tag: String, children: List<InlineNode>) {
            builder.append("<$tag>")
            inlines(children)
            builder.append("</$tag>")
        }
    }

    private fun plainText(nodes: List<InlineNode>): String = buildString {
        nodes.forEach { node ->
            when (node) {
                is InlineNode.Text -> append(node.literal)
                is InlineNode.SoftBreak -> append('\n')
                is InlineNode.HardBreak -> append('\n')
                is InlineNode.CodeSpan -> append(node.literal)
                is InlineNode.Emphasis -> append(plainText(node.children))
                is InlineNode.Strong -> append(plainText(node.children))
                is InlineNode.Strikethrough -> append(plainText(node.children))
                is InlineNode.Link -> append(plainText(node.children))
                is InlineNode.Image -> append(plainText(node.alt))
                is InlineNode.MathSpan -> append(node.latex)
            }
        }
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(char)
            }
        }
    }

    /** mdurl `encode` as used by commonmark.js: percent-encodes everything but URL-safe ASCII and existing escapes. */
    private fun encodeUrl(url: String): String = buildString {
        var index = 0
        while (index < url.length) {
            val char = url[index]
            when {
                char == '%' && index + 2 < url.length && url[index + 1].isHex() && url[index + 2].isHex() -> {
                    append(url, index, index + 3)
                    index += 3
                    continue
                }

                char.code < 0x80 && (char.isLetterOrDigit() || char in URL_SAFE) -> append(char)
                else -> {
                    val end = if (char.isHighSurrogate() && index + 1 < url.length) index + 2 else index + 1
                    url.substring(index, end).encodeToByteArray().forEach { byte ->
                        append('%')
                        append(HEX[(byte.toInt() shr 4) and 0xF])
                        append(HEX[byte.toInt() and 0xF])
                    }
                    index = end
                    continue
                }
            }
            index += 1
        }
    }

    private fun Char.isHex(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private const val URL_SAFE = ";/?:@&=+$,-_.!~*'()#"
    private const val HEX = "0123456789ABCDEF"
}
