package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Streaming in LLM-token-sized chunks must cost time proportional to the mutable tail. Before the stable
 * prefix was made exact, a 200KB document took about 75 s in 4-10 character chunks (one-shot: 20 ms); it
 * now takes about 0.1 s. The bounds are generous so that only quadratic behaviour, not a slow CI machine,
 * fails these tests. Benchmarks with more scenarios live in the `benchmarks` module.
 */
class StreamingPerformanceTest {
    @Test
    fun mixedDocumentStreamsInLinearTime() {
        val section = "## Heading\n\nSome paragraph with *emphasis*, `code`, and a [link](http://x.y).\nSecond line.\n\n" +
            "- item one\n- item two\n  - nested\n\n> quote line\n\n```kotlin\nval x = 1\n```\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n"
        val document = buildString { while (length < 200_000) append(section) }
        assertStreamsWithin(document, 5.seconds)
    }

    @Test
    fun unclosedFenceStreamsWithoutCopyingTheDocument() {
        val document = "```\n" + buildString { while (length < 50_000) append("line of code here\n") }
        assertStreamsWithin(document, 5.seconds)
    }

    private fun assertStreamsWithin(document: String, limit: Duration) {
        val chunks = randomChunks(document, Random(7), minSize = 4, maxSize = 10)
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)
        val mark = TimeSource.Monotonic.markNow()
        chunks.forEach(engine::append)
        engine.finish()
        val elapsed = mark.elapsedNow()
        assertTrue(elapsed < limit, "streaming ${document.length} chars in ${chunks.size} chunks took $elapsed")
    }
}
