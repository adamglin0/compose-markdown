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
- `LineIndex` tracks newline offsets so block parsing can rescan only the requested range;
- Stage 8 adds cached snapshots plus direct tail inspection helpers to avoid redundant whole-buffer copies.

### 2. `BlockParser`

- parses line-oriented blocks: paragraphs, headings, lists, quotes, fenced code blocks, tables, thematic breaks;
- assigns stable block IDs through an engine-supplied allocator;
- keeps parsing range-based and leaves inline resolution for a later phase.

### 3. `InlineParser`

- resolves inline syntax inside block-local text ranges;
- supports links, emphasis, strong, strikethrough, code spans, images, autolinks, and line breaks;
- caches inline results per block ID + literal hash so preserved blocks do not reparse inline payloads.

### 4. Incremental Engine

- appends normalized text into the source buffer;
- computes the earliest safe dirty start from the previous mutable tail;
- preserves prefix block records and reparses only the dirty region;
- rehydrates inline content only where needed;
- rebuilds immutable `MarkdownSnapshot` / `ParseDelta` for consumers.

### 5. Compose Renderer

- renders block lists with stable IDs;
- updates at block granularity;
- keeps Compose concerns outside parser internals.

## Incremental State Model

- `stable prefix`: source range known to keep its interpretation;
- `mutable tail`: suffix that may still change meaning after more input;
- `dirty region`: the actual range reparsed for the latest append;
- `block cache`: preserved top-level blocks plus unresolved reference metadata;
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
