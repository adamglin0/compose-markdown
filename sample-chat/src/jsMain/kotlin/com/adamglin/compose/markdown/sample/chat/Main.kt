package com.adamglin.compose.markdown.sample.chat

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        ComposeMarkdownSampleScreen()
    }
}
