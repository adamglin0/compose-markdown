# Compose Markdown

![Maven Central Version](https://img.shields.io/maven-central/v/com.adamglin.compose.markdown/markdown-core)
![License](https://img.shields.io/badge/license-MIT-111111)

> Kotlin Multiplatform Markdown parser and Compose renderer for append-only streaming output.
>
> This library is under development.

## Install

> Publishing is not ready yet. The coordinates below are planned for the first public release.

**libs.versions.toml**

```toml
[versions]
compose-markdown = "latest-version"

[libraries]
compose-markdown-core = { module = "com.adamglin.compose.markdown:markdown-core", version.ref = "compose-markdown" }
compose-markdown-compose = { module = "com.adamglin.compose.markdown:markdown-compose", version.ref = "compose-markdown" }
```

**build.gradle.kts**

```kotlin
implementation(libs.compose.markdown.compose)
```

## Example

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.adamglin.compose.markdown.compose.Markdown
import com.adamglin.compose.markdown.core.api.MarkdownEngine

@Composable
fun Message(content: String) {
    val engine = remember { MarkdownEngine() }
    engine.reset()
    val snapshot = engine.append(content).snapshot
    Markdown(snapshot = snapshot)
}
```

## Modules

- `markdown-core` - incremental Markdown engine with streaming-friendly snapshots
- `markdown-compose` - Compose renderer built around stable block identity

## Notes

- presets: `ChatFast`, `CommonMarkCore`, `GfmCompat`
- good fit: chat UIs, streaming previews, Compose apps
- out of scope: raw HTML, arbitrary in-place editing, full CommonMark parity
- docs: `docs/compatibility-report.md`, `docs/known-limitations.md`

## License

MIT
