package com.adamglin.compose.markdown.sample.chat

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "compose-markdown - Markdown Preview Workbench",
        state = rememberWindowState(
            width = 1440.dp,
            height = 960.dp,
        ),
    ) {
        ComposeMarkdownSampleScreen()
    }
}
