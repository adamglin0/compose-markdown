package com.adamglin.compose.markdown.core.inline

import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.internal.LinkReferenceDefinition
import com.adamglin.compose.markdown.core.model.TextRange
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * A parse resumed from the checkpoint of a shorter literal must equal a parse from the start, whatever
 * text the longer literal adds: constructs that may still pair with later text block the checkpoint.
 */
class InlineCheckpointTest {
    private val definitions = mapOf(
        "ref" to LinkReferenceDefinition(label = "ref", destination = "/r", title = null, range = TextRange(0, 0)),
    )

    private val fragments = listOf(
        "word ", "*", "**", "_", "__", "~", "~~", "`", "``", "\$", "\\(", "\\)", "[", "]", "![", "](", "(/u)", ")",
        "[ref]", "[ref][]", "<", ">", "<http://a.b>", "www.x.com ", "a@b.co ", "\\", "&amp;", "  ", "\n", "\n", "\n",
        " \"t\"", "x", "y ", "*a*", "`c`", "\$m\$",
    )

    @Test
    fun resumedParsesMatchFullParses() {
        val failures = mutableListOf<String>()
        listOf(MarkdownDialect.GfmMath, MarkdownDialect.CommonMarkCore).forEach { dialect ->
            val parser = InlineParser(dialect = dialect, referenceDefinitions = definitions)
            repeat(800) { seed ->
                val random = Random(seed)
                val text = buildString { repeat(random.nextInt(4, 40)) { append(fragments[random.nextInt(fragments.size)]) } }
                    .trim('\n', ' ')
                    .replace(Regex("\n[ \n]*"), "\n")
                if (text.isEmpty()) {
                    return@repeat
                }
                val range = TextRange(start = 7, endExclusive = 7 + text.length)
                val full = parser.parse(literal = text, range = range)
                for (cut in 1 until text.length) {
                    val checkpoint = parser.parse(literal = text.substring(0, cut), range = TextRange(7, 7 + cut)).checkpoint ?: continue
                    val resumed = parser.parse(literal = text, range = range, resume = checkpoint)
                    if (resumed.nodes != full.nodes || resumed.referenceLabels != full.referenceLabels) {
                        failures += "${dialect.id} '${text.replace("\n", "\\n")}' resumed at ${checkpoint.offset} (prefix of $cut)"
                        break
                    }
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail("${failures.size} resumed parses differ:\n" + failures.take(10).joinToString("\n"))
        }
    }

    @Test
    fun checkpointsSitAtLineStartsWithNothingPending() {
        val parser = InlineParser(dialect = MarkdownDialect.GfmCompat, referenceDefinitions = emptyMap())
        fun checkpointOf(text: String) = parser.parse(literal = text, range = TextRange(0, text.length)).checkpoint?.offset

        assertEquals(10, checkpointOf("*a* and b\nc"))
        assertNull(checkpointOf("single line"))
        // An opener that may still find its closer, an open bracket and an unclosed code span all block it.
        assertNull(checkpointOf("*a and b\nc"))
        assertNull(checkpointOf("[a and b\nc"))
        assertNull(checkpointOf("`a and b\nc"))
        assertEquals(10, assertNotNull(checkpointOf("`a` and b\nc")))
    }
}
