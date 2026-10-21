# Public API Draft

This document now reflects the Stage 5 append-only incremental checkpoint. Names may still receive small adjustments later, but the boundary and object roles should remain stable.

## Design Rules

- Public objects are immutable.
- Internal parser state stays internal.
- Public structures use source ranges and stable IDs.
- `append()` always tries to return a displayable snapshot, even if the tail is still syntactically incomplete.
- Deltas are block-oriented so UI can update incrementally.

## Draft API

```kotlin
package com.adamglin.compose.markdown.core.api

interface MarkdownEngine {
    val dialect: MarkdownDialect

    fun append(chunk: String): ParseDelta
    fun finish(): ParseDelta
    fun snapshot(): MarkdownSnapshot
    fun reset()
}

data class MarkdownSnapshot(
    val version: Long,
    val dialect: MarkdownDialect,
    val document: MarkdownDocument,
    val stablePrefixRange: TextRange,
    val dirtyRegion: TextRange,
    val isFinal: Boolean,
)

data class MarkdownDocument(
    val sourceLength: Int,
    val lineCount: Int,
    val blocks: List<BlockNode>,
)

data class ParseDelta(
    val version: Long,
    val changedBlocks: List<BlockChange>,
    val insertedBlockIds: List<BlockId>,
    val updatedBlockIds: List<BlockId>,
    val removedBlockIds: List<BlockId>,
    val stablePrefixRange: TextRange,
    val dirtyRegion: TextRange,
    val snapshot: MarkdownSnapshot,
    val stats: ParseStats,
    val hasStateChange: Boolean,
)
```

## Public Concepts

### `MarkdownEngine`

- `append(chunk: String)`: appends new source text and returns the block-level delta.
- `finish()`: signals end-of-input so open constructs can close deterministically.
- `append()` is invalid after `finish()` until `reset()` is called or a new engine is created.
- `snapshot()`: returns the latest immutable snapshot without mutating parser state.
- `reset()`: clears all source, caches, and parser state.

### `MarkdownSnapshot`

Represents the latest displayable state. It contains:

- a monotonically increasing version,
- the active dialect,
- the immutable document,
- the current stable prefix boundary,
- the current dirty region,
- whether the stream is final.

### `MarkdownDocument`

Represents the parsed document model at one version. It is immutable and range-based.

### `ParseDelta`

Describes what changed after one append or finish operation:

- updated or inserted blocks,
- explicit inserted and updated block ID sets,
- removed block IDs,
- the dirty region used by incremental reparsing,
- whether the operation changed externally observable engine state even when block diffs stayed empty,
- the post-update snapshot.

## Additional Public Types

The following types are expected to be public in some form:

```kotlin
@JvmInline
value class BlockId(val raw: Long)

@JvmInline
value class InlineId(val raw: Long)

data class TextRange(
    val start: Int,
    val endExclusive: Int,
)

data class LineRange(
    val startLine: Int,
    val endLineExclusive: Int,
)

data class BlockChange(
    val id: BlockId,
    val oldIndex: Int?,
    val newIndex: Int,
    val block: BlockNode,
)

data class ParseStats(
    val parsedBlockCount: Int,
    val changedBlockCount: Int,
    val reusedBlockCount: Int,
    val inlineParsedBlockCount: Int,
    val inlineCacheHitBlockCount: Int,
    val appendedChars: Int,
    val processedLines: Int,
    val reparsedBlocks: Int,
    val preservedBlocks: Int,
    val fallbackCount: Int,
    val fallbackReason: String?,
)
```

`BlockNode` currently covers: `Document`, `Paragraph`, `Heading`, `FencedCodeBlock`, `IndentedCodeBlock`, `BlockQuote`, `ListBlock` (with `ListType.Bullet(bulletChar)` or `ListType.Ordered(startNumber, delimiter)`), `ListItem`, `ThematicBreak`, `TableBlock` / `TableRow` / `TableCell`, and `MathBlock`.

`InlineNode` currently exposes the planned public node set: `Text`, `Emphasis`, `Strong`, `CodeSpan`, `Link`, `SoftBreak`, `HardBreak`, `Strikethrough`, and `UnsupportedInline`.

## API Invariants

- `version` increases after every successful state mutation.
- `snapshot()` is always safe to call after any `append()`.
- `stablePrefixRange` never moves backward unless `reset()` is called.
- `append()` after `finish()` fails with a clear error until `reset()` is called or a new engine is created.
- block IDs stay stable while a block's identity is preserved across reparses.
- `hasStateChange` is true whenever the returned snapshot differs from the previous snapshot, including `finish()` transitions that only change `isFinal` or `stablePrefixRange`.
- `dirtyRegion` covers the earliest source offset the current engine actually reparsed; in the normal append path this is the mutable-tail boundary rather than `0`.
- snapshots are safe for UI read access because they expose immutable values only.

## Stage 5 Incremental Metrics

