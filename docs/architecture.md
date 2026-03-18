# Architecture

## Goals

`markstream` is designed for a narrow first target with room to grow:

- parse Markdown in Kotlin Multiplatform,
- render it in Compose Multiplatform,
- optimize for append-only streaming text,
- keep the public model stable and immutable,
- and evolve toward broader compatibility without rewriting the core.

The first optimization target is LLM chat output, not a fully general Markdown editor.

## Module Graph

```
markstream
|- markdown-core
|  |- SourceBuffer / LineIndex
|  |- BlockParser
|  |- InlineParser
|  |- IncrementalEngine
|  |- MarkdownDocument / Snapshot / ParseDelta
|  `- RenderIR
|
|- markdown-compose
|  |- Compose renderer
|  |- block-level composables
|  `- RenderIR -> UI mapping
|
|- sample-chat
|  |- streaming demo
|  |- append() driver
|  `- visual verification surface
|
`- benchmarks (planned)
   |- append-heavy parser benchmarks
   |- block reuse benchmarks
   `- rendering churn benchmarks
```

Dependency direction:

```
markdown-core <- markdown-compose <- sample-chat
markdown-core <- benchmarks
```

`markdown-core` must not depend on Compose.

## Layered Pipeline

The parsing pipeline is explicitly layered and each layer owns a narrow responsibility.

### 1. SourceBuffer / LineIndex

- Stores the append-only source text.
- Tracks line starts and line ranges without repeated full rescans.
- Exposes offsets and ranges used by later stages.
- Avoids eager substring creation.

### 2. BlockParser

- Scans line-oriented block structure.
- Produces block nodes with source ranges and stable IDs.
- Handles paragraph formation, headings, fenced code blocks, block quotes, and lists.
- Does not resolve inline syntax.

### 3. InlineParser

- Parses text spans inside block payload ranges.
- Produces inline nodes with ranges back into the source buffer.
- Runs per block and can be cached per block.

### 4. IncrementalEngine

- Accepts append-only updates.
- Determines the smallest dirty region that must be reparsed.
- Reuses stable prefix blocks and cached inline results when valid.
- Emits `ParseDelta` describing block-level changes.

### 5. RenderIR / Presentation Model

- Converts parsed document nodes into a renderer-friendly model.
- Keeps styling and UI concerns out of parser internals.
- Preserves stable block identity so UI can update only changed blocks.

### 6. Compose Renderer

- Maps block-level `RenderIR` to composables.
- Uses stable IDs for keyed recomposition.
- Never depends on internal parser state machines.

## Module Responsibilities

### `markdown-core`

Owns:

- source storage,
- ranges and IDs,
- dialect definitions,
- block parsing,
- inline parsing,
- incremental invalidation,
- document snapshots,
- parse deltas,
- RenderIR generation.

Does not own:

- Compose UI,
- HTML rendering,
- rich editor behaviors,
- arbitrary in-place document editing.

### `markdown-compose`

Owns:

- `@Composable` rendering entry points,
- theming hooks,
- block-level UI mapping,
- efficient UI updates using block IDs.

Does not own:

- source mutation,
- parser caches,
- dialect parsing rules,
- core document storage.

### `sample-chat`

Owns:

- a concrete streaming demo,
- developer-visible append scenarios,
- manual visual verification.

It is not the source of truth for architecture decisions.

### `benchmarks`

Planned later. It will measure:

- throughput per append,
- reparsed source length per append,
- block reuse ratio,
- render update churn.

## Package Planning

Initial package layout target:

```
dev.markstream.core.source
dev.markstream.core.range
dev.markstream.core.model
dev.markstream.core.block
dev.markstream.core.inline
dev.markstream.core.incremental
dev.markstream.core.dialect
dev.markstream.core.render

dev.markstream.compose
dev.markstream.compose.renderer
dev.markstream.compose.theme

dev.markstream.sample.chat
```

Files should stay small and single-purpose. Parser state machines must not leak into public API packages.

## Public API Boundary

Public API centers on an engine plus immutable snapshots:

- `MarkdownEngine`
- `ParseDelta`
- `MarkdownDocument`
- `MarkdownSnapshot`
- range types and stable block IDs

The engine hides mutable internals. Consumers observe immutable results and block-level deltas.

Details live in `docs/api-draft.md`.

## Non-Goals

- No arbitrary mid-document editing in v0.
- No HTML-first output pipeline.
- No promise of full CommonMark or GFM compliance from day one.
- No parser generator or grammar tooling.
- No whole-document `AnnotatedString` replacement strategy.

## Testing Strategy by Layer

- `SourceBuffer / LineIndex`: offset and line-boundary tests.
- `BlockParser`: block fixture tests and range invariants.
- `InlineParser`: inline fixture tests and delimiter edge cases.
- `IncrementalEngine`: append-sequence tests, dirty-region tests, stable-ID reuse tests.
- `RenderIR`: mapping tests from document nodes to presentation model.
- `markdown-compose`: renderer tests focused on block-level updates, not parser correctness.

## Stage 0 Stop Point

Stage 0 stops after architecture, API, dialect, and incremental boundaries are documented and directory/module layout is frozen at a planning level.
