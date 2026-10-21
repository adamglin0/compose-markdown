# Compose Markdown

![Maven Central Version](https://img.shields.io/maven-central/v/com.adamglin.compose.markdown/markdown-core)
![License](https://img.shields.io/badge/license-MIT-111111)

This library is developing by AI.

> Kotlin Multiplatform Markdown parser and Compose renderer for append-only streaming output.
>

## Install

**libs.versions.toml**

```toml
[versions]
compose-markdown = "0.1.5"

[libraries]
compose-markdown-core = { module = "com.adamglin.compose.markdown:markdown-core", version.ref = "compose-markdown" }
compose-markdown-compose = { module = "com.adamglin.compose.markdown:markdown-compose", version.ref = "compose-markdown" }
```

**build.gradle.kts**

```kotlin
implementation(libs.compose.markdown.compose)
```

## Example

Render a complete string (parsed once per distinct text):

```kotlin
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import com.adamglin.compose.markdown.compose.Markdown
import com.adamglin.compose.markdown.compose.MarkdownColors
import com.adamglin.compose.markdown.compose.MarkdownTheme

@Composable
fun Message(content: String) {
    MarkdownTheme(colors = if (isSystemInDarkTheme()) MarkdownColors.Dark else MarkdownColors.Light) {
        SelectionContainer {
            Markdown(content)
        }
    }
}
```

Stream chunks as they arrive. `MarkdownState` owns the parser and updates only the blocks a chunk touched:

```kotlin
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.adamglin.compose.markdown.compose.Markdown
import com.adamglin.compose.markdown.compose.markdown
import com.adamglin.compose.markdown.compose.rememberMarkdownState
import kotlinx.coroutines.flow.Flow

@Composable
fun StreamingReply(chunks: Flow<String>) {
    val state = rememberMarkdownState()
    LaunchedEffect(chunks) {
        state.reset()
        chunks.collect { state.append(it) }
        state.finish()
    }
    Markdown(state.document)
}

// Long documents: one lazy item per top-level block, keyed by stable block id.
@Composable
fun LongReply(chunks: Flow<String>) {
    val state = rememberMarkdownState()
    LaunchedEffect(chunks) {
        state.reset()
        chunks.collect { state.append(it) }
        state.finish()
    }
    LazyColumn {
        markdown(state.document)
    }
}
```

Links open through `LocalUriHandler`; pass `onLinkClick` to handle them yourself. Optional slots: `codeHighlighter` (`null` disables highlighting), `mathRenderer` (LaTeX, e.g. with the `GfmMath` dialect), and `imageContent` (render images with your own loader; alt text by default).

## Modules

- `markdown-core` - incremental Markdown engine with streaming-friendly snapshots
- `markdown-compose` - Compose renderer built around stable block identity

## Notes

- presets: `ChatFast`, `CommonMarkCore`, `GfmCompat`, `GfmMath`
- good fit: chat UIs, streaming previews, Compose apps
- CommonMark 0.31.2 and GFM table conformance apart from raw HTML
- out of scope: raw HTML, arbitrary in-place editing
- docs: `docs/compatibility-report.md`, `docs/known-limitations.md`

## License

MIT
