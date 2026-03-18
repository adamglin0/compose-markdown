package dev.markstream.sample.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.markstream.compose.Markdown
import dev.markstream.compose.rememberMarkdownRendererState
import dev.markstream.compose.rememberMarkdownState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MarkstreamSampleApp() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val uriHandler = LocalUriHandler.current
            val scope = rememberCoroutineScope()
            val markdownState = rememberMarkdownState()
            val rendererState = rememberMarkdownRendererState(snapshot = markdownState.snapshot)

            var selectedScriptIndex by remember { mutableIntStateOf(0) }
            var chunkDelayMs by remember { mutableFloatStateOf(140f) }
            var isStreaming by remember { mutableStateOf(false) }
            var streamJob by remember { mutableStateOf<Job?>(null) }
            var lastDeltaDebug by remember { mutableStateOf("(尚未开始 streaming)") }
            var lastLinkClick by remember { mutableStateOf("(暂无)") }
            var streamedChunkCount by remember { mutableIntStateOf(0) }

            fun stopStreaming() {
                streamJob?.cancel()
                streamJob = null
                isStreaming = false
            }

            fun resetStreaming() {
                stopStreaming()
                markdownState.reset()
                rendererState.render(markdownState.snapshot)
                lastDeltaDebug = "(已重置，等待开始)"
                streamedChunkCount = 0
            }

            fun startStreaming() {
                val script = SampleChatDefaults.scripts[selectedScriptIndex]
                stopStreaming()
                markdownState.reset()
                rendererState.render(markdownState.snapshot)
                lastDeltaDebug = "script=${script.title}\n准备开始 streaming"
                streamedChunkCount = 0
                isStreaming = true
                streamJob = scope.launch {
                    SampleChatDefaults.createStreamingChunks(script.message).forEachIndexed { index, chunk ->
                        val delta = markdownState.append(chunk)
                        rendererState.apply(delta)
                        streamedChunkCount = index + 1
                        lastDeltaDebug = buildString {
                            appendLine("script=${script.title}")
                            appendLine("chunk=${index + 1} size=${chunk.length}")
                            append(delta.toDebugText())
                        }
                        delay(chunkDelayMs.toLong())
                    }

                    val finalDelta = markdownState.finish()
                    rendererState.apply(finalDelta)
                    lastDeltaDebug = buildString {
                        appendLine("script=${script.title}")
                        appendLine("stream complete")
                        append(finalDelta.toDebugText())
                    }
                    isStreaming = false
                    streamJob = null
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "markstream sample-chat",
                    style = MaterialTheme.typography.headlineMedium,
                )

                Text(
                    text = "阶段 6 streaming demo：按 chunk append，按 block keyed 渲染，只替换变更 block。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                CardSection(title = "示例脚本") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SampleChatDefaults.scripts.forEachIndexed { index, script ->
                            val isSelected = index == selectedScriptIndex
                            val button: @Composable (() -> Unit) = {
                                Text(
                                    text = script.title,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            if (isSelected) {
                                Button(
                                    onClick = { selectedScriptIndex = index },
                                    enabled = !isStreaming,
                                ) { button() }
                            } else {
                                OutlinedButton(
                                    onClick = { selectedScriptIndex = index },
                                    enabled = !isStreaming,
                                ) { button() }
                            }
                        }
                    }

                    Text(
                        text = SampleChatDefaults.scripts[selectedScriptIndex].message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            .padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                CardSection(title = "控制") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(onClick = ::startStreaming, enabled = !isStreaming) {
                            Text("开始")
                        }
                        OutlinedButton(onClick = ::stopStreaming, enabled = isStreaming) {
                            Text("停止")
                        }
                        OutlinedButton(onClick = ::resetStreaming) {
                            Text("重置")
                        }
                    }

                    Text(
                        text = "chunk 间隔: ${chunkDelayMs.toInt()} ms  |  已发送 chunk: $streamedChunkCount",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = chunkDelayMs,
                        onValueChange = { chunkDelayMs = it },
                        valueRange = 60f..400f,
                        enabled = !isStreaming,
                    )
                }

                CardSection(title = "Markdown 渲染") {
                    Markdown(
                        state = rendererState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(18.dp),
                            )
                            .padding(16.dp),
                        onLinkClick = { url ->
                            lastLinkClick = url
                            uriHandler.openUri(url)
                        },
                    )
                }

                CardSection(title = "调试信息") {
                    DebugText(label = "Last delta", text = lastDeltaDebug)
                    DebugText(label = "Block snapshot", text = markdownState.snapshot.toDebugText())
                    DebugText(label = "Last link click", text = lastLinkClick)
                }
            }
        }
    }
}

@Composable
private fun CardSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            content()
        },
    )
}

@Composable
private fun DebugText(
    label: String,
    text: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
