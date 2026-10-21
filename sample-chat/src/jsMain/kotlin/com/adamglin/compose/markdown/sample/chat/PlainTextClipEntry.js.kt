package com.adamglin.compose.markdown.sample.chat

import androidx.compose.ui.platform.ClipEntry

internal actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry.withPlainText(text)
