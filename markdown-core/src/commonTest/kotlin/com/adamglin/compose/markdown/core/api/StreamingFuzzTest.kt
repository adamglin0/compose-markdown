package com.adamglin.compose.markdown.core.api

import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.MarkdownSnapshot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Streams random block-heavy documents in random chunks and checks the incremental contract: every
 * intermediate snapshot equals a one-shot parse of the same prefix, the final snapshot equals a one-shot
 * parse of the whole document, and the stable prefix never moves backward nor changes the blocks it covers.
 */
class StreamingFuzzTest {
    @Test
    fun streamedSnapshotsMatchOneShotParses() {
        val random = Random(seed = SEED)
        repeat(DOCUMENT_COUNT) { documentIndex ->
            val document = randomDocument(random)
            val chunks = randomChunks(document, random)
            dialects.forEach { dialect ->
                checkStreaming(document, chunks, dialect, "document #$documentIndex")
            }
        }
    }

    private fun checkStreaming(document: String, chunks: List<String>, dialect: MarkdownDialect, label: String) {
        val context = "$label, ${dialect.id}: ${document.debug()} in chunks ${chunks.map { it.debug() }}"
        val engine = MarkdownEngine(dialect = dialect)
        val stableSnapshots = mutableListOf<MarkdownSnapshot>()
        var previousStableEnd = 0
        var prefix = ""
        chunks.forEach { chunk ->
            prefix += chunk
            val snapshot = engine.append(chunk).snapshot
            val oneShot = MarkdownEngine(dialect = dialect).apply { append(prefix) }.snapshot()
            assertEquals(oneShot.document.blocks.shape(), snapshot.document.blocks.shape(), "prefix ${prefix.debug()} of $context")
            assertEquals(oneShot.stablePrefixEnd, snapshot.stablePrefixEnd, "stable prefix of ${prefix.debug()} in $context")
            assertTrue(snapshot.stablePrefixEnd >= previousStableEnd, "stable prefix moved backward at ${prefix.debug()} in $context")
            previousStableEnd = snapshot.stablePrefixEnd
            stableSnapshots += snapshot
        }
        val final = engine.finish().snapshot
        val oneShotFinal = MarkdownEngine(dialect = dialect).apply { append(document) }.finish().snapshot
        assertEquals(oneShotFinal.document.blocks.shape(), final.document.blocks.shape(), "final of $context")

        // Inline content of stable blocks may still change when a later link reference definition resolves it.
        val finalBlocks = final.document.blocks.associateBy { it.range.start }
        stableSnapshots.forEach { snapshot ->
            snapshot.document.blocks
                .filter { it.range.endExclusive <= snapshot.stablePrefixEnd }
                .forEach { block ->
                    val finalBlock = finalBlocks[block.range.start]
                    assertEquals(block.blockShape(), finalBlock?.blockShape(), "stable block changed after ${snapshot.version} in $context")
                }
        }
    }

    private fun randomDocument(random: Random): String = buildString {
        repeat(random.nextInt(1, 30)) { index ->
            if (index > 0) {
                append(if (random.nextInt(12) == 0) "\r\n" else "\n")
            }
            append(lineFragments[random.nextInt(lineFragments.size)])
        }
        if (random.nextBoolean()) {
            append('\n')
        }
    }

    private fun randomChunks(document: String, random: Random): List<String> = buildList {
        var start = 0
        while (start < document.length) {
            val end = minOf(document.length, start + random.nextInt(1, 9))
            add(document.substring(start, end))
            start = end
        }
    }

    private fun String.debug(): String = replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")

    private companion object {
        // Larger counts and other seeds were run locally; this keeps the test fast on every target.
        const val DOCUMENT_COUNT = 300
        const val SEED = 20261021

        val dialects = listOf(
            MarkdownDialect.ChatFast,
            MarkdownDialect.CommonMarkCore,
            MarkdownDialect.GfmCompat,
            MarkdownDialect.GfmMath,
        )

        val lineFragments = listOf(
            "", "", "", "Foo", "bar *baz*", "lazy", "   three spaces", "===", "---", "--", "***", "* * *", "# Heading", "## h ##",
            "- a", "- b", "-", "* c", "+ d", "1. one", "2) two", "1.", "  - nested", "   1. deep", "    code", "\tcode", "      wide",
            "> quote", "> > inner", ">", "> - item", "> ```", ">\tcode?", "  > indented quote", "```", "```kt", "~~~", "````",
            "\$\$", "\$\$x\$\$", "a \$\$", "\$\$5", "\\[", "\\]", "\\[x\\]", "x\\]", "| a | b |", "|---|:-:|", "a | b", "--- | ---", "x\\|y | `a|b`",
            "[ref]: /url", "[ref]: /other \"t\"", "\"title\"", "[ref]:", "see [ref]", "- [ ] task", "- [x] done", "- [ ]", "  ```",
            "  code in item", "\t- tab item", "-\tfoo", "10. ten", "<div>", "a  ", "b\\",
            "  |a|b|", "    b|c", "2) b|c", "-|-", "[a]: /u '", "'", "  [ref]: /x", "> [ref]: /q", "- [ref]: /l", "|", "x|",
            "> > > - a", "1. - a", "- > a", "   ```", "~~~ ~", "  \$\$", "\$\$ ", "> \$\$", "- \$\$", "[a]", "[ref]", "  ", "\t\t", " - x", "[ref]: /o \"t\"",
        )
    }
}

private val idPattern = Regex("""(Block|Inline)Id\(raw=\d+\)""")

/** The full block tree without ids, which streamed and one-shot parses allocate differently. */
private fun List<BlockNode>.shape(): String = joinToString(separator = "\n") { it.toString().replace(idPattern, "") }

/** The block structure and ranges without inline content. */
private fun BlockNode.blockShape(): String = when (this) {
    is BlockNode.BlockQuote -> "quote$range[${children.joinToString { it.blockShape() }}]"
    is BlockNode.Document -> "document$range[${children.joinToString { it.blockShape() }}]"
    is BlockNode.ListBlock -> "list$range($type, loose=$isLoose)[${items.joinToString { it.blockShape() }}]"
    is BlockNode.ListItem -> "item$range($taskState)[${children.joinToString { it.blockShape() }}]"
    is BlockNode.TableBlock -> "table$range($alignments, rows=${rows.size})"
    is BlockNode.TableRow -> "row$range"
    is BlockNode.TableCell -> "cell$range"
    is BlockNode.Paragraph -> "paragraph$range"
    is BlockNode.Heading -> "heading$range($level)"
    is BlockNode.FencedCodeBlock -> "fence$range($infoString, $isClosed, $literal)"
    is BlockNode.IndentedCodeBlock -> "code$range($literal)"
    is BlockNode.MathBlock -> "math$range($latex, $isClosed)"
    is BlockNode.ThematicBreak -> "break$range"
}
