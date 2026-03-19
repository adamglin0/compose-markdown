package dev.markstream.sample.chat

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun MarkstreamSampleScreen() {
    val scripts = remember { loadPlatformSampleScripts() }
    SelectionContainer {
        MarkstreamSampleApp(scripts = scripts)
    }
}