- `ParseDelta.changedBlocks` is reported at the top-level block list only; nested children are reflected through the replacement block payloads.
- `insertedBlockIds` and `updatedBlockIds` are derived from the top-level delta classification.
- `ParseStats.parsedBlockCount` / `reparsedBlocks` count reparsed top-level tail blocks for the current update.
- `ParseStats.reusedBlockCount` / `preservedBlocks` count top-level blocks preserved from block cache.
- `ParseStats.inlineParsedBlockCount` and `inlineCacheHitBlockCount` are counted on text-bearing reparsed blocks (`Paragraph`/`Heading`) during inline hydration.
- `fallbackCount` and `fallbackReason` remain reserved diagnostic fields; the current Stage 5 engine keeps them at `0` / `null`.

## Deferred Surface Area

Not part of the initial public API:

- arbitrary text replacement APIs,
- HTML renderer APIs,
- direct cache inspection,
- mutable AST nodes,
- reference-link resolution hooks,
- editor caret or selection models.

## Stage 5 Stop Point

Stage 5 freezes the append-only incremental contract: stable prefix reuse, mutable-tail reparsing, newline normalization before parsing, and explicit dirty-region reporting are now part of the expected engine behavior.

## Compose Renderer API (`markdown-compose`)

```kotlin
package com.adamglin.compose.markdown.compose

// Rendering
@Composable fun Markdown(
    document: MarkdownDocument,
    modifier: Modifier = Modifier,
    codeHighlighter: CodeHighlighter? = DefaultCodeHighlighter, // null disables highlighting
    mathRenderer: MathRenderer? = null,                         // null shows LaTeX source
    imageContent: (@Composable (MarkdownImage) -> Unit)? = null, // null shows alt text
    onLinkClick: ((url: String) -> Unit)? = null,               // null opens via LocalUriHandler
)
@Composable fun Markdown(text: String, modifier: Modifier = Modifier, dialect: MarkdownDialect = MarkdownDialect.ChatFast, /* same plug-ins */)
fun LazyListScope.markdown(document: MarkdownDocument, key: Any, /* same plug-ins */)

// Documents
@Composable fun rememberMarkdownDocument(text: String, dialect: MarkdownDialect = MarkdownDialect.ChatFast): MarkdownDocument

@Stable class MarkdownState(dialect: MarkdownDialect = MarkdownDialect.ChatFast) {
    val document: MarkdownDocument
    fun append(chunk: String)
    fun finish()
    fun reset()
}
@Composable fun rememberMarkdownState(dialect: MarkdownDialect = MarkdownDialect.ChatFast): MarkdownState

// Theming
@Composable fun MarkdownTheme(
    colors: MarkdownColors = MarkdownTheme.colors,
    typography: MarkdownTypography = MarkdownTheme.typography,
    blockSpacing: Dp = MarkdownTheme.blockSpacing,
    content: @Composable () -> Unit,
)
object MarkdownTheme { val colors: MarkdownColors; val typography: MarkdownTypography; val blockSpacing: Dp }
data class MarkdownColors(text, textSecondary, link, accent, surface, border, codeKeyword, codeString, codeLiteral, codeComment) // .Light, .Dark
data class MarkdownTypography(heading1..heading6, body, table, code, label) // .Default

// Plug-ins
fun interface CodeHighlighter { fun highlight(code: String, language: String, colors: MarkdownColors): List<HighlightToken>? }
data class HighlightToken(val start: Int, val endExclusive: Int, val color: Color)
object DefaultCodeHighlighter : CodeHighlighter // dev.snipme:highlights
interface MathRenderer {
    @Composable fun BlockMath(latex: String, modifier: Modifier)
    @Composable fun InlineMath(latex: String, style: TextStyle)
}
data class MarkdownImage(val url: String, val altText: String, val title: String?)
```

Renderer rules:

- `MarkdownState` publishes every `append`/`finish` synchronously and keeps the previous instance of every block that did not change, so only touched blocks recompose. It only reads `ParseDelta.snapshot`.
- `Markdown(text)` parses once per distinct `text`/`dialect` in `remember`; no engine is mutated during composition.
- Without a `MarkdownTheme`, the renderer uses `MarkdownColors.Light`; dark mode is the app's choice (`MarkdownTheme(colors = MarkdownColors.Dark)`), never `isSystemInDarkTheme()`.
- Typography styles normally leave `color` unspecified; `MarkdownColors.text` applies unless a style sets its own color. `MarkdownTypography.code.lineHeight` is the code block line height.
- Highlighters run off the main thread for closed fences only; results are cached by code, language, colors and highlighter. Indented code renders as an unlabeled, unhighlighted code block.
- Inline math and images are composed, measured at natural size (capped to the text width) and given exact placeholders; sizes are remembered by content, so streaming a paragraph does not re-measure embeds already laid out. Unclosed math blocks show source text and never reach `MathRenderer`.
- The renderer does not add a `SelectionContainer`; wrap it when text should be selectable.
- Lazy item keys are `"$key:${blockId}"`; `key` is required so every document in one list gets distinct item keys.
- `onLinkClick` and `imageContent` may be fresh lambdas on every call (only their presence is a remember key); `codeHighlighter` and `mathRenderer` should be long-lived instances, since a new instance rebuilds the render context and misses the highlight cache.
- Tables scroll with a standard `horizontalScroll` (fling, mouse wheel, RTL and accessibility scroll actions); the grid reads the viewport width captured in front of the scroll.
