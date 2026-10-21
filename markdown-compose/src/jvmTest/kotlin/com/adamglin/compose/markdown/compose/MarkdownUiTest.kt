package com.adamglin.compose.markdown.compose

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MarkdownUiTest {
    @Test
    fun tableInsideBlockQuoteRenders() = runComposeUiTest {
        setContent {
            Markdown("> | a | b |\n> |---|---|\n> | 1 | 2 |", dialect = gfm)
        }
        onNodeWithText("a").assertExists()
        onNodeWithText("2").assertExists()
    }

    @Test
    fun newTextReplacesPreviousDocument() = runComposeUiTest {
        var text by mutableStateOf("first document")
        setContent { Markdown(text) }
        onNodeWithText("first document").assertExists()

        text = "second document"
        waitForIdle()

        onNodeWithText("second document").assertExists()
        onNodeWithText("first document").assertDoesNotExist()
    }

    @Test
    fun linksOpenThroughUriHandlerByDefault() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent {
            CompositionLocalProvider(LocalUriHandler provides RecordingUriHandler(opened)) {
                Markdown("see [docs](https://example.com/docs)")
            }
        }
        onNodeWithText("see docs").performFirstLinkClick()
        assertEquals(listOf("https://example.com/docs"), opened)
    }

    @Test
    fun customLinkHandlerReplacesUriHandler() = runComposeUiTest {
        val opened = mutableListOf<String>()
        val clicked = mutableListOf<String>()
        setContent {
            CompositionLocalProvider(LocalUriHandler provides RecordingUriHandler(opened)) {
                Markdown("see [docs](https://example.com/docs)", onLinkClick = { clicked += it })
            }
        }
        onNodeWithText("see docs").performFirstLinkClick()
        assertEquals(listOf("https://example.com/docs"), clicked)
        assertEquals(emptyList(), opened)
    }

    @Test
    fun orderedListsAreNumberedFromTheirStart() = runComposeUiTest {
        setContent { Markdown("1. one\n1. two\n1. three\n\nbreak\n\n7) seven\n7) eight") }
        listOf("1.", "2.", "3.", "7)", "8)").forEach { marker ->
            onNodeWithText(marker).assertExists()
        }
    }

    @Test
    fun headingsAndTaskItemsExposeSemantics() = runComposeUiTest {
        setContent { Markdown("# Title\n\n- [x] done\n- [ ] todo", dialect = gfm) }
        onNode(isHeading()).assertExists()
        onNode(checkbox(ToggleableState.On)).assertExists()
        onNode(checkbox(ToggleableState.Off)).assertExists()
    }

    @Test
    fun streamingStateUpdatesRenderedBlocks() = runComposeUiTest {
        val state = MarkdownState()
        setContent { Markdown(state.document) }

        state.append("Hello")
        waitForIdle()
        onNodeWithText("Hello").assertExists()

        state.append(" world\n\n```kotlin\nval x")
        waitForIdle()
        onNodeWithText("Hello world").assertExists()
        onNodeWithText("val x").assertExists()

        state.finish()
        state.reset()
        state.append("fresh")
        waitForIdle()
        onNodeWithText("fresh").assertExists()
        onNodeWithText("Hello world").assertDoesNotExist()
    }

    @Test
    fun lazyEntryRendersBlocksAsItems() = runComposeUiTest {
        setContent {
            val document = rememberMarkdownDocument("# Head\n\nbody text")
            LazyColumn {
                markdown(document, key = "doc")
            }
        }
        onNodeWithText("Head").assertIsDisplayed()
        onNodeWithText("body text").assertIsDisplayed()
    }

    @Test
    fun severalDocumentsShareOneLazyList() = runComposeUiTest {
        setContent {
            val first = rememberMarkdownDocument("first message")
            val second = rememberMarkdownDocument("second message")
            LazyColumn {
                markdown(first, key = 1)
                markdown(second, key = 2)
            }
        }
        onNodeWithText("first message").assertIsDisplayed()
        onNodeWithText("second message").assertIsDisplayed()
    }

    @Test
    fun streamingRecomposesOnlyTheTouchedBlock() = runComposeUiTest {
        val state = MarkdownState(MarkdownDialect.GfmMath)
        val renderer = CountingMathRenderer()
        setContent { Markdown(state.document, mathRenderer = renderer) }

        state.append("\$\$\nx\n\$\$\n\ntail")
        streamTail(state)
        assertEquals(1, renderer.blockCompositions)
    }

    @Test
    fun lazyStreamingWithFreshLambdasRecomposesOnlyTheTouchedBlock() = runComposeUiTest {
        val state = MarkdownState(MarkdownDialect.GfmMath)
        val renderer = CountingMathRenderer()
        setContent {
            val document = state.document
            LazyColumn {
                markdown(
                    document = document,
                    key = "doc",
                    mathRenderer = renderer,
                    // Capturing lambdas are fresh on every LazyListScope pass.
                    imageContent = { image -> BasicText("image ${image.url} in ${document.blocks.size} blocks") },
                    onLinkClick = { url -> println("$url in ${document.blocks.size} blocks") },
                )
            }
        }

        state.append("\$\$\nx\n\$\$\n\ntail")
        streamTail(state)
        assertEquals(1, renderer.blockCompositions)
    }

    @Test
    fun renderContextSurvivesFreshLambdas() = runComposeUiTest {
        // Lambdas built outside composition (as in a LazyListScope builder) are new instances.
        fun imageLambda(tag: Int): @Composable (MarkdownImage) -> Unit = { image -> BasicText("${image.url} $tag") }
        fun linkLambda(tag: Int): (String) -> Unit = { url -> println("$url $tag") }
        var imageContent by mutableStateOf(imageLambda(0))
        var onLinkClick by mutableStateOf(linkLambda(0))
        val contexts = mutableSetOf<MarkdownRenderContext>()
        setContent {
            contexts += rememberMarkdownRenderContext(DefaultCodeHighlighter, null, imageContent, onLinkClick)
        }
        for (tag in 1..3) {
            imageContent = imageLambda(tag)
            onLinkClick = linkLambda(tag)
            waitForIdle()
        }
        assertEquals(1, contexts.size)
    }

    private fun ComposeUiTest.streamTail(state: MarkdownState) {
        waitForIdle()
        repeat(5) {
            state.append(" more")
            waitForIdle()
        }
        onNodeWithText("tail more more more more more").assertExists()
    }

    @Test
    fun wideTableScrollsHorizontally() = runComposeUiTest {
        setContent {
            Markdown(
                "| a | b | c |\n|---|---|---|\n| ${"x".repeat(60)} | ${"y".repeat(60)} | last |",
                modifier = Modifier.width(160.dp),
                dialect = gfm,
            )
        }
        val cell = onNodeWithText("last")
        val before = cell.fetchSemanticsNode().positionInRoot.x
        onNode(hasScrollAction()).performTouchInput { swipeLeft() }
        waitForIdle()
        val after = cell.fetchSemanticsNode().positionInRoot.x
        assertTrue(after < before, "table did not scroll: $before -> $after")
    }

    @Test
    fun imagesShowAltTextOrCustomContent() = runComposeUiTest {
        var custom by mutableStateOf(false)
        setContent {
            Markdown(
                "![a cat](https://example.com/cat.png)",
                imageContent = if (custom) {
                    { image -> BasicText("image ${image.url}") }
                } else {
                    null
                },
            )
        }
        onNodeWithText("a cat").assertExists()
        custom = true
        waitForIdle()
        onNodeWithText("image https://example.com/cat.png").assertExists()
    }

    @Test
    fun mathRendererGetsOnlyClosedMath() = runComposeUiTest {
        val state = MarkdownState(MarkdownDialect.GfmMath)
        setContent { Markdown(state.document, mathRenderer = TextMathRenderer) }

        state.append("inline \$x^2\$ math\n\n\$\$\nE = mc^2")
        waitForIdle()
        onNodeWithText("[x^2]").assertIsDisplayed()
        onNodeWithText("block E = mc^2").assertDoesNotExist()
        onNodeWithText("E = mc^2").assertExists()

        state.append("\n\$\$\n")
        waitForIdle()
        onNodeWithText("block E = mc^2").assertExists()
    }
}

private val gfm = MarkdownDialect.GfmCompat

private object TextMathRenderer : MathRenderer {
    @Composable
    override fun BlockMath(latex: String, modifier: Modifier) {
        BasicText("block $latex", modifier)
    }

    @Composable
    override fun InlineMath(latex: String, style: TextStyle) {
        BasicText("[$latex]", style = style)
    }
}

/** Counts how often a block formula is composed. */
private class CountingMathRenderer : MathRenderer {
    var blockCompositions = 0

    @Composable
    override fun BlockMath(latex: String, modifier: Modifier) {
        SideEffect { blockCompositions++ }
        BasicText(latex, modifier)
    }

    @Composable
    override fun InlineMath(latex: String, style: TextStyle) = Unit
}

private fun checkbox(state: ToggleableState) =
    SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and
        SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state)

private class RecordingUriHandler(private val opened: MutableList<String>) : UriHandler {
    override fun openUri(uri: String) {
        opened += uri
    }
}
