package com.adamglin.compose.markdown.sample.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adamglin.compose.markdown.compose.Markdown
import com.adamglin.compose.markdown.compose.MarkdownRendererState
import com.adamglin.compose.markdown.compose.rememberMarkdownRendererState
import com.adamglin.compose.markdown.compose.rememberMarkdownState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ComposeMarkdownSampleApp(
    scripts: List<SampleScript>,
) {
    ProvideSampleChatTheme {
        require(scripts.isNotEmpty())
        val colors = SampleChatTheme.colors
        val uriHandler = LocalUriHandler.current
        val scope = rememberCoroutineScope()
        val markdownState = rememberMarkdownState(
            engineFactory = SampleChatDefaults::createMarkdownEngine,
        )
        val rendererState = rememberMarkdownRendererState()

        var selectedScriptIndex by remember { mutableIntStateOf(0) }
        var chunkDelayMs by remember { mutableFloatStateOf(120f) }
        var isStreaming by remember { mutableStateOf(false) }
        var streamJob by remember { mutableStateOf<Job?>(null) }
        var lastLinkClick by remember { mutableStateOf("None") }
        var streamedChunkCount by remember { mutableIntStateOf(0) }
        var statusText by remember { mutableStateOf("Examples loaded. Click Start to replay the streaming flow.") }

        val selectedScript = scripts[selectedScriptIndex]

        fun renderSelectedScriptPreview() {
            stopStreaming(
                streamJob = streamJob,
                onStopped = {
                    streamJob = null
                    isStreaming = false
                },
            )
            markdownState.reset()
            rendererState.render(SampleChatDefaults.finalSnapshot(message = selectedScript.message))
            streamedChunkCount = 0
            statusText = "Switched to ${selectedScript.title}. Showing the full preview."
        }

        fun startStreaming() {
            stopStreaming(
                streamJob = streamJob,
                onStopped = {
                    streamJob = null
                    isStreaming = false
                },
            )

            markdownState.reset()
            rendererState.render(markdownState.snapshot)
            streamedChunkCount = 0
            statusText = "Replaying ${selectedScript.title} chunk by chunk."
            isStreaming = true
            streamJob = scope.launch {
                val chunks = SampleChatDefaults.createStreamingChunks(selectedScript.message)
                chunks.forEachIndexed { index, chunk ->
                    val delta = markdownState.append(chunk)
                    rendererState.apply(delta)
                    streamedChunkCount = index + 1
                    statusText = "${selectedScript.title}: appended ${index + 1}/${chunks.size} chunks."
                    delay(chunkDelayMs.toLong())
                }

                val finalDelta = markdownState.finish()
                rendererState.apply(finalDelta)
                streamedChunkCount = chunks.size
                statusText = "${selectedScript.title} replay complete."
                streamJob = null
                isStreaming = false
            }
        }

        fun stopCurrentStreaming() {
            stopStreaming(
                streamJob = streamJob,
                onStopped = {
                    streamJob = null
                    isStreaming = false
                    statusText = "Replay stopped. Keeping the rendered content on screen."
                },
            )
        }

        LaunchedEffect(selectedScriptIndex) {
            if (!isStreaming) {
                renderSelectedScriptPreview()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(colors.background, colors.backgroundShade),
                    ),
                )
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Sidebar(
                    scripts = scripts,
                    selectedScriptId = selectedScript.id,
                    selectionEnabled = !isStreaming,
                    onSelect = { index -> selectedScriptIndex = index },
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight(),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Toolbar(
                        chunkDelayMs = chunkDelayMs,
                        streamedChunkCount = streamedChunkCount,
                        statusText = statusText,
                        isStreaming = isStreaming,
                        onChunkDelayChanged = { chunkDelayMs = it },
                        onStart = ::startStreaming,
                        onStop = ::stopCurrentStreaming,
                        onReset = ::renderSelectedScriptPreview,
                    )

                    PreviewCanvas(
                        rendererState = rendererState,
                        lastLinkClick = lastLinkClick,
                        onLinkClick = { url ->
                            lastLinkClick = url
                            uriHandler.openUri(url)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    scripts: List<SampleScript>,
    selectedScriptId: String,
    selectionEnabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppPanel(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        backgroundColor = SampleChatTheme.colors.panelAlt.copy(alpha = 0.85f),
        borderColor = SampleChatTheme.colors.border,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppText(
                    text = "Markdown Examples",
                    style = SampleChatTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                AppText(
                    text = "Pick a scenario on the left to view the full preview or replay the streaming append.",
                    style = SampleChatTheme.typography.bodyMedium,
                    color = SampleChatTheme.colors.textSecondary,
                )
            }

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                scripts.forEachIndexed { index, script ->
                    val isSelected = script.id == selectedScriptId
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                color = if (isSelected) {
                                    SampleChatTheme.colors.panelInset
                                } else {
                                    SampleChatTheme.colors.panel.copy(alpha = 0.65f)
                                },
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    SampleChatTheme.colors.accent.copy(alpha = 0.55f)
                                } else {
                                    SampleChatTheme.colors.border.copy(alpha = 0.8f)
                                },
                                shape = RoundedCornerShape(18.dp),
                            )
                            .clickable(enabled = selectionEnabled) { onSelect(index) },
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AppText(
                                text = script.title,
                                style = SampleChatTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            )
                            AppText(
                                text = script.summary,
                                style = SampleChatTheme.typography.bodySmall,
                                color = SampleChatTheme.colors.textMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Toolbar(
    chunkDelayMs: Float,
    streamedChunkCount: Int,
    statusText: String,
    isStreaming: Boolean,
    onChunkDelayChanged: (Float) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
) {
    AppPanel(
        shape = RoundedCornerShape(24.dp),
        backgroundColor = SampleChatTheme.colors.panel,
        borderColor = SampleChatTheme.colors.border,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppText(
                        text = "Chunk playback speed ${chunkDelayMs.toInt()} ms",
                        style = SampleChatTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    AppSlider(
                        value = chunkDelayMs,
                        onValueChange = onChunkDelayChanged,
                        valueRange = 40f..320f,
                        enabled = !isStreaming,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppButton(text = "Start", onClick = onStart, enabled = !isStreaming)
                    AppButton(text = "Stop", onClick = onStop, enabled = isStreaming, filled = false)
                    AppButton(text = "Reset", onClick = onReset, enabled = !isStreaming, filled = false)
                }
            }

            AppText(
                text = "$statusText  Chunks sent: $streamedChunkCount",
                style = SampleChatTheme.typography.bodyMedium,
                color = SampleChatTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun PreviewCanvas(
    rendererState: MarkdownRendererState,
    lastLinkClick: String,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppPanel(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        backgroundColor = SampleChatTheme.colors.panel,
        borderColor = SampleChatTheme.colors.borderStrong,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = "Markdown Preview",
                    style = SampleChatTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                AppText(
                    text = "Last clicked link: $lastLinkClick",
                    style = SampleChatTheme.typography.bodySmall,
                    color = SampleChatTheme.colors.textMuted,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        color = SampleChatTheme.colors.panelInset.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(22.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = SampleChatTheme.colors.border.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(22.dp),
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 26.dp, vertical = 24.dp),
                ) {
                    Markdown(
                        state = rendererState,
                        modifier = Modifier.fillMaxWidth(),
                        onLinkClick = onLinkClick,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

private fun stopStreaming(
    streamJob: Job?,
    onStopped: () -> Unit,
) {
    streamJob?.cancel()
    onStopped()
}
