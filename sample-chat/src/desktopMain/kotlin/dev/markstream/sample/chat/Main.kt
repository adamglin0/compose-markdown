package dev.markstream.sample.chat

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "markstream",
    ) {
        MarkstreamSampleApp()
    }
}
