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
- Stage 6: `markdown-compose` block-level rendering and snapshot/delta-driven renderer state.
- Stage 7: `ChatFast` polish plus first `CommonMarkCore` / `GfmCompat` preset expansion.
- Stage 8: benchmarks, compatibility hardening, and broader dialect coverage.

Each stage must define:

- a clear goal,
- concrete deliverables,
- tests,
- documentation updates,
- and a stop point before the next stage starts.

## Current Status

This repository currently ships a Stage 7 checkpoint for the append-only engine, Compose renderer wiring, and the first dialect preset expansion.

- Current checkpoint: append-only `SourceBuffer` and `LineIndex`, normalized newline handling, line-based `BlockParser`, inline parsing, stable prefix / mutable tail tracking, explicit dirty regions, block cache reuse, inline cache reuse, block-keyed Compose rendering, dialect presets (`ChatFast`, `CommonMarkCore`, `GfmCompat`), setext headings, tables, task lists, reference-style links/definitions, and localized dependency-driven invalidation for late definitions.
- Not done yet: raw HTML support, full CommonMark delimiter edge cases, full table alignment/render fidelity, dedicated RenderIR, or benchmark coverage.

## Dialect Snapshot

- `ChatFast`: default preset for streaming chat; keeps raw HTML off, enables setext/task-list/tables, and leaves reference-style links off by default to avoid cross-block work unless the caller opts into a richer preset.
- `CommonMarkCore`: enables reference-style links and definitions, setext headings, and stricter core compatibility while still keeping raw HTML disabled in this repository checkpoint.
- `GfmCompat`: adds task lists, tables, strikethrough, and reference links on top of the same append-only engine model.

See `docs/dialect-matrix.md`, `docs/dialect-presets.md`, and `docs/reference-links.md` for the support matrix and invalidation rules.

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

- `markdown-core`: Kotlin Multiplatform block-layer engine, immutable document model, and tests.
- `markdown-compose`: snapshot/delta-driven Compose renderer built on top of `markdown-core`, with keyed block updates and per-block inline text mapping.
- `sample-chat`: desktop-first sample app that simulates chunked chat streaming into the renderer and exposes delta/snapshot diagnostics.
- `docs`: Stage 0 architecture, ADRs, and API drafts preserved as the source of truth for later stages.

Start with:

- `docs/architecture.md`
- `docs/api-draft.md`
- `docs/core-model.md`
- `docs/dialect-matrix.md`
- `docs/incremental-model.md`
- `docs/incremental-engine.md`
- `docs/roadmap.md`
- `docs/adr/`
