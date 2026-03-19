package com.adamglin.compose.markdown.sample.chat

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun ComposeMarkdownSampleScreen() {
    val scripts = remember { loadPlatformSampleScripts() }
    SelectionContainer {
        ComposeMarkdownSampleApp(scripts = scripts)
    }
}
