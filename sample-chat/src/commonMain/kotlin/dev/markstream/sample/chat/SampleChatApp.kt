package dev.markstream.sample.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.markstream.compose.Markdown
import dev.markstream.compose.rememberMarkdownState

@Composable
fun MarkstreamSampleApp() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            var input by remember { mutableStateOf(SampleChatDefaults.initialMessage) }
            val markdownState = rememberMarkdownState()

            LaunchedEffect(input) {
                markdownState.reset()
                if (input.isNotEmpty()) {
                    markdownState.append(input)
                }
                markdownState.finish()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = "markstream sample-chat",
                    style = MaterialTheme.typography.headlineMedium,
                )

                    Text(
                        text = "Desktop placeholder app for the Stage 2 checkpoint. Edit the text below to exercise append(), finish(), and snapshot-driven preview updates.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Source input",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                            )
                            .padding(16.dp),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Rendered preview",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Markdown(
                        snapshot = markdownState.snapshot,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
