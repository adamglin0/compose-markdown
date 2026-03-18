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
import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.InlineNode
import dev.markstream.core.model.MarkdownSnapshot

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
                    text = "No blocks yet. Stage 2 currently renders the latest placeholder snapshot.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                snapshot.document.blocks.forEach { block ->
                    Text(
                        text = block.renderPreviewText(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
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

private fun BlockNode.renderPreviewText(): String = when (this) {
    is BlockNode.BlockQuote -> children.joinToString(separator = "\n") { it.renderPreviewText() }
    is BlockNode.Document -> children.joinToString(separator = "\n") { it.renderPreviewText() }
    is BlockNode.FencedCodeBlock -> literal
    is BlockNode.Heading -> children.renderInlineText()
    is BlockNode.ListBlock -> items.joinToString(separator = "\n") { item -> "${item.marker} ${item.children.joinToString(" ") { it.renderPreviewText() }}" }
    is BlockNode.ListItem -> children.joinToString(separator = " ") { it.renderPreviewText() }
    is BlockNode.Paragraph -> children.renderInlineText()
    is BlockNode.RawTextBlock -> literal
    is BlockNode.ThematicBreak -> marker
    is BlockNode.UnsupportedBlock -> literal
}

private fun List<InlineNode>.renderInlineText(): String = joinToString(separator = "") { node ->
    when (node) {
        is InlineNode.CodeSpan -> node.literal
        is InlineNode.Emphasis -> node.children.renderInlineText()
        is InlineNode.HardBreak -> "\n"
        is InlineNode.Link -> node.children.renderInlineText()
        is InlineNode.SoftBreak -> "\n"
        is InlineNode.Strikethrough -> node.children.renderInlineText()
        is InlineNode.Strong -> node.children.renderInlineText()
        is InlineNode.Text -> node.literal
        is InlineNode.UnsupportedInline -> node.literal
    }
}
