package com.adamglin.compose.markdown.compose

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberPlatformMarkdownCodeHighlighter(
    palette: MarkdownCodeHighlightPalette,
): CodeHighlighter = PlainCodeHighlighter
