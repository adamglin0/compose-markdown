package com.adamglin.compose.markdown.compose

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalTestApi::class)
class MarkdownStateTest {
    @Test
    fun heldLinesShowOnceTheHoldTimeoutPasses() = runComposeUiTest {
        mainClock.autoAdvance = false
        lateinit var state: MarkdownState
        setContent {
            state = rememberMarkdownState(holdUnsettledLines = true, holdTimeout = 500.milliseconds)
            Markdown(state.document)
        }
        runOnIdle { state.append("| a |\n|---|\n| 1") }
        mainClock.advanceTimeBy(400)
        onNodeWithText("a").assertExists()
        onNodeWithText("1").assertDoesNotExist()

        // A settled line restarts the wait for the next held one.
        runOnIdle { state.append("2 |\n| 3") }
        mainClock.advanceTimeBy(400)
        onNodeWithText("12").assertExists()
        onNodeWithText("3").assertDoesNotExist()

        mainClock.advanceTimeBy(200)
        onNodeWithText("3").assertExists()
    }

    @Test
    fun heldAppendsDoNotRecompose() = runComposeUiTest {
        mainClock.autoAdvance = false
        lateinit var state: MarkdownState
        var compositions = 0
        setContent {
            state = rememberMarkdownState(holdUnsettledLines = true)
            SideEffect { compositions += 1 }
            Markdown(state.document)
        }
        runOnIdle { state.append("| a |\n|---|\n") }
        mainClock.advanceTimeByFrame()
        val before = compositions
        for (chunk in listOf("| 1", "23", " | 4", "56")) {
            runOnIdle { state.append(chunk) }
            mainClock.advanceTimeByFrame()
        }
        assertEquals(before, compositions)
    }

    @Test
    fun resetRestartsTheHoldTimeout() = runComposeUiTest {
        mainClock.autoAdvance = false
        lateinit var state: MarkdownState
        setContent {
            state = rememberMarkdownState(holdUnsettledLines = true, holdTimeout = 500.milliseconds)
            Markdown(state.document)
        }
        runOnIdle { state.append("| x") }
        mainClock.advanceTimeBy(400)
        // Held at the same offset as before the reset; the wait starts over.
        runOnIdle {
            state.reset()
            state.append("| y")
        }
        mainClock.advanceTimeBy(400)
        onNodeWithText("y", substring = true).assertDoesNotExist()
        mainClock.advanceTimeBy(200)
        onNodeWithText("y", substring = true).assertExists()
    }
}
