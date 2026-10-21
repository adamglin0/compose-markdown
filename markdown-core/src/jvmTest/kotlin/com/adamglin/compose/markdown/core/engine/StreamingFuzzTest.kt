package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

/**
 * Random documents assembled from block fragments, streamed char by char and in random chunks. A failure
 * is shrunk to a minimal document before it is reported.
 */
class StreamingFuzzTest {
    private val fragments = listOf(
        "- a\n", "- b\n", "  - c\n", "1. x\n", "2) y\n", "* z\n", "+ w\n", "- [ ] t\n",
        "> q\n", ">> qq\n", "> - lq\n", "para text\n", "more *em\n", "ph* end\n", "**s**\n",
        "```\n", "```js\n", "~~~\n", "code line\n", "  ```\n", "  indented\n", "    four\n",
        "| a | b |\n", "|---|---|\n", "a | b\n", "--- | ---\n", "| 1 | 2 |\n",
        "# h\n", "## h2 ##\n", "#tag\n", "Title\n", "===\n", "---\n", "***\n", "___\n",
        "[r]: /u\n", "see [r]\n", "[r]\n", "[r]: /v \"t\"\n", "$$\n", "x^2\n", "\\[\n", "\\]\n", "\$a\$ inline\n",
        "\n", "\n", "\n", "line  \n", "back\\\n", "\uD83D\uDE00 emoji\n", "<div>\n", "-\n", "#\n", "-1 deg\n",
        "*** x\n", "1.5 is\n", "-- dash\n", "```inline``` text\n", "> \n", "===x\n", "| no pipe end\n",
        "- item\n\n  ```\n  code\n  ```\n", "\r\n", "a\r", "\t- tab\n",
    ) + listOf(
        // Lines that probe the block parser's edge cases; each gets a line ending.
        "", "", "", "Foo", "bar *baz*", "lazy", "   three spaces", "===", "---", "--", "***", "* * *", "# Heading", "## h ##",
        "- a", "- b", "-", "* c", "+ d", "1. one", "2) two", "1.", "  - nested", "   1. deep", "    code", "\tcode", "      wide",
        "> quote", "> > inner", ">", "> - item", "> ```", ">\tcode?", "  > indented quote", "```", "```kt", "~~~", "````",
        "\$\$", "\$\$x\$\$", "a \$\$", "\$\$5", "\\[", "\\]", "\\[x\\]", "x\\]", "| a | b |", "|---|:-:|", "a | b", "--- | ---", "x\\|y | `a|b`",
        "[ref]: /url", "[ref]: /other \"t\"", "\"title\"", "[ref]:", "see [ref]", "- [ ] task", "- [x] done", "- [ ]", "  ```",
        "  code in item", "\t- tab item", "-\tfoo", "10. ten", "<div>", "a  ", "b\\",
        "  |a|b|", "    b|c", "2) b|c", "-|-", "[a]: /u '", "'", "  [ref]: /x", "> [ref]: /q", "- [ref]: /l", "|", "x|",
        "> > > - a", "1. - a", "- > a", "   ```", "~~~ ~", "  \$\$", "\$\$ ", "> \$\$", "- \$\$", "[a]", "[ref]", "  ", "\t\t", " - x", "[ref]: /o \"t\"",
    ).map { "$it\n" }

    /** Uses of a label inside containers and tables, with definitions arriving inside and after them. */
    private val definitionFragments = listOf(
        "- [r]\n", "> [r]\n", ">> [r]\n", "- > [r]\n", "  [r]\n", "| [r] |\n", "|---|\n", "- | [r] |\n", "  |---|\n",
        "[r]: /q\n", "- [r]: /q\n", "> [r]: /q\n", "[r]: /w \"t\"\n", "[r]:\n", "/x\n", "```\n", "  ```\n",
        "\n", "\n", ">\n", "-\n", "text [r]\n",
    )

    @Test
    fun randomDocumentsStreamLikeOneShotParse() = fuzz(fragments)

    @Test
    fun lateDefinitionsStreamLikeOneShotParse() = fuzz(definitionFragments)

    private fun fuzz(fragments: List<String>) {
        val failures = mutableListOf<String>()
        allDialects.forEach { dialect ->
            for (seed in 0 until SEEDS) {
                val random = Random(seed)
                val text = buildString { repeat(random.nextInt(2, 13)) { append(fragments[random.nextInt(fragments.size)]) } }
                    .let { if (random.nextBoolean()) it.trimEnd('\n') else it }
                val chunkings = listOf(charChunks(text), randomChunks(text, random, minSize = 1, maxSize = 12))
                if (chunkings.any { streamingViolation(dialect, it) != null }) {
                    val minimal = shrink(dialect, text)
                    failures += "$dialect '${minimal.escaped()}': ${streamingViolation(dialect, charChunks(minimal))}"
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail("${failures.size} streaming mismatches (shrunk, char by char):\n" + failures.distinct().take(10).joinToString("\n"))
        }
    }

    /** Drops lines, then characters, while char-by-char streaming still disagrees with the one-shot parse. */
    private fun shrink(dialect: MarkdownDialect, text: String): String {
        fun fails(candidate: String) = candidate.isNotEmpty() && streamingViolation(dialect, charChunks(candidate)) != null
        if (!fails(text)) {
            return text
        }
        var current = text
        var shrunk = true
        while (shrunk) {
            shrunk = false
            val lines = current.split('\n')
            for (index in lines.indices) {
                val candidate = lines.filterIndexed { other, _ -> other != index }.joinToString("\n")
                if (fails(candidate)) {
                    current = candidate
                    shrunk = true
                    break
                }
            }
            if (shrunk) {
                continue
            }
            for (index in current.indices) {
                val candidate = current.removeRange(index, index + 1)
                if (fails(candidate)) {
                    current = candidate
                    shrunk = true
                    break
                }
            }
        }
        return current
    }

    private companion object {
        const val SEEDS = 300
    }
}
