package com.adamglin.compose.markdown.core.inline

import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Pathological inputs (findings 1 and 2). The previous recursive-descent parser was exponential on
 * nested brackets (n = 24 took 1.5 s) and quadratic on unmatched backticks, `\(`, `$` and `[` (80k chars
 * took ~350 ms); the input sizes here are far beyond what it could finish. The bound is deliberately
 * generous so that only super-linear behaviour, not a slow CI machine, fails these tests.
 */
class InlineParserPerformanceTest {
    @Test
    fun nestedBracketsAreLinear() {
        assertFast("[".repeat(20_000) + "a" + "]".repeat(20_000))
        assertFast("![".repeat(20_000) + "a" + "]".repeat(20_000))
        assertFast("[".repeat(20_000) + "a" + "](b)".repeat(20_000))
        assertFast("[a](b)".let { link -> "[".repeat(10_000) + link.repeat(10_000) })
        assertFast("[x " + "![a ".repeat(40_000) + "[l](u) ".repeat(40_000))
    }

    @Test
    fun unmatchedOpenersAreLinear() {
        assertFast("`a``".repeat(50_000))
        assertFast(buildString { for (length in 1..2_000) append("`".repeat(length % 97 + 1)).append('a') })
        assertFast("\\(a ".repeat(50_000))
        assertFast("\$a ".repeat(60_000))
        assertFast("[a".repeat(60_000))
        assertFast("[a](<b".repeat(30_000))
        assertFast("[a](b".repeat(30_000))
        assertFast("[a](b \"c".repeat(30_000))
        assertFast("[a][b".repeat(30_000))
    }

    @Test
    fun delimiterRunsAreLinear() {
        assertFast("*a _b ".repeat(40_000))
        assertFast("**a *b ".repeat(40_000))
        assertFast("~a ~~b ".repeat(40_000))
        assertFast("*".repeat(10_000) + "a" + "*".repeat(10_000))
        assertFast("a**b".repeat(50_000))
        assertFast("*a ".repeat(40_000) + "[a](b) ".repeat(40_000))
    }

    @Test
    fun autolinkScanningIsLinear() {
        assertFast("_www.a".repeat(50_000))
        assertFast("www.a_".repeat(50_000))
        assertFast("a.a.a@".repeat(50_000))
        assertFast("http://a.com/" + ")".repeat(100_000))
        assertFast("<a".repeat(60_000))
        assertFast("&#1".repeat(60_000))
    }

    private fun assertFast(input: String) {
        val dialect = MarkdownDialect.GfmMath
        val mark = TimeSource.Monotonic.markNow()
        parseInline(input, dialect = dialect)
        val elapsed = mark.elapsedNow()
        assertTrue(elapsed < 5.seconds, "Parsing ${input.length} chars (${input.take(16)}...) took $elapsed")
    }
}
