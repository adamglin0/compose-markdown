# Architecture

## Goals

`compose-markdown` is designed around a narrow but practical target:

- parse Markdown in Kotlin Multiplatform;
- keep every append renderable;
- reuse as much previous work as possible on append-heavy streams;
- preserve a stable public API centered on immutable snapshots;
- keep the toolchain light enough for library and sample development.

The first optimization target remains LLM/chat-style output, not a full rich-text editor.

## Module Graph

```text
compose-markdown
|- markdown-core
|  |- SourceBuffer / LineIndex
|  |- BlockParser
|  |- InlineParser
|  |- IncrementalMarkdownEngine
|  `- Snapshot / ParseDelta / Stats
|
|- markdown-compose
|  |- Markdown composables
|  `- snapshot -> UI mapping
|
|- sample-chat
|  |- chunk driver
|  `- visual/debug verification
|
|- benchmarks
|  |- corpus generators
|  |- JVM benchmark runner
|  `- benchmark smoke task
|
`- docs
   |- architecture / incremental model / dialect matrix
   |- known limitations / performance notes
   `- release planning / ADRs
```

Dependency direction:

```text
markdown-core <- markdown-compose <- sample-chat
markdown-core <- benchmarks
```

`markdown-core` stays UI-free and benchmark code never changes public API semantics.

## Parsing Pipeline

### 1. `SourceBuffer` and `LineIndex`

- `SourceBuffer` stores normalized append-only text;
- `LineIndex` tracks newline offsets, so line ranges are O(1) and only new lines are copied out of the source.

### 2. `BlockParser`

- parses line-oriented blocks: paragraphs, headings, lists, quotes, fenced code blocks, tables, thematic breaks;
- is resumable: each complete line is incorporated once, and an unterminated last line is parsed in a cheap fork;
- `BlockTreeBuilder` builds nodes, caching closed blocks, and asks an engine-supplied allocator for stable block IDs.

### 3. `InlineParser`

- resolves inline syntax inside block-local text ranges;
- supports links, emphasis, strong, strikethrough, code spans, images, autolinks, and line breaks;
- results are cached per block ID and literal, and a growing paragraph resumes from its last final line start.

### 4. Incremental Engine

- appends normalized text into the source buffer;
- feeds completed lines to the block parser and the unterminated last line to a fork of it;
- moves closed top-level blocks into the stable prefix, built once;
- rebuilds only blocks that use a link reference definition that changed;
- rebuilds immutable `MarkdownSnapshot` / `ParseDelta` for consumers.

### 5. Compose Renderer

- renders block lists with stable IDs;
- updates at block granularity;
- keeps Compose concerns outside parser internals.

## Incremental State Model

- `stable prefix`: source range known to keep its interpretation;
- `mutable tail`: suffix that may still change meaning after more input;
- `dirty region`: the range whose lines the latest append parsed;
- `block cache`: built nodes of closed blocks, plus the reference labels they use;
- `inline cache`: per-block inline parse results.

See `docs/incremental-model.md` for the detailed rules.

## Stage 8 Engineering Notes

- benchmarks now live in a real Gradle module instead of a placeholder directory;
- CI stays lightweight and only runs build, test, benchmark smoke, and sample dry-run;
- shared normalization code moved into `com.adamglin.compose.markdown.core.internal` to reduce duplicate logic and regex churn;
- block-tree flattening now uses an accumulator instead of recursive `buildList { addAll(...) }` chains.

## Public API Boundary

Public API remains centered on:

- `MarkdownEngine`
- `MarkdownSnapshot`
- `MarkdownDocument`
- `ParseDelta`
- `ParseStats`
- `BlockNode` / `InlineNode` / `TextRange`

Internal caches, ID allocation, and parser bookkeeping remain hidden.

## Non-Goals

- arbitrary middle-of-document editing;
- raw HTML rendering;
- parser-generator-based implementation;
- benchmark-driven API changes;
- heavy release or CI infrastructure before the core model stabilizes.
