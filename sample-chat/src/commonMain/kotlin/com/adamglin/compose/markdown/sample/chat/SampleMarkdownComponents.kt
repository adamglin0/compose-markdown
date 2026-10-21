package com.adamglin.compose.markdown.sample.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.adamglin.compose.markdown.compose.MarkdownCodeBlockScope
import com.adamglin.compose.markdown.compose.MarkdownComponents
import com.adamglin.compose.markdown.compose.MarkdownTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The sample's slots: code blocks get a header with their language and a copy button. */
internal val SampleMarkdownComponents = MarkdownComponents(
    codeBlock = { CodeBlockWithHeader(this) },
)

@Composable
private fun CodeBlockWithHeader(scope: MarkdownCodeBlockScope) {
    val colors = MarkdownTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface.copy(alpha = 0.55f))
            .border(1.dp, colors.border, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(scope.language ?: "text", style = MarkdownTheme.typography.label.copy(color = colors.textSecondary))
            // A streaming fence is still growing: offer to copy it once it is complete.
            if (scope.isClosed) CopyButton(scope.code)
        }
        scope.HighlightedCode(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp))
    }
}

@Composable
private fun CopyButton(code: String) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var copied by remember(code) { mutableStateOf(false) }
    if (copied) {
        LaunchedEffect(Unit) {
            delay(1_500)
            copied = false
        }
    }
    BasicText(
        text = if (copied) "Copied" else "Copy",
        style = MarkdownTheme.typography.label.copy(color = MarkdownTheme.colors.link),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                coroutineScope.launch {
                    clipboard.setClipEntry(plainTextClipEntry(code))
                    copied = true
                }
            }
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A clipboard entry holding [text]; building one is platform-specific. */
internal expect fun plainTextClipEntry(text: String): ClipEntry
