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

## Stage 2 - BlockParser For ChatFast v0

- Goal: parse `ChatFast` v0 block structure in a non-incremental pass first.
- Deliverables: paragraphs, ATX headings, fenced code blocks, block quotes, basic ordered and unordered lists.
- Tests: block fixture tests, range tests, malformed tail tests, graceful degradation tests.
- Docs: update dialect matrix and architecture examples.
- Stop point: block parsing is reliable, but inline parsing is not yet complete.

## Stage 3 - InlineParser For ChatFast v0

- Goal: parse inline syntax inside parsed block payloads.
- Deliverables: inline code, emphasis, strong, links, autolinks, strikethrough, hard and soft breaks.
- Tests: delimiter ambiguity tests, incomplete-tail tests, link parsing tests, plain-text fallback tests.
- Docs: update dialect matrix and API examples.
- Stop point: full `ChatFast` v0 parse exists, but incremental reuse is still limited.

## Stage 4 - Append-Only Incremental Engine

- Goal: avoid whole-document reparsing on every append.
- Deliverables: stable prefix tracking, mutable tail handling, dirty region calculation, block cache, inline cache, `ParseDelta` emission.
- Tests: append sequence tests, stable-ID reuse tests, minimal invalidation tests, unfinished fence and list continuation tests.
- Docs: expand incremental model with concrete invariants and examples.
- Stop point: append-only streaming is efficient enough for parser-level usage, but UI layer may still be provisional.

## Stage 5 - RenderIR And Compose Renderer

- Goal: render parsed snapshots incrementally at block granularity.
- Deliverables: RenderIR, Compose mapping, keyed block rendering, preview surfaces in `markdown-compose`.
- Tests: RenderIR mapping tests, renderer state tests, block-level recomposition tests.
- Docs: renderer usage guide and sample integration notes.
- Stop point: renderer is usable, but demo app and benchmarks may still be incomplete.

## Stage 6 - Sample Chat Demo

- Goal: show the intended streaming user experience.
- Deliverables: `sample-chat` app, append simulation, snapshot visualization, diagnostics panel for block deltas.
- Tests: smoke tests for sample behavior where practical, manual verification script.
- Docs: demo usage instructions and known limitations.
- Stop point: project is demonstrable end to end for the primary use case.

## Stage 7 - Benchmarks And Compatibility Expansion

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
