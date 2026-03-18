# Roadmap

This roadmap defines the implementation order and stop points. The order is deliberate and optimized to reduce rework.

## Stage 0 - Architecture And Boundary Freeze

- Goal: freeze module boundaries, dialect scope, incremental model, public API draft, and staged delivery plan.
- Deliverables: README, architecture docs, dialect matrix, incremental model, ADRs, module directory placeholders.
- Tests: documentation completeness and repository structure checks.
- Docs: all Stage 0 docs are the deliverable.
- Stop point: no parser or Compose implementation starts yet.

## Stage 1 - Immutable Core Skeleton

- Goal: establish `SourceBuffer`, `LineIndex`, `TextRange`, `BlockId`, and minimal immutable document model in `markdown-core`.
- Deliverables: range model, append-only source storage, line indexing, node skeletons, baseline engine shell without full parsing.
- Tests: source append tests, line lookup tests, range invariants, stable ID allocation tests.
- Docs: update architecture and API draft where naming becomes concrete.
- Stop point: source and model layer are ready, but block parsing is still limited or stubbed.

## Stage 2 - Core API And Placeholder Engine

- Goal: freeze the immutable public API, snapshot model, and append-only placeholder engine contract.
- Deliverables: `MarkdownEngine`, immutable document and delta models, dialect boundary, stable block IDs, placeholder block classification, and baseline docs.
- Tests: API behavior tests, model invariants, stable ID tests, placeholder delta tests.
- Docs: update README, API draft, and core model notes with concrete names.
- Stop point: block parsing is still provisional, but the public boundary is frozen.

## Stage 3 - BlockParser For ChatFast v0

- Goal: parse `ChatFast` v0 block structure in a non-incremental pass first.
- Deliverables: paragraphs, ATX headings, fenced code blocks, block quotes, basic ordered and unordered lists.
- Tests: block fixture tests, range tests, malformed tail tests, graceful degradation tests.
- Docs: update dialect matrix, API notes, and architecture examples.
- Stop point: block parsing is reliable, but inline parsing is not yet complete.

## Stage 4 - InlineParser For ChatFast v0

- Goal: parse inline syntax inside parsed block payloads.
- Deliverables: inline code, emphasis, strong, links, autolinks, strikethrough, hard and soft breaks.
- Tests: delimiter ambiguity tests, incomplete-tail tests, link parsing tests, plain-text fallback tests.
- Docs: update dialect matrix and API examples.
- Stop point: full `ChatFast` v0 parse exists, but incremental reuse is still limited.

## Stage 5 - Append-Only Incremental Engine

- Goal: avoid whole-document reparsing on every append.
- Deliverables: stable prefix tracking, mutable tail handling, dirty region calculation, block cache, inline cache, `ParseDelta` emission.
- Tests: append sequence tests, stable-ID reuse tests, minimal invalidation tests, unfinished fence and list continuation tests.
- Docs: expand incremental model with concrete invariants and examples.
- Stop point: append-only streaming is efficient enough for parser-level usage, but UI layer may still be provisional.

Status: implemented in the current repository checkpoint, including newline-normalized append parsing and sample diagnostics.

## Stage 6 - RenderIR And Compose Renderer

- Goal: render parsed snapshots incrementally at block granularity.
- Deliverables: RenderIR, Compose mapping, keyed block rendering, preview surfaces in `markdown-compose`.
- Tests: RenderIR mapping tests, renderer state tests, block-level recomposition tests.
- Docs: renderer usage guide and sample integration notes.
- Stop point: renderer is usable, but demo app and benchmarks may still be incomplete.

## Stage 7 - ChatFast Polish And Preset Expansion

- Goal: harden the primary streaming flow while expanding beyond the initial `ChatFast` v0 subset.
- Deliverables: preset-level support for setext headings, tables, task lists, reference-style links/definitions, and localized dependency-driven invalidation for late definitions.
- Tests: parser and incremental fixtures for newly enabled syntax, plus targeted renderer/sample smoke coverage for new node kinds.
- Docs: update dialect matrix, preset docs, core model notes, and sample usage text to reflect the expanded Stage 7 boundary.
- Stop point: the append-only engine, Compose wiring, and dialect presets are aligned for the current repository checkpoint, but dedicated benchmarks and broader compatibility work still wait for Stage 8.

## Stage 8 - Benchmarks And Compatibility Expansion

- Goal: validate performance and carefully expand syntax support.
- Deliverables: benchmark scenarios, regression thresholds, `CommonMarkCore` roadmap items, selected `GfmCompat` features.
- Tests: benchmark reproducibility checks, compatibility fixtures, regression suites.
- Docs: benchmark methodology and dialect coverage updates.
- Stop point: broadening support remains incremental and evidence-driven.

## Compatibility Expansion Order

1. Finish `ChatFast` v0 primary flow.
2. Harden append-only incremental behavior.
3. Add `CommonMarkCore` features that do not destabilize the incremental model.
4. Add selected `GfmCompat` features with explicit dependency tracking.

## Non-Goals During Early Stages

- no arbitrary in-place editing support,
- no HTML-first output path,
- no full spec parity before append-only quality is proven,
- no heavy feature expansion before benchmarks exist.
