package com.adamglin.compose.markdown.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.Composer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Streaming one token into a long document must cost the changed blocks, not the document: in
 * [MarkdownState] and in composition. Prints the measured per-append cost.
 */
@OptIn(ExperimentalTestApi::class, InternalComposeTracingApi::class)
class MarkdownStreamingCostTest {
    @Test
    fun stateAppendCostDoesNotGrowWithDocumentSize() {
        val small = stateAppendNanos(documentOfSize(25_000))
        val large = stateAppendNanos(documentOfSize(200_000))
        println("MarkdownState per append: 25KB ${small / 1000} us, 200KB ${large / 1000} us")
        // Eight times the blocks; an O(document) append would be about eight times slower.
        assertTrue(large < small * 3 + 20_000, "per-append cost grew with the document: $small ns -> $large ns")
    }

    @Test
    fun streamingAWholeDocumentCostsTheSamePerAppendAtAnySize() {
        val small = streamNanosPerAppend(documentOfSize(25_000))
        val large = streamNanosPerAppend(documentOfSize(200_000))
        println("MarkdownState streams a whole document: 25KB ${small / 1000} us, 200KB ${large / 1000} us per append")
        assertTrue(large < small * 3 + 5_000, "per-append cost of a whole stream grew with the document: $small ns -> $large ns")
    }

    @Test
    fun columnRecomposesOnlyTheTail() = documentSizes.forEach { size ->
        runComposeUiTest {
            val state = MarkdownState(dialect)
            val math = CountingMathRenderer()
            setContent { Markdown(state.document, mathRenderer = math) }
            state.append(documentOfSize(size))
            waitForIdle()
            assertTailOnlyRecomposes(state, math, label = "Markdown(document)")
        }
    }

    @Test
    fun lazyListRecomposesOnlyTheTail() = documentSizes.forEach { size ->
        runComposeUiTest {
            val state = MarkdownState(dialect)
            val math = CountingMathRenderer()
            val listState = LazyListState()
            setContent {
                Box(Modifier.width(400.dp).height(800.dp)) {
                    LazyColumn(state = listState) {
                        markdown(state.document, key = "doc", mathRenderer = math)
                        // Room below the last block, so the streamed tail is visible once scrolled to it.
                        item(key = "end") { Spacer(Modifier.height(800.dp)) }
                    }
                }
            }
            state.append(documentOfSize(size))
            waitForIdle()
            runOnIdle { listState.requestScrollToItem(state.document.blocks.lastIndex) }
            waitForIdle()
            assertTailOnlyRecomposes(state, math, label = "LazyListScope.markdown")
        }
    }

    /**
     * Streams a tail into the document [state] holds, token by token, and checks that every append
     * composes the changed tail block (at most the two last top-level blocks) and never a closed formula.
     */
    private fun ComposeUiTest.assertTailOnlyRecomposes(state: MarkdownState, math: CountingMathRenderer, label: String) {
        val blocks = state.document.blocks.size
        val formulasBefore = math.blockCompositions
        val tail = "\n\nThe tail paragraph grows token by token, with *emphasis* and `code` spans.\n\nA second one starts here and keeps going."
        val counter = BlockCounter()
        var worst = 0
        var fewest = Int.MAX_VALUE
        var nanos = 0L
        val chunks = tail.chunked(TOKEN)
        withTracer(counter) {
            chunks.forEach { chunk ->
                counter.blocks = 0
                val start = TimeSource.Monotonic.markNow()
                state.append(chunk)
                waitForIdle()
                nanos += start.elapsedNow().inWholeNanoseconds
                worst = maxOf(worst, counter.blocks)
                fewest = minOf(fewest, counter.blocks)
            }
        }
        println("$label, $blocks blocks: at most $worst blocks composed per append, ${nanos / chunks.size / 1000} us per append")
        assertEquals(formulasBefore, math.blockCompositions, "closed formulas recomposed")
        onNodeWithText("A second one starts here and keeps going.").assertExists()
        // Every chunk changes the tail paragraph, which must compose; nothing else may.
        assertTrue(fewest >= 1, "an append composed no block")
        assertTrue(worst <= 2, "an append composed $worst blocks")
    }

    /** Mean cost of a token-sized append into [text] followed by a streamed tail paragraph. */
    private fun stateAppendNanos(text: String): Long {
        val tail = "Streaming tail paragraph with *emphasis*, `code` and plain words. ".repeat(40).chunked(TOKEN)
        return fastestOf {
            val state = MarkdownState(dialect)
            state.append(text)
            val start = TimeSource.Monotonic.markNow()
            tail.forEach(state::append)
            start.elapsedNow().inWholeNanoseconds / tail.size
        }
    }

    /** Mean cost of an append while streaming all of [text] in token-sized chunks. */
    private fun streamNanosPerAppend(text: String): Long {
        val chunks = text.chunked(TOKEN)
        return fastestOf {
            val state = MarkdownState(dialect)
            val start = TimeSource.Monotonic.markNow()
            chunks.forEach(state::append)
            state.finish()
            start.elapsedNow().inWholeNanoseconds / chunks.size
        }
    }

    private inline fun fastestOf(run: () -> Long): Long {
        repeat(3) { run() }
        return (1..5).minOf { run() }
    }

    private class BlockCounter : CompositionTracer {
        var blocks = 0

        override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
            if (info.startsWith("com.adamglin.compose.markdown.compose.MarkdownBlock ")) {
                blocks += 1
            }
        }

        override fun traceEventEnd() = Unit

        override fun isTraceInProgress(): Boolean = true
    }

    private inline fun withTracer(tracer: CompositionTracer, action: () -> Unit) {
        Composer.setTracer(tracer)
        try {
            action()
        } finally {
            Composer.setTracer(null)
        }
    }

    private companion object {
        val dialect = MarkdownDialect.GfmMath
        val documentSizes = listOf(25_000, 200_000)

        /** Characters per append, about one LLM token. */
        const val TOKEN = 4

        /** A chat-like document of about [size] characters: headings, paragraphs, lists, formulas, quotes. */
        fun documentOfSize(size: Int): String = buildString {
            var section = 0
            while (length < size) {
                append("## Section $section\n\n")
                append("Paragraph $section with *emphasis*, `code` and a [link](https://example.com/$section).\n\n")
                append("- first item\n- second item\n\n")
                append("$$\nx_{$section}\n$$\n\n")
                append("> quoted $section\n\n")
                section += 1
            }
        }
    }
}
