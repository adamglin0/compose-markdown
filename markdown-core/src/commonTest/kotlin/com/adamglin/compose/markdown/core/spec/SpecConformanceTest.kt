package com.adamglin.compose.markdown.core.spec

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import kotlin.test.Test
import kotlin.test.fail

/**
 * Runs the vendored CommonMark 0.31.2 and GFM extension spec examples through the engine and compares
 * the rendered HTML verbatim.
 *
 * Each section declares what is expected to pass. The check is a ratchet in both directions: an expected
 * example that fails is a regression, and an example that starts passing while still listed as failing
 * must be moved into the expected set, so the expectations always describe the current state exactly.
 */
class SpecConformanceTest {
    @Test
    fun commonMarkExamplesMatchSectionExpectations() {
        assertExpectations(commonMarkSpecExamples, MarkdownDialect.CommonMarkCore, commonMarkExpectations)
    }

    @Test
    fun gfmExtensionExamplesMatchSectionExpectations() {
        assertExpectations(gfmExtensionSpecExamples, MarkdownDialect.GfmCompat, gfmExpectations)
    }

    private fun assertExpectations(
        examples: List<SpecExample>,
        dialect: MarkdownDialect,
        expectations: Map<String, SectionExpectation>,
    ) {
        val problems = mutableListOf<String>()
        examples.groupBy { it.section }.forEach { (section, sectionExamples) ->
            val expectation = expectations[section]
            if (expectation == null) {
                problems += "Section '$section' has no expectation"
                return@forEach
            }
            if (expectation is SectionExpectation.Excluded) {
                return@forEach
            }
            sectionExamples.forEach { example ->
                val actual = render(example.markdown, dialect)
                val passes = actual == example.html
                val expectedToPass = expectation.expectsPass(example.number)
                if (expectedToPass && !passes) {
                    problems += "Example ${example.number} ($section) should pass.\n" +
                        "markdown: ${example.markdown.debug()}\nexpected: ${example.html.debug()}\nactual:   ${actual.debug()}"
                } else if (!expectedToPass && passes) {
                    problems += "Example ${example.number} ($section) now passes; add it to the section's expected set."
                }
            }
        }
        if (problems.isNotEmpty()) {
            fail("${problems.size} spec expectation mismatch(es):\n\n" + problems.joinToString(separator = "\n\n"))
        }
    }

    private fun render(markdown: String, dialect: MarkdownDialect): String = try {
        val engine = MarkdownEngine(dialect = dialect)
        engine.append(markdown)
        SpecHtmlRenderer.render(engine.finish().snapshot.document.blocks)
    } catch (error: Throwable) {
        "<exception: $error>"
    }

    private fun String.debug(): String = replace("\n", "\\n").replace("\t", "\\t")
}

internal sealed interface SectionExpectation {
    fun expectsPass(number: Int): Boolean

    /** Every example passes except the listed ones, each with the reason it cannot pass. */
    data class AllExcept(val exclusions: Map<Int, String> = emptyMap()) : SectionExpectation {
        override fun expectsPass(number: Int): Boolean = number !in exclusions
    }

    /** Exactly these examples pass today; block-level sections grow this set as the block parser improves. */
    data class Passing(val numbers: Set<Int>) : SectionExpectation {
        override fun expectsPass(number: Int): Boolean = number in numbers
    }

    /** The whole section is out of scope by design. */
    data class Excluded(val reason: String) : SectionExpectation {
        override fun expectsPass(number: Int): Boolean = false
    }
}

private const val RAW_HTML = "Raw HTML is unsupported by design (every preset has rawHtml = false), so tags render as escaped text."
private const val INDENTED_CODE = "Block level: indented code blocks are not produced by the block parser."
private const val LIST_TIGHTNESS = "Block level: list tightness / item structure is decided by the block parser."
private const val MULTILINE_DEFINITION = "Block level: link reference definitions spanning several lines are not recognized by the block parser."

