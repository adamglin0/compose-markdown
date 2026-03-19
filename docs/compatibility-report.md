# Compatibility Report

## Scope

This report is the Stage 9 compatibility and acceptance summary for the current repository checkpoint.

- parser scope: `markdown-core`
- renderer scope: `markdown-compose`
- sample scope: `sample-chat`
- benchmark scope: JVM-only `benchmarks`
- spec baseline: CommonMark 0.31.2 and GitHub Flavored Markdown 0.29-gfm
- regression style: curated representative cases mapped to AST-shaped assertions, plus repository-specific streaming chunk splits

This repository targets predictable append-only rendering first, not full spec parity.

## Preset Matrices

### ChatFast

| Category | Status | Notes |
| --- | --- | --- |
| Paragraphs / soft wraps | Supported | multiline paragraphs and break nodes are covered |
| ATX + setext headings | Supported | setext upgrades use localized backward invalidation |
| Fenced code blocks | Supported | open fences stay renderable during streaming |
| Block quotes | Supported | quote continuation stays localized to affected container |
| Ordered / unordered lists | Supported | stable IDs are preserved across tail growth |
| Task list items | Supported | parsed in core, rendered as textual markers |
| Tables | Supported | pipe-table subset only |
| Thematic breaks | Supported | dedicated block node |
| Emphasis / strong / code / links | Supported | common inline forms covered |
| Strikethrough | Supported | enabled by preset |
| Autolinks / bare URLs | Supported | heuristic rather than strict full-spec matching |
| Reference links / definitions | Not supported | disabled by preset |
| Raw HTML | Not supported | explicitly disabled |
| Incremental streaming | Supported | regression covers text, quote, list, fence, table |
| Compose renderer | Partial | readable output, lightweight image/table/task handling |

### CommonMarkCore

| Category | Status | Notes |
| --- | --- | --- |
| Paragraphs / headings / quotes / lists / fences / thematic breaks | Supported | core CommonMark-style block surface |
| Tables | Not supported | lines remain ordinary paragraph text |
| Task list items | Not supported | markers remain plain list text |
| Emphasis / strong / code / links | Supported | common inline forms covered |
| Reference links / definitions | Supported | one-line definitions only |
| Strikethrough | Not supported | disabled by preset |
| Autolinks / bare URLs | Supported | pragmatic heuristics |
| Raw HTML | Not supported | explicitly disabled |
| Incremental streaming | Supported | regression covers text, quote, list, fence, reference links |
| Compose renderer | Partial | renderer consumes produced nodes, but no HTML/export path |

### GfmCompat

| Category | Status | Notes |
| --- | --- | --- |
| CommonMark-style core blocks | Supported | same core engine surface as other presets |
| Tables | Supported | pipe-table subset with alignment metadata |
| Task list items | Supported | parsed in core, rendered as textual markers |
| Reference links / definitions | Supported | one-line definitions only |
| Strikethrough | Supported | enabled by preset |
| Autolinks / bare URLs | Supported | pragmatic heuristics |
| Raw HTML | Not supported | explicitly disabled |
| Incremental streaming | Supported | regression covers text, quote, list, fence, table, reference links |
| Compose renderer | Partial | table layout is readable fallback, not GitHub-faithful layout |

## Supported

- append-only chunked parsing with immutable snapshots and explicit dirty-region reporting
- stable top-level block IDs across ordinary tail growth, including headings, lists, fences, and tables
- paragraphs, ATX headings, setext headings, thematic breaks, fenced code blocks, block quotes, and lists
- inline emphasis, strong emphasis, code spans, inline links, autolinks, bare URLs, soft breaks, hard breaks, images, and GFM strikethrough where enabled
- localized invalidation for setext upgrades, table delimiter upgrades, and late reference-definition resolution
- Compose block rendering for paragraphs, headings, quotes, lists, fenced code blocks, thematic breaks, tables, and clickable links

## Partially Supported

- CommonMark compatibility: common examples work, but delimiter edge cases and several advanced constructs are intentionally not implemented
- GFM compatibility: tables, task lists, and strikethrough are covered, but only the lightweight pipe-table subset is implemented
- reference-style links: supported only in `CommonMarkCore` and `GfmCompat`, and only for one-line definitions
- images: parsed in the core model, but renderer intentionally falls back to alt-text only
- Compose rendering: optimized for correctness and stable identity reuse, not for GitHub-style visual fidelity

## Explicitly Unsupported

- raw HTML blocks and raw HTML inline rendering
- arbitrary middle-of-document insert/delete editing
- indented code block support
- footnotes, HTML sanitization, HTML export, and rich-text editor behavior
- full CommonMark title parsing for multi-line reference definitions
- heavyweight external spec runners or large generated fixture pipelines

## Known Deviations

- autolink and bare-URL detection uses pragmatic heuristics and can differ from strict CommonMark/GFM expectations
- delimiter handling is simplified; nested emphasis precedence does not aim for full CommonMark edge-case parity
- reference definitions are single-line only, so valid multi-line spec examples intentionally degrade to plain text
- tables cover common pipe-table syntax only; GitHub-specific layout quirks and richer table edge cases are out of scope
- task list rendering uses read-only checkbox UI rather than plain textual markers
- image rendering currently emits alt text only; no built-in image fetching or sizing pipeline exists

## Regression Suite

`markdown-core/src/commonTest/kotlin/com/adamglin/compose/markdown/core/api/CompatibilityRegressionTest.kt` is the canonical lightweight compatibility regression suite.

Coverage policy:

- prefer representative official and semi-official cases over importing an entire spec harness
- map examples to AST-shape assertions so tests stay stable and readable across platforms
- add repository-specific chunk splits only where streaming behavior is the actual acceptance target

Current curated sources:

- CommonMark 0.31.2 representative examples for paragraphs, thematic breaks, setext headings, fenced code blocks, lists, block quotes, and reference links
- GFM 0.29-gfm representative examples for tables, task list items, and strikethrough
- local streaming splits for plain text, quote, list, fenced code, table, and reference-link upgrade paths

## Renderer Audit

- block rendering is stable-ID keyed and updates changed blocks instead of rebuilding the whole renderer state list
- inline annotations preserve links, emphasis, strong, code, and strike text styling
- code blocks, quotes, and tables render readably in the sample app
- renderer remains intentionally lightweight: no HTML, no image pipeline, no interactive task toggles, no advanced table sizing model

## Acceptance Summary

- repository can be cloned, built, tested, benchmarked, and inspected locally with documented commands
- final docs now identify supported behavior, partial coverage, non-goals, and prioritized follow-up work
- compatibility risk remains primarily around spec edge cases, not around the documented append-only streaming target
