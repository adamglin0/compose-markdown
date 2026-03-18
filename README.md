# markstream

`markstream` is a Kotlin Multiplatform Markdown parsing and rendering project for Compose Multiplatform, optimized first for append-only LLM streaming output.

The project goal is not a generic HTML-first Markdown toolchain. The primary target is a chat-style rendering engine that can accept partial text, keep producing a displayable snapshot, and reuse as much previous parse work as possible after every `append()`.

## Project Direction

- Hand-written parser only. No parser generator, no ANTLR, no tree-sitter, no PEG generator, no third-party Markdown parser.
- `commonMain`-first core. Platform-specific code is a last resort.
- Public API stays small and mostly immutable. Internal parser state may be mutable.
- Core models are range-based and stable-ID-based to avoid repeated substring allocation and whole-document replacement.
- Rendering updates happen at block granularity, not by rebuilding one giant `AnnotatedString` for the whole document.
- Default dialect progression is `ChatFast` -> `CommonMarkCore` -> `GfmCompat`.
- Raw HTML is disabled by default. Unsupported syntax degrades to plain text instead of throwing.

## Planned Modules

- `markdown-core`: source model, parsers, incremental engine, document model, RenderIR.
- `markdown-compose`: Compose Multiplatform rendering layer built on top of `markdown-core`.
- `sample-chat`: demo app for streaming chat rendering.
- `benchmarks`: planned benchmark suite for append-heavy and steady-state parsing workloads.

## Architecture Snapshot

```
append(chunk)
    |
    v
SourceBuffer + LineIndex
    |
    v
BlockParser
    |
    v
InlineParser
    |
    v
IncrementalEngine
    |
    v
MarkdownDocument + ParseDelta + Snapshot
    |
    v
RenderIR
    |
    v
Compose renderer
```

Core parsing and incremental decisions stay inside `markdown-core`. Compose-specific presentation stays outside the core module.

## Dialect Strategy

- `ChatFast`: fast, predictable subset optimized for chat and streaming.
- `CommonMarkCore`: stricter compatibility for broadly expected CommonMark behavior.
- `GfmCompat`: targeted extensions that matter for GitHub-style content.

`ChatFast` v0 is the first delivery target. It supports the subset most common in LLM chat output and intentionally leaves harder cross-block dependency features for later phases.

## Phase Roadmap

The roadmap below describes the intended end-state for each stage. The current repository is still earlier than most of those targets.

- Stage 0: architecture, ADRs, dialect boundaries, incremental model, public API draft.
- Stage 1: `SourceBuffer`, `LineIndex`, text ranges, stable block IDs, immutable document skeleton.
- Stage 2: core API, AST, range, stable IDs, snapshot model, dialect boundary, placeholder engine, tests, docs.
- Stage 3: `BlockParser` for `ChatFast` v0 blocks.
- Stage 4: `InlineParser` for `ChatFast` v0 inline syntax.
- Stage 5: append-only `IncrementalEngine`, dirty-region tracking, parse deltas, cache reuse.
- Stage 6: `RenderIR` and `markdown-compose` block-level rendering.
- Stage 7: `sample-chat` demo plus first benchmark scenarios.
- Stage 8: compatibility hardening, `CommonMarkCore`, then selected `GfmCompat` features.

Each stage must define:

- a clear goal,
- concrete deliverables,
- tests,
- documentation updates,
- and a stop point before the next stage starts.

## Current Status

This repository currently ships a Stage 2 checkpoint for the immutable core API and placeholder engine boundary.

- Current checkpoint: range and ID models, immutable document / snapshot / delta types, dialect boundary, placeholder append-only engine, internal mutable session placeholders, snapshot-driven Compose rendering scaffolding, and model-focused tests.
- Not done yet: real block parsing, real inline parsing, incremental cache reuse beyond placeholder ID reuse, production renderer behavior, or benchmark coverage.

## Project Structure

```
.
|- markdown-core
|  |- src/commonMain/kotlin/dev/markstream/core/...
|  |- src/commonTest/kotlin/dev/markstream/core/...
|  |- src/jvmMain/kotlin/dev/markstream/core/...
|  `- src/jvmTest/kotlin/dev/markstream/core/...
|
|- markdown-compose
|  `- src/commonMain/kotlin/dev/markstream/compose/...
|
|- sample-chat
|  |- src/commonMain/kotlin/dev/markstream/sample/chat/...
|  `- src/desktopMain/kotlin/dev/markstream/sample/chat/...
|
`- docs
   `- adr/
```

- `markdown-core`: Kotlin Multiplatform placeholder engine, immutable document model, and tests.
- `markdown-compose`: snapshot-driven placeholder renderer built on top of `markdown-core`; temporary Material3 styling only serves the Stage 2 placeholder surface and is not a long-term API commitment.
- `sample-chat`: desktop-first sample app that wires input text into the placeholder renderer.
- `docs`: Stage 0 architecture, ADRs, and API drafts preserved as the source of truth for later stages.

Start with:

- `docs/architecture.md`
- `docs/api-draft.md`
- `docs/core-model.md`
- `docs/dialect-matrix.md`
- `docs/incremental-model.md`
- `docs/roadmap.md`
- `docs/adr/`
