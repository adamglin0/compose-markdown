package com.adamglin.compose.markdown.sample.chat

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainer = checkNotNull(document.body)) {
        ComposeMarkdownSampleScreen()
    }
}
