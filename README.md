# Compose Markdown

![Maven Central Version](https://img.shields.io/maven-central/v/com.adamglin.compose.markdown/markdown-core)
![License](https://img.shields.io/badge/license-MIT-111111)

This library is developing by AI.

> Kotlin Multiplatform Markdown parser and Compose Multiplatform renderer for append-only streaming output, such as LLM chat replies.

- CommonMark 0.31.2 and GFM tables, task lists, strikethrough and autolinks; every spec example passes except raw HTML, which is out of scope.
- Streaming is invisible: after every chunk the document equals a one-shot parse of the same text. Earlier lines are never parsed again, so a chunk costs about as much as its own length.
- Optional LaTeX math (`$…$`, `$$…$$`, `\(…\)`, `\[…\]`) rendered by a `MathRenderer` you plug in.
- Every element can be replaced or decorated through `MarkdownComponents` slots, such as a code block header with a copy button.
- Targets: Android, JVM desktop, iOS (arm64, simulator arm64), JS and Wasm.

## Install

```toml
[versions]
compose-markdown = "2.0.0"

[libraries]
compose-markdown = { module = "com.adamglin.compose.markdown:markdown-compose", version.ref = "compose-markdown" }
```

```kotlin
commonMain.dependencies {
    implementation(libs.compose.markdown)
}
```

`markdown-compose` brings in `markdown-core`. Depend on `markdown-core` alone to parse without Compose. The libraries are built against Compose Multiplatform 1.11.1, and the JVM artifact of `markdown-compose` needs Java 21.

## Render a string

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

`Markdown(text)` parses once per distinct text and dialect. Wrap it in a `SelectionContainer` when text should be selectable.

## Stream chunks

`MarkdownState` owns the parser. Each `append` publishes the new document right away, and only the blocks the chunk touched recompose.

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

// Long documents: one lazy item per top-level block, keyed by `key` plus the block's stable id.
@Composable
fun LongReply(chunks: Flow<String>) {
    val state = rememberMarkdownState()
    LaunchedEffect(chunks) {
        state.reset()
        chunks.collect { state.append(it) }
        state.finish()
    }
    LazyColumn {
        markdown(state.document, key = "reply")
    }
}
```

`MarkdownState` is plain observable state, so it can also live in a view model.

Pass `holdUnsettledLines = true` to `rememberMarkdownState` or `MarkdownState` to keep the rendered structure from jumping while streaming. A table row appears once its line ends, a line that is still only a marker such as `-` or `#` appears once it has content, and a line starting with `|` waits for the next line, so a table header never shows as a paragraph first. A held line never waits longer than `holdTimeout` (500 ms by default); after that it shows as it is. `rememberMarkdownState` runs that timeout; for a `MarkdownState` in a view model, run `state.releaseHeldLinesOnTimeout()` in the view model's scope.

## Dialects

Pass a `MarkdownDialect` to `Markdown(text, dialect = …)` or `rememberMarkdownState(dialect)`.

| Dialect | Use it for |
| --- | --- |
| `ChatFast` (default) | Chat output: GFM without link reference definitions, so a streamed `[label]: url` line never rewrites links that were already shown. |
| `CommonMarkCore` | Plain CommonMark, with no GFM extensions. |
| `GfmCompat` | README-style content: CommonMark plus GFM tables, task lists, strikethrough and bare URL autolinks. |
| `GfmMath` | `GfmCompat` plus LaTeX math. |

Details are in [docs/dialects.md](docs/dialects.md).

## Math

`GfmMath` parses math into `BlockNode.MathBlock` and `InlineNode.MathSpan`. The library does not include a LaTeX engine. Implement `MathRenderer` with the LaTeX library you prefer and pass one long-lived instance:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.adamglin.compose.markdown.compose.Markdown
import com.adamglin.compose.markdown.compose.MathRenderer
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect

object MyMathRenderer : MathRenderer {
    @Composable
    override fun BlockMath(latex: String, modifier: Modifier) {
        // A display formula: `$$…$$` or `\[…\]`.
    }

    @Composable
    override fun InlineMath(latex: String, style: TextStyle) {
        // An inline formula: `$…$` or `\(…\)`. It is drawn at its natural size, in the surrounding text style.
    }
}

@Composable
fun MathMessage(content: String) {
    Markdown(content, dialect = MarkdownDialect.GfmMath, mathRenderer = MyMathRenderer)
}
```

Without a renderer, math shows its LaTeX source. A math block that has not been closed yet also shows its source and is never passed to the renderer. `sample-chat` contains renderers built on `io.github.darriousliu:ratex` and `io.github.huarangmeng:latex-renderer`.

## Customization

- `MarkdownTheme(colors, typography, blockSpacing)` styles every renderer inside it. Without it, the renderer uses `MarkdownColors.Light`, `MarkdownTypography.Default` and 12.dp block spacing.
- `components = MarkdownComponents(...)` replaces or decorates how elements render (see below).
- Links open through `LocalUriHandler`. Pass `onLinkClick` to handle them yourself.
- `codeHighlighter` colors closed fenced code blocks. It defaults to `DefaultCodeHighlighter`, which is built on `dev.snipme:highlights`; pass `null` to turn highlighting off.
- Images show their alt text. Set `MarkdownComponents(image = { image -> ... })` to load them with your own image library.
- `onLinkClick` and `MarkdownComponents` can be new on every call. A slot lambda that changes re-runs for every element of its kind on screen, so hoist or `remember` slots that capture changing values. `codeHighlighter` and `mathRenderer` should be long-lived instances, such as objects or values you `remember`.

### Components

`MarkdownComponents` has one slot per element: `paragraph`, `heading`, `codeBlock`, `mathBlock`, `blockQuote`, `list`, `listItem`, `listMarker`, `taskCheckbox`, `table`, `thematicBreak` and `image`. A slot runs with a scope as receiver. The scope holds the element's data (`block`, the parsed node, or the fields of a code block or list marker) and `DefaultContent()`, the built-in rendering, so a slot can wrap the default or replace it. `blockQuote` and `listItem` also get `Children()`, which renders the nested blocks with their own slots. Slots you leave out keep the built-in rendering, and `copy(...)` derives a variant of a shared set, such as `ChatComponents.copy(image = { ... })`.

A code block with a header that shows the language and a copy button, around the built-in highlighted code:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adamglin.compose.markdown.compose.Markdown
import com.adamglin.compose.markdown.compose.MarkdownComponents

val ChatComponents = MarkdownComponents(
    codeBlock = {
        Column {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                BasicText(language ?: "text")
                // `isClosed` is false while the fence is still streaming.
                if (isClosed) BasicText("Copy", Modifier.clickable { copyToClipboard(code) })
            }
            HighlightedCode(contentPadding = PaddingValues(14.dp))
        }
    },
    heading = {
        Column {
            DefaultContent()
            if (block.level <= 2) Divider()
        }
    },
)

@Composable
fun Message(content: String) {
    Markdown(content, components = ChatComponents)
}
```

`copyToClipboard` and `Divider` stand for your own code. `sample-chat` has a working version in `SampleMarkdownComponents.kt`. Each slot should emit one layout node; wrap several in a `Column` or `Box`, as above. A replaced slot drops the default's accessibility semantics, such as `heading()` on headings, unless you set them yourself. `LazyListScope.markdown` takes the same `components`.

## Parse without Compose

```kotlin
import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect

fun main() {
    val engine = MarkdownEngine(MarkdownDialect.GfmCompat)
    engine.append("# Title\n\nSome *streamed")
    val delta = engine.append(" text*\n")
    println(delta.changedBlocks) // the top-level blocks this chunk added or changed
    val document = engine.finish().snapshot.document
    println(document.blocks)
}
```

## Not supported

- Raw HTML. It stays literal text.
- Footnotes and other GFM extensions beyond tables, task lists, strikethrough and autolinks.
- Edits in the middle of the source. The engine only appends.

The full list is in [docs/known-limitations.md](docs/known-limitations.md).

## Documentation

- [API](docs/api.md): public types and their guarantees
- [Dialects](docs/dialects.md): which syntax each dialect enables
- [Compatibility report](docs/compatibility-report.md): spec conformance and deviations
- [Known limitations](docs/known-limitations.md)
- [Architecture](docs/architecture.md): how the parser, engine and renderer fit together
- [Performance notes](docs/performance-notes.md): benchmark method and results
- [Publishing](docs/maven-central-publishing.md): releases, versioning and the ABI check

## License

MIT
