package com.adamglin.compose.markdown.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.ListType
import com.adamglin.compose.markdown.core.model.TaskState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MarkdownComponentsTest {
    @Test
    fun paragraphSlot() = runComposeUiTest {
        assertReplaceAndDecorate("plain words", "paragraph 1", hasText("plain words")) { mode ->
            MarkdownComponents(paragraph = { Slot(mode, "paragraph ${block.children.size}") { DefaultContent() } })
        }
    }

    @Test
    fun headingSlot() = runComposeUiTest {
        assertReplaceAndDecorate("## Title", "heading 2", hasText("Title")) { mode ->
            MarkdownComponents(heading = { Slot(mode, "heading ${block.level}") { DefaultContent() } })
        }
    }

    @Test
    fun codeBlockSlot() = runComposeUiTest {
        assertReplaceAndDecorate("```kotlin title=x\nval x = 1\n```", "code kotlin [kotlin title=x] true 9", hasText("val x = 1")) { mode ->
            MarkdownComponents(
                codeBlock = { Slot(mode, "code $language [$infoString] $isClosed ${code.length}") { DefaultContent() } },
            )
        }
    }

    @Test
    fun indentedCodeUsesTheCodeBlockSlot() = runComposeUiTest {
        setContent {
            Markdown(
                "para\n\n    val y = 2",
                dialect = MarkdownDialect.CommonMarkCore,
                components = MarkdownComponents(codeBlock = { BasicText("indented $language $infoString $isClosed $code") }),
            )
        }
        onNodeWithText("indented null null true val y = 2").assertExists()
    }

    @Test
    fun mathBlockSlot() = runComposeUiTest {
        assertReplaceAndDecorate("$$\nE = mc^2\n$$", "math true", hasText("E = mc^2")) { mode ->
            MarkdownComponents(mathBlock = { Slot(mode, "math ${block.isClosed}") { DefaultContent() } })
        }
    }

    @Test
    fun blockQuoteSlot() = runComposeUiTest {
        assertReplaceAndDecorate("> quoted words", "quote 1", hasText("quoted words")) { mode ->
            MarkdownComponents(blockQuote = { Slot(mode, "quote ${block.children.size}") { DefaultContent() } })
        }
    }

    @Test
    fun listSlot() = runComposeUiTest {
        assertReplaceAndDecorate("1. one\n2. two", "list 2", hasText("two")) { mode ->
            MarkdownComponents(list = { Slot(mode, "list ${block.items.size}") { DefaultContent() } })
        }
    }

    @Test
    fun listItemSlot() = runComposeUiTest {
        assertReplaceAndDecorate("- only item", "item 1", hasText("only item")) { mode ->
            MarkdownComponents(listItem = { Slot(mode, "item ${block.children.size}") { DefaultContent() } })
        }
    }

    @Test
    fun listMarkerSlot() = runComposeUiTest {
        assertReplaceAndDecorate("7. seven", "marker 7 at 0", hasText("7.")) { mode ->
            MarkdownComponents(
                listMarker = {
                    val start = (listType as ListType.Ordered).startNumber
                    Slot(mode, "marker ${start + index} at $depth") { DefaultContent() }
                },
            )
        }
    }

    @Test
    fun bulletMarkersReportTheirDepth() = runComposeUiTest {
        setContent {
            Markdown(
                "- outer\n  - inner",
                components = MarkdownComponents(listMarker = { BasicText("${listType::class.simpleName} $index/$depth") }),
            )
        }
        onNodeWithText("Bullet 0/0").assertExists()
        onNodeWithText("Bullet 0/1").assertExists()
    }

    @Test
    fun taskCheckboxSlot() = runComposeUiTest {
        assertReplaceAndDecorate("- [x] done", "task true", checkbox) { mode ->
            MarkdownComponents(
                taskCheckbox = { Slot(mode, "task ${block.taskState == TaskState.Checked}") { DefaultContent() } },
            )
        }
    }

    @Test
    fun tableSlot() = runComposeUiTest {
        assertReplaceAndDecorate("| a | b |\n|---|---|\n| 1 | 2 |", "table 2x1", hasText("2")) { mode ->
            MarkdownComponents(table = { Slot(mode, "table ${block.header.cells.size}x${block.rows.size}") { DefaultContent() } })
        }
    }

    @Test
    fun thematicBreakSlot() = runComposeUiTest {
        // The default rule has no semantics; the decorated case checks it through the tagged box.
        assertReplaceAndDecorate("---", "break ---", defaultContent = null) { mode ->
            MarkdownComponents(thematicBreak = { Slot(mode, "break ${block.marker}") { DefaultContent() } })
        }
    }

    @Test
    fun customCodeBlockWrapsTheDefaultCodeWithACopyButton() = runComposeUiTest {
        val copied = mutableListOf<String>()
        val components = MarkdownComponents(
            codeBlock = {
                Column {
                    Row {
                        BasicText(language ?: "text")
                        if (isClosed) BasicText("Copy", Modifier.clickable { copied += code })
                    }
                    HighlightedCode()
                }
            },
        )
        val state = MarkdownState(MarkdownDialect.GfmCompat)
        setContent { Markdown(state.document, components = components) }

        state.append("```python\nprint(1)")
        waitForIdle()
        onNodeWithText("python").assertExists()
        onNodeWithText("print(1)").assertExists()
        // Streaming fences are open: no copy button yet.
        onNodeWithText("Copy").assertDoesNotExist()

        state.append("\n```\n")
        waitForIdle()
        onNodeWithText("Copy").performClick()
        assertEquals(listOf("print(1)"), copied)
        // The default label and container are replaced, the default code text is kept.
        onNodeWithText("print(1)").assertExists()
    }

    @Test
    fun customContainersRenderNestedChildrenWithTheirSlots() = runComposeUiTest {
        val components = MarkdownComponents(
            blockQuote = { Row { BasicText("|"); Children() } },
            listItem = { Column { BasicText("item of ${block.children.size}"); Children() } },
            codeBlock = { BasicText("code: $code") },
        )
        setContent {
            Markdown("> - first\n>\n>   ```\n>   nested\n>   ```\n> - second", dialect = MarkdownDialect.GfmCompat, components = components)
        }
        onNodeWithText("|").assertExists()
        onNodeWithText("item of 2").assertExists()
        onNodeWithText("item of 1").assertExists()
        onNodeWithText("first").assertExists()
        onNodeWithText("second").assertExists()
        // The quote's default surface is replaced, the nested code block still goes through its slot.
        onNodeWithText("code: nested").assertExists()
    }

    @Test
    fun lazyEntryUsesComponents() = runComposeUiTest {
        setContent {
            val document = rememberMarkdownDocument("# Head\n\n---")
            LazyColumn {
                markdown(
                    document,
                    key = "doc",
                    components = MarkdownComponents(
                        heading = { Column { BasicText("anchor ${block.level}"); DefaultContent() } },
                        thematicBreak = { BasicText("custom rule") },
                    ),
                )
            }
        }
        onNodeWithText("anchor 1").assertExists()
        onNodeWithText("Head").assertExists()
        onNodeWithText("custom rule").assertExists()
    }

    @Test
    fun componentsWithTheSameSlotsAreEqual() {
        assertEquals(MarkdownComponents(), MarkdownComponents())
        val image: @Composable (MarkdownImage) -> Unit = { BasicText(it.url) }
        assertEquals(MarkdownComponents(image = image), MarkdownComponents(image = image))
        assertNotEquals(MarkdownComponents(), MarkdownComponents(image = image))
    }

    private enum class Mode { Replace, Decorate }

    /** Shows [label]; in [Mode.Decorate] also the default content, in a box tagged [DEFAULT_TAG]. */
    @Composable
    private fun Slot(mode: Mode, label: String, default: @Composable () -> Unit) {
        Column {
            BasicText(label)
            if (mode == Mode.Decorate) Box(Modifier.testTag(DEFAULT_TAG)) { default() }
        }
    }

    /**
     * Renders [markdown] with the components [components] builds for each mode: replacing shows
     * [label] instead of [defaultContent], decorating shows both.
     */
    private fun ComposeUiTest.assertReplaceAndDecorate(
        markdown: String,
        label: String,
        defaultContent: SemanticsMatcher?,
        components: (Mode) -> MarkdownComponents,
    ) {
        var mode by mutableStateOf(Mode.Replace)
        setContent { Markdown(markdown, dialect = MarkdownDialect.GfmMath, components = components(mode)) }
        onNodeWithText(label).assertExists()
        onNodeWithTag(DEFAULT_TAG).assertDoesNotExist()
        if (defaultContent != null) onNode(defaultContent).assertDoesNotExist()

        mode = Mode.Decorate
        waitForIdle()
        onNodeWithText(label).assertExists()
        assertTrue(onNodeWithTag(DEFAULT_TAG).fetchSemanticsNode().size.height > 0, "default content is empty")
        if (defaultContent != null) onNode(defaultContent).assertExists()
    }

    private companion object {
        const val DEFAULT_TAG = "default"
        val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
    }
}
