package dev.markstream.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.markstream.core.model.MarkdownSnapshot
import dev.markstream.core.model.PlainTextBlock

@Composable
fun Markdown(
    snapshot: MarkdownSnapshot,
    modifier: Modifier = Modifier,
) = MarkdownSnapshotView(
    snapshot = snapshot,
    modifier = modifier,
)

@Composable
fun MarkdownSnapshotView(
    snapshot: MarkdownSnapshot,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (snapshot.isFinal) {
                    "Placeholder markdown snapshot"
                } else {
                    "Placeholder markdown preview"
                },
                style = MaterialTheme.typography.titleSmall,
            )

            if (snapshot.document.blocks.isEmpty()) {
                Text(
                    text = "No blocks yet. Stage 1 renders plain text as a single placeholder block.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                snapshot.document.blocks.forEach { block ->
                    when (block) {
                        is PlainTextBlock -> {
                            Text(
                                text = block.text,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            Text(
                text = "version=${snapshot.version} stablePrefixEnd=${snapshot.stablePrefixEnd} dirty=${snapshot.dirtyRegion.start}..${snapshot.dirtyRegion.endExclusive}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