private val commonMarkExpectations: Map<String, SectionExpectation> = mapOf(
    // Inline sections: fully green apart from raw HTML and block-level constructs used by the example.
    "Inlines" to SectionExpectation.AllExcept(),
    "Backslash escapes" to SectionExpectation.AllExcept(
        mapOf(18 to INDENTED_CODE, 21 to RAW_HTML),
    ),
    "Entity and numeric character references" to SectionExpectation.AllExcept(
        mapOf(31 to RAW_HTML, 36 to INDENTED_CODE, 38 to LIST_TIGHTNESS),
    ),
    "Code spans" to SectionExpectation.AllExcept(
        mapOf(344 to RAW_HTML),
    ),
    "Emphasis and strong emphasis" to SectionExpectation.AllExcept(
        mapOf(475 to RAW_HTML, 476 to RAW_HTML, 477 to RAW_HTML),
    ),
    "Links" to SectionExpectation.AllExcept(
        mapOf(
            491 to RAW_HTML,
            494 to RAW_HTML,
            524 to RAW_HTML,
            536 to RAW_HTML,
            541 to MULTILINE_DEFINITION,
        ),
    ),
    "Images" to SectionExpectation.AllExcept(),
    "Autolinks" to SectionExpectation.AllExcept(),
    "Raw HTML" to SectionExpectation.Excluded(RAW_HTML),
    "Hard line breaks" to SectionExpectation.AllExcept(
        mapOf(642 to RAW_HTML, 643 to RAW_HTML),
    ),
    "Soft line breaks" to SectionExpectation.AllExcept(),
    "Textual content" to SectionExpectation.AllExcept(),

    // Block sections: tracked pass sets.
    "Tabs" to SectionExpectation.Passing(setOf(11)),
    "Precedence" to SectionExpectation.Passing(emptySet()),
    "Thematic breaks" to SectionExpectation.Passing(setOf(43, 44, 45, 46, 47, 50, 52, 55, 58, 59)),
    "ATX headings" to SectionExpectation.Passing(setOf(62, 63, 64, 65, 66, 68, 72, 73, 74, 77, 78)),
    "Setext headings" to SectionExpectation.Passing(
        setOf(80, 81, 82, 84, 86, 88, 89, 90, 91, 92, 94, 95, 96, 97, 98, 99, 101, 102, 103, 104, 106),
    ),
    "Indented code blocks" to SectionExpectation.Passing(setOf(108, 113)),
    "Fenced code blocks" to SectionExpectation.Passing(
        setOf(119, 120, 121, 122, 123, 124, 125, 126, 129, 130, 135, 136, 138, 140, 141, 142, 143, 144, 145, 146, 147),
    ),
    "HTML blocks" to SectionExpectation.Excluded(RAW_HTML),
    "Link reference definitions" to SectionExpectation.Passing(
        setOf(192, 194, 197, 199, 200, 202, 203, 204, 205, 206, 207, 209, 210, 212, 213, 214, 215, 216, 218),
    ),
    "Paragraphs" to SectionExpectation.Passing(setOf(219, 220, 221, 223, 226)),
    "Blank lines" to SectionExpectation.Passing(setOf(227)),
    "Block quotes" to SectionExpectation.Passing(setOf(228, 229, 230, 234, 237, 239, 240, 241, 243, 244, 245, 246, 248, 249)),
    "List items" to SectionExpectation.Passing(setOf(256, 261, 262, 269, 285, 298, 299)),
    "Lists" to SectionExpectation.Passing(setOf(306, 314, 316, 317, 324, 326)),
)

private val gfmExpectations: Map<String, SectionExpectation> = mapOf(
    "Strikethrough (extension)" to SectionExpectation.AllExcept(),
    "Autolinks (extension)" to SectionExpectation.AllExcept(),
    "Tables (extension)" to SectionExpectation.Passing(setOf(198, 199, 201, 203, 205)),
)
