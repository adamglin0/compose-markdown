package dev.markstream.sample.chat

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.markstream.compose.Markdown
import dev.markstream.compose.rememberMarkdownRendererState
import dev.markstream.compose.rememberMarkdownState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MarkstreamSampleApp(
    scripts: List<SampleScript>,
) {
    require(scripts.isNotEmpty())

    val colorScheme = lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF2E5B88),
        secondary = androidx.compose.ui.graphics.Color(0xFF607D94),
        background = androidx.compose.ui.graphics.Color(0xFFF3F5F8),
        surface = androidx.compose.ui.graphics.Color(0xFFFBFCFE),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE8EDF3),
        outline = androidx.compose.ui.graphics.Color(0xFFBBC6D2),
        outlineVariant = androidx.compose.ui.graphics.Color(0xFFD4DBE3),
    )

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
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
            var lastLinkClick by remember { mutableStateOf("暂无") }
            var streamedChunkCount by remember { mutableIntStateOf(0) }
            var statusText by remember { mutableStateOf("已载入示例，点击开始可重播流式过程。") }

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
                statusText = "已切换到 ${selectedScript.title}，当前展示完整预览。"
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
                statusText = "正在按 chunk 回放 ${selectedScript.title}。"
                isStreaming = true
                streamJob = scope.launch {
                    val chunks = SampleChatDefaults.createStreamingChunks(selectedScript.message)
                    chunks.forEachIndexed { index, chunk ->
                        val delta = markdownState.append(chunk)
                        rendererState.apply(delta)
                        streamedChunkCount = index + 1
                        statusText = "${selectedScript.title} 已追加 ${index + 1}/${chunks.size} 个 chunk。"
                        delay(chunkDelayMs.toLong())
                    }

                    val finalDelta = markdownState.finish()
                    rendererState.apply(finalDelta)
                    streamedChunkCount = chunks.size
                    statusText = "${selectedScript.title} 回放完成。"
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
                        statusText = "已停止回放，当前保留已渲染内容。"
                    },
                )
            }

            LaunchedEffect(selectedScriptIndex) {
                if (!isStreaming) {
                    renderSelectedScriptPreview()
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Markdown 示例",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "选择左侧场景，右侧查看完整预览或重播流式 append。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                scripts.forEachIndexed { index, script ->
                    val isSelected = script.id == selectedScriptId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = selectionEnabled) { onSelect(index) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                            },
                        ),
                        shape = RoundedCornerShape(18.dp),
                        border = if (isSelected) {
                            androidx.compose.foundation.BorderStroke(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            )
                        } else {
                            null
                        },
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = script.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            )
                            Text(
                                text = script.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
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
                    Text(
                        text = "Chunk 模拟速度 ${chunkDelayMs.toInt()} ms",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Slider(
                        value = chunkDelayMs,
                        onValueChange = onChunkDelayChanged,
                        valueRange = 40f..320f,
                        enabled = !isStreaming,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onStart, enabled = !isStreaming) {
                        Text("开始")
                    }
                    OutlinedButton(onClick = onStop, enabled = isStreaming) {
                        Text("停止")
                    }
                    OutlinedButton(onClick = onReset, enabled = !isStreaming) {
                        Text("重置")
                    }
                }
            }

            Text(
                text = "$statusText  已发送 chunk: $streamedChunkCount",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewCanvas(
    rendererState: dev.markstream.compose.MarkdownRendererState,
    lastLinkClick: String,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
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
                Text(
                    text = "Markdown 预览",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "最后点击链接: $lastLinkClick",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(22.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
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
