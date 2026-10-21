# Public API

Both published modules compile in Kotlin explicit API mode. The public surface is recorded in the ABI dumps under `markdown-core/api/` and `markdown-compose/api/`, and `check` fails when the code no longer matches the dumps (see [maven-central-publishing.md](maven-central-publishing.md#abi-validation)). Parser state, caches and id allocation are internal.

## Core (`markdown-core`)

### Engine

```kotlin
package com.adamglin.compose.markdown.core.api

public interface MarkdownEngine {
    public val dialect: MarkdownDialect

    public fun append(chunk: String): ParseDelta
    public fun finish(): ParseDelta
    public fun snapshot(): MarkdownSnapshot
    public fun reset()
}

public fun MarkdownEngine(dialect: MarkdownDialect = MarkdownDialect.ChatFast): MarkdownEngine
```

- `append(chunk)` adds source text and returns what changed. Line endings are normalized to `\n`, also across chunk boundaries.
- `finish()` marks the end of input, so open constructs such as fences and math blocks close.
- `append()` after `finish()` throws until `reset()` is called or a new engine is created.
- `snapshot()` returns the latest snapshot without changing the engine.
- `reset()` drops all source and state. Ids from before the reset carry no guarantee.
- An engine is single-writer: call it from one thread at a time. Snapshots are immutable and can be read from any thread.

### Dialect

```kotlin
package com.adamglin.compose.markdown.core.dialect

public sealed class MarkdownDialect {
    public data object ChatFast : MarkdownDialect()
    public data object CommonMarkCore : MarkdownDialect()
    public data object GfmCompat : MarkdownDialect()
    public data object GfmMath : MarkdownDialect()
}
```

The syntax each dialect enables is listed in [dialects.md](dialects.md). The feature flags behind a dialect are internal.

### Snapshot and delta

```kotlin
package com.adamglin.compose.markdown.core.model

public data class MarkdownSnapshot internal constructor(
    val version: Long,
    val dialect: MarkdownDialect,
    val document: MarkdownDocument,
    val stablePrefixEnd: Int,
    val isFinal: Boolean,
)

public data class MarkdownDocument internal constructor(
    val sourceLength: Int,
    val lineCount: Int,
    val blocks: List<BlockNode>, // top-level blocks
)

public data class ParseDelta internal constructor(
    val changedBlocks: List<BlockChange>,
    val removedBlockIds: List<BlockId>,
    val dirtyRegion: TextRange,
    val snapshot: MarkdownSnapshot,
    val stats: ParseStats,
)

public data class BlockChange internal constructor(val id: BlockId, val oldIndex: Int?, val newIndex: Int, val block: BlockNode)

public data class ParseStats internal constructor(
    val appendedChars: Int,
    val processedLines: Int,
    val builtBlocks: Int,
    val preservedBlocks: Int,
    val inlineParsedBlockCount: Int,
    val inlineCacheHitBlockCount: Int,
)
```

Only the engine creates snapshots, documents, deltas and stats (their constructors and `copy` are internal), so they always satisfy the guarantees below. Nodes, ranges and ids have public constructors so apps can build documents for previews and renderer tests.

- `changedBlocks` lists the top-level blocks that are new, moved or changed, in document order. `oldIndex` is `null` for an inserted block. Nested changes show up as a changed top-level block.
- `removedBlockIds` lists the ids, at any depth, that were in the previous snapshot and are not in this one.
- Applying `removedBlockIds` and `changedBlocks` to the previous top-level list gives `snapshot.document.blocks`. Top-level blocks not listed are the same instances as in the previous snapshot; a block rebuilt equal to its previous version also keeps the previous instance, so consumers can compare blocks by identity.
- `dirtyRegion` is the source range the call parsed: from the start of the previous unterminated last line to the end of input.
- `ParseStats` holds diagnostic counters, not a complexity guarantee. `builtBlocks` counts blocks at any depth built by the call instead of taken from the cache of closed blocks. `preservedBlocks` counts stable top-level blocks carried over. The inline counters count leaf blocks (paragraphs, headings, table cells) whose inline content was parsed or reused.

### Nodes

`BlockNode` is a sealed interface. Every node has an `id: BlockId`, a `range: TextRange` and a `lineRange: LineRange`.

| Node | Content |
| --- | --- |
| `Paragraph` | `children: List<InlineNode>` |
| `Heading` | `level` (1-6), `children`, `style: HeadingStyle` (`Atx`, `Setext`) |
| `FencedCodeBlock` | `infoString`, `languageHint` (the first word of the info string, lowercased, with common aliases such as `kt` mapped to `kotlin`), `literal`, `isClosed` |
| `IndentedCodeBlock` | `literal` |
| `BlockQuote` | `children: List<BlockNode>` |
| `ListBlock` | `type: ListType` (`Bullet(bulletChar)` or `Ordered(startNumber, delimiter)`), `items: List<ListItem>`, `isLoose` |
| `ListItem` | `children: List<BlockNode>`, `taskState: TaskState?` (`Checked`, `Unchecked`) |
| `TableBlock` | `header: TableRow`, `alignments: List<TableAlignment>` (`Default`, `Left`, `Center`, `Right`), `rows` |
| `TableRow` | `cells: List<TableCell>`, `isHeader` |
| `TableCell` | `children: List<InlineNode>` |
| `ThematicBreak` | `marker` |
| `MathBlock` | `latex`, `isClosed`, `delimiter: MathBlockDelimiter` (`Dollar`, `Bracket`) |

`InlineNode` is a sealed interface. Every node has an `id: InlineId` and a `range: TextRange`.

| Node | Content |
| --- | --- |
| `Text` | `literal`, with escapes and character references decoded |
| `Emphasis`, `Strong`, `Strikethrough` | `children` |
| `CodeSpan` | `literal` |
| `Link` | `destination`, `title`, `children`, `referenceLabel` (set when resolved from a definition) |
| `Image` | `destination`, `title`, `alt: List<InlineNode>`, `referenceLabel` |
| `SoftBreak`, `HardBreak` | nothing |
| `MathSpan` | `latex`, `delimiter: MathInlineDelimiter` (`Dollar`, `Paren`) |

`TextRange(start, endExclusive)` and `LineRange(startLine, endLineExclusive)` are half-open; empty ranges are valid. `BlockId` and `InlineId` are inline value classes over a non-negative `Long`.

### Guarantees

- Snapshots never change. Every call that adds text, and the first `finish()`, returns a new snapshot with a higher `version`; a call that changes nothing (such as an empty chunk) returns the previous snapshot.
- **Streaming is invisible.** After every `append()`, the snapshot equals what a fresh engine produces for the same source in one `append()`. After `finish()`, it equals a one-shot parse followed by `finish()`. This holds in every dialect, however the input is split into chunks.
- **Stable prefix.** The stable prefix is `[0, stablePrefixEnd)`. It ends at a top-level block boundary, never moves backward within a session, and covers the whole source after `finish()`. Its top-level blocks keep their index, id, range and structure in every later snapshot and are never parsed again. Their inline content changes only when a link reference definition later in the document defines a label they use; the block is then reported in `changedBlocks` at the same index.
- **Stable ids.** A closed block keeps its id for good. A block of the mutable tail keeps its id while it keeps its identity (kind, start offset, depth and a discriminator such as heading level or list marker type). Ids are unique within a snapshot.
- Open fenced code and math blocks appear in non-final snapshots with `isClosed = false`. After `finish()`, `isClosed = false` still means the source has no closing fence.
- Link reference definitions are not blocks; they are resolved into `Link` and `Image` nodes.

The engine internals behind these guarantees are described in [incremental-model.md](incremental-model.md) and [incremental-engine.md](incremental-engine.md).

## Compose renderer (`markdown-compose`)

```kotlin
package com.adamglin.compose.markdown.compose

// Rendering
@Composable public fun Markdown(
    document: MarkdownDocument,
    modifier: Modifier = Modifier,
    codeHighlighter: CodeHighlighter? = DefaultCodeHighlighter, // null disables highlighting
    mathRenderer: MathRenderer? = null,                         // null shows LaTeX source
    imageContent: (@Composable (MarkdownImage) -> Unit)? = null, // null shows alt text
    onLinkClick: ((url: String) -> Unit)? = null,               // null opens via LocalUriHandler
)
@Composable public fun Markdown(text: String, modifier: Modifier = Modifier, dialect: MarkdownDialect = MarkdownDialect.ChatFast, /* same plug-ins */)
public fun LazyListScope.markdown(document: MarkdownDocument, key: Any, /* same plug-ins */)

// Documents
@Composable public fun rememberMarkdownDocument(text: String, dialect: MarkdownDialect = MarkdownDialect.ChatFast): MarkdownDocument

@Stable public class MarkdownState(dialect: MarkdownDialect = MarkdownDialect.ChatFast) {
    public var document: MarkdownDocument // private set
    public fun append(chunk: String)
    public fun finish()
    public fun reset()
}
@Composable public fun rememberMarkdownState(dialect: MarkdownDialect = MarkdownDialect.ChatFast): MarkdownState

// Theming
@Composable public fun MarkdownTheme(
    colors: MarkdownColors = MarkdownTheme.colors,
    typography: MarkdownTypography = MarkdownTheme.typography,
    blockSpacing: Dp = MarkdownTheme.blockSpacing,
    content: @Composable () -> Unit,
)
public object MarkdownTheme { val colors: MarkdownColors; val typography: MarkdownTypography; val blockSpacing: Dp }
public data class MarkdownColors(text, textSecondary, link, accent, surface, border, codeKeyword, codeString, codeLiteral, codeComment) // .Light, .Dark
public data class MarkdownTypography(heading1..heading6, body, table, code, label) // .Default

// Plug-ins
public fun interface CodeHighlighter { fun highlight(code: String, language: String, colors: MarkdownColors): List<HighlightToken>? }
public data class HighlightToken(val start: Int, val endExclusive: Int, val color: Color)
public object DefaultCodeHighlighter : CodeHighlighter // dev.snipme:highlights
public interface MathRenderer {
    @Composable fun BlockMath(latex: String, modifier: Modifier)
    @Composable fun InlineMath(latex: String, style: TextStyle)
}
public data class MarkdownImage(val url: String, val altText: String, val title: String?)
```

Renderer rules:

- `MarkdownState` publishes every `append`/`finish` synchronously as the engine's snapshot document, without copying or comparing it: the engine keeps every block that did not change as the same instance, so an append costs work proportional to the new text and only touched blocks recompose. `document` uses referential equality.
- `LazyListScope.markdown` costs the changed blocks per append: the item list is the snapshot's shared block list, and lazy layout only composes the visible items whose block instance changed. `Markdown(document)` lays all blocks out in one `Column`, so each append also costs a skip check, a measure and a draw per top-level block; use the lazy entry for long documents.
- `Markdown(text)` parses once per distinct `text`/`dialect` in `remember`; no engine is mutated during composition.
- `markdown-core` is compiled without the Compose compiler, so Compose treats its model types as unstable and, with strong skipping, compares them by identity. That is the intended comparison: the engine keeps unchanged blocks as the same instances, and a changed block is never compared structurally.
- Without a `MarkdownTheme`, the renderer uses `MarkdownColors.Light`; dark mode is the app's choice (`MarkdownTheme(colors = MarkdownColors.Dark)`), and the library never reads `isSystemInDarkTheme()`.
- Typography styles normally leave `color` unspecified; `MarkdownColors.text` applies unless a style sets its own color. `MarkdownTypography.code.lineHeight` is the code block line height.
- Highlighters run off the main thread for closed fences only; results are cached by code, language, colors and highlighter. Indented code renders as an unlabeled, unhighlighted code block.
- Inline math and images are composed, measured at natural size (capped to the text width) and given exact placeholders; sizes are remembered by content, so streaming a paragraph does not re-measure embeds already laid out. Unclosed math blocks show source text and never reach `MathRenderer`.
- The renderer does not add a `SelectionContainer`; wrap it when text should be selectable.
- Lazy item keys are `"$key:${blockId}"`; `key` is required so every document in one list gets distinct item keys.
- `onLinkClick` and `imageContent` may be fresh lambdas on every call (only their presence is a remember key); `codeHighlighter` and `mathRenderer` should be long-lived instances, since a new instance rebuilds the render context and misses the highlight cache.
- Tables scroll with a standard `horizontalScroll` (fling, mouse wheel, RTL and accessibility scroll actions); the grid reads the viewport width captured in front of the scroll.

## Not part of the API

- edits in the middle of the source;
- HTML rendering or export;
- access to parser caches or mutable nodes;
- hooks into reference-link resolution;
- editor caret or selection models.
