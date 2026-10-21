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

    /** The whole section is out of scope by design. */
    data class Excluded(val reason: String) : SectionExpectation {
        override fun expectsPass(number: Int): Boolean = false
    }
}

private const val RAW_HTML = "Raw HTML is unsupported by design (no HTML blocks or inline HTML), so tags render as escaped text."

private val commonMarkExpectations: Map<String, SectionExpectation> = mapOf(
    // Every section is fully green apart from examples that need raw HTML.
    "Inlines" to SectionExpectation.AllExcept(),
    "Backslash escapes" to SectionExpectation.AllExcept(
        mapOf(21 to RAW_HTML),
    ),
    "Entity and numeric character references" to SectionExpectation.AllExcept(
        mapOf(31 to RAW_HTML),
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

    "Tabs" to SectionExpectation.AllExcept(),
    "Precedence" to SectionExpectation.AllExcept(),
    "Thematic breaks" to SectionExpectation.AllExcept(),
    "ATX headings" to SectionExpectation.AllExcept(),
    "Setext headings" to SectionExpectation.AllExcept(),
    "Indented code blocks" to SectionExpectation.AllExcept(),
    "Fenced code blocks" to SectionExpectation.AllExcept(),
    "HTML blocks" to SectionExpectation.Excluded(RAW_HTML),
    "Link reference definitions" to SectionExpectation.AllExcept(
        mapOf(201 to RAW_HTML),
    ),
    "Paragraphs" to SectionExpectation.AllExcept(),
    "Blank lines" to SectionExpectation.AllExcept(),
    "Block quotes" to SectionExpectation.AllExcept(),
    "List items" to SectionExpectation.AllExcept(),
    "Lists" to SectionExpectation.AllExcept(
        mapOf(308 to RAW_HTML, 309 to RAW_HTML),
    ),
)

private val gfmExpectations: Map<String, SectionExpectation> = mapOf(
    "Strikethrough (extension)" to SectionExpectation.AllExcept(),
    "Autolinks (extension)" to SectionExpectation.AllExcept(),
    "Tables (extension)" to SectionExpectation.AllExcept(),
)
