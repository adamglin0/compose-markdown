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
 * Streaming in LLM-token-sized chunks must cost time proportional to the new text, however large the
 * document or its open block. Before the block parser became resumable, each append re-parsed the open
 * top-level block from its start: 100KB in 4-10 character chunks took 2-13 s for one long list, paragraph,
 * block quote, table or fence (one-shot: a few ms); it now takes 20-40 ms. The bounds are generous so that
 * only quadratic behaviour, not a slow CI machine, fails these tests. Benchmarks with more scenarios live
 * in the `benchmarks` module.
 */
class StreamingPerformanceTest {
    @Test
    fun mixedDocumentStreamsInLinearTime() {
        val section = "## Heading\n\nSome paragraph with *emphasis*, `code`, and a [link](http://x.y).\nSecond line.\n\n" +
            "- item one\n- item two\n  - nested\n\n> quote line\n\n```kotlin\nval x = 1\n```\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n"
        assertStreamsWithin(repeatToLength(section, 200_000), 5.seconds)
    }

    @Test
    fun oneOpenBlockStreamsInLinearTime() {
        val blocks = mapOf(
            "fence" to "```\n" + repeatToLength("line of code here\n", OPEN_BLOCK_SIZE),
            "list" to repeatToLength("- list item with *some* words\n", OPEN_BLOCK_SIZE),
            "nested list" to "- top\n" + repeatToLength("  - nested item text\n", OPEN_BLOCK_SIZE),
            "paragraph" to repeatToLength("Some *words* and `code` in a [long](/u) paragraph line\n", OPEN_BLOCK_SIZE),
            "block quote" to repeatToLength("> quoted line of **text** here\n", OPEN_BLOCK_SIZE),
            "table" to "| a | b |\n|---|---|\n" + repeatToLength("| cell *one* | cell two |\n", OPEN_BLOCK_SIZE),
        )
        blocks.forEach { (name, document) -> assertStreamsWithin(document, 2.seconds, name) }
    }

    private fun repeatToLength(text: String, length: Int): String = buildString { while (this.length < length) append(text) }

    private fun assertStreamsWithin(document: String, limit: Duration, name: String = "document") {
        val chunks = randomChunks(document, Random(7), minSize = 4, maxSize = 10)
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)
        val mark = TimeSource.Monotonic.markNow()
        chunks.forEach(engine::append)
        engine.finish()
        val elapsed = mark.elapsedNow()
        assertTrue(elapsed < limit, "streaming a ${document.length} char $name in ${chunks.size} chunks took $elapsed")
    }

    private companion object {
        const val OPEN_BLOCK_SIZE = 100_000
    }
}
