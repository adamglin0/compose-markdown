# Compatibility Report

## Scope

This report is the Stage 9 compatibility and acceptance summary for the current repository checkpoint.

- parser scope: `markdown-core`
- renderer scope: `markdown-compose`
- sample scope: `sample-chat`
- benchmark scope: JVM-only `benchmarks`
- spec baseline: CommonMark 0.31.2 and GitHub Flavored Markdown 0.29-gfm
- regression style: a vendored CommonMark 0.31.2 / GFM extension spec conformance suite, curated representative cases mapped to AST-shaped assertions, plus repository-specific streaming chunk splits

This repository targets predictable append-only rendering first, not full spec parity.

## Preset Matrices

### ChatFast

| Category | Status | Notes |
| --- | --- | --- |
| Paragraphs / soft wraps | Supported | multiline paragraphs, lazy continuation lines and break nodes are covered |
| ATX + setext headings | Supported | setext upgrades use localized backward invalidation |
| Fenced + indented code blocks | Supported | open fences stay renderable during streaming |
| Block quotes | Supported | CommonMark continuation and laziness |
| Ordered / unordered lists | Supported | CommonMark list items, tight/loose lists; stable IDs are preserved across tail growth |
| Task list items | Supported | parsed in core, rendered as read-only checkboxes |
| Tables | Supported | cmark-gfm table extension rules |
| Thematic breaks | Supported | dedicated block node |
| Emphasis / strong / code / links | Supported | CommonMark inline algorithm (delimiter + bracket stacks) |
| Strikethrough | Supported | GFM `~` / `~~` |
| Autolinks / bare URLs | Supported | CommonMark `<...>` autolinks plus GFM extended `www.` / URL / email autolinks |
| Reference links / definitions | Not supported | disabled by preset |
| Raw HTML | Not supported | HTML stays literal text |
| Incremental streaming | Supported | regression covers text, quote, list, fence, table |
| Compose renderer | Partial | readable output, lightweight image/table/task handling |

### CommonMarkCore

| Category | Status | Notes |
| --- | --- | --- |
| Paragraphs / headings / quotes / lists / code blocks / thematic breaks | Supported | every CommonMark block section passes except raw-HTML examples |
| Tables | Not supported | lines remain ordinary paragraph text |
| Task list items | Not supported | markers remain plain list text |
| Emphasis / strong / code / links | Supported | every inline spec section passes except raw-HTML examples |
| Reference links / definitions | Supported | including definitions that span several lines |
| Strikethrough | Not supported | disabled by preset |
| Autolinks | Supported | CommonMark `<scheme:...>` / `<email>` autolinks only; bare URLs are a GFM extension and stay text |
| Raw HTML | Not supported | HTML stays literal text |
| Incremental streaming | Supported | regression covers text, quote, list, fence, reference links |
| Compose renderer | Partial | renderer consumes produced nodes, but no HTML/export path |

### GfmCompat

| Category | Status | Notes |
| --- | --- | --- |
| CommonMark-style core blocks | Supported | same core engine surface as other presets |
| Tables | Supported | cmark-gfm table extension rules with alignment metadata; all GFM table examples pass |
| Task list items | Supported | parsed in core, rendered as read-only checkboxes |
| Reference links / definitions | Supported | including definitions that span several lines |
| Strikethrough | Supported | GFM `~` / `~~`; all GFM strikethrough examples pass |
| Autolinks / bare URLs | Supported | GFM extended autolinks; all GFM autolink extension examples pass |
| Raw HTML | Not supported | HTML stays literal text |
| Incremental streaming | Supported | regression covers text, quote, list, fence, table, reference links |
| Compose renderer | Partial | table layout uses content-based column widths and alignment; not full GitHub chrome |

## Spec Conformance

Pass counts from `SpecConformanceTest` (`CommonMarkCore` for CommonMark 0.31.2, `GfmCompat` for the GFM extension examples). Every section passes in full except examples that need raw HTML, which is unsupported by design: 573 of 652 CommonMark examples pass, and the 79 others are the raw HTML ones.

| Section | Passing | Excluded examples |
| --- | --- | --- |
| Tabs | 11 / 11 | |
| Precedence | 1 / 1 | |
| Thematic breaks | 19 / 19 | |
| ATX headings | 18 / 18 | |
| Setext headings | 27 / 27 | |
| Indented code blocks | 12 / 12 | |
| Fenced code blocks | 29 / 29 | |
| HTML blocks | excluded | unsupported by design |
| Link reference definitions | 26 / 27 | 201 (raw HTML) |
| Paragraphs | 8 / 8 | |
| Blank lines | 1 / 1 | |
| Block quotes | 25 / 25 | |
| List items | 48 / 48 | |
| Lists | 24 / 26 | 308, 309 (raw HTML) |
| Inlines | 1 / 1 | |
| Backslash escapes | 12 / 13 | 21 (raw HTML) |
| Entity and numeric character references | 16 / 17 | 31 (raw HTML) |
| Code spans | 21 / 22 | 344 (raw HTML) |
| Emphasis and strong emphasis | 129 / 132 | 475, 476, 477 (raw HTML) |
| Links | 86 / 90 | 491, 494, 524, 536 (raw HTML) |
| Images | 22 / 22 | |
| Autolinks | 19 / 19 | |
| Raw HTML | excluded | unsupported by design |
| Hard line breaks | 13 / 15 | 642, 643 (raw HTML) |
| Soft line breaks | 2 / 2 | |
| Textual content | 3 / 3 | |
| GFM Tables (extension) | 8 / 8 | |
| GFM Strikethrough (extension) | 2 / 2 | |
| GFM Autolinks (extension) | 11 / 11 | |

## Supported

- append-only chunked parsing with immutable snapshots and explicit dirty-region reporting
- stable top-level block IDs across ordinary tail growth, including headings, lists, fences, and tables
- CommonMark block structure per the spec's parsing strategy: paragraphs with lazy continuation lines, ATX and setext headings, thematic breaks, indented and fenced code blocks, block quotes, tight and loose lists, link reference definitions (including multi-line ones), and tabs with four-column stops
- GFM tables per cmark-gfm's table extension and task list items; display math blocks (`$$` / `\[`) in `GfmMath`
- CommonMark inline parsing per the spec's reference algorithm: emphasis and strong emphasis (flanking rules, rule of 3), code spans, inline and reference links and images with titles, autolinks, backslash escapes, entity and numeric character references, soft and hard breaks
- GFM strikethrough and extended autolinks where enabled; inline math (`$...$`, `\(...\)`) in `GfmMath`
- linear-time inline parsing on pathological input (nested brackets, unmatched backticks, `$`, `\(`, `[`)
- localized invalidation for setext upgrades, table delimiter upgrades, and late reference-definition resolution
- Compose block rendering for paragraphs, headings, quotes, lists, fenced code blocks, thematic breaks, tables, and clickable links

## Partially Supported

- GFM compatibility: tables, task lists, strikethrough and extended autolinks are covered; other GFM extensions (footnotes, raw HTML filtering) are not
- Compose tables measure preferred cell widths, keep short columns when space remains, shrink tall columns first on overflow, and apply left/center/right alignment
- reference-style links: supported only in `CommonMarkCore` and `GfmCompat`
- images: parsed in the core model, but renderer intentionally falls back to alt-text only
- Compose rendering: optimized for correctness and stable identity reuse, not for GitHub-style visual fidelity

## Explicitly Unsupported

- raw HTML blocks and raw HTML inline rendering
- arbitrary middle-of-document insert/delete editing
- footnotes, HTML sanitization, HTML export, and rich-text editor behavior

## Known Deviations

- raw inline HTML is not parsed, so spec examples that rely on it render the tags as text
- GFM extended `www.` / URL autolinks are suppressed while a link bracket is open (as in cmark-gfm)
- inline nodes nested deeper than 32 levels stay literal text
- raw HTML stays literal text, so the HTML block examples and the three list / definition examples that contain HTML comments render the tags as text
- a task marker alone on an item's line (`- [ ]`) is an empty task item, where cmark-gfm requires a space or tab after the marker
- task list rendering uses read-only checkbox UI rather than plain textual markers
- image rendering currently emits alt text only; no built-in image fetching or sizing pipeline exists

## Regression Suite

`markdown-core/src/commonTest/kotlin/com/adamglin/compose/markdown/core/spec/SpecConformanceTest.kt` runs every CommonMark 0.31.2 example and the cmark-gfm strikethrough, autolink and table extension examples (vendored as generated Kotlin by `markdown-core/scripts/generate_spec_examples.py`) through a test-only HTML renderer. Every section must pass in full except documented exclusions, and the test fails when an excluded example starts passing.

`markdown-core/src/commonTest/kotlin/com/adamglin/compose/markdown/core/api/CompatibilityRegressionTest.kt` remains the lightweight AST-shape and streaming regression suite.

Coverage policy:

- official spec examples are checked through the conformance suite; curated cases cover AST shape and streaming
- map examples to AST-shape assertions so tests stay stable and readable across platforms
- add repository-specific chunk splits only where streaming behavior is the actual acceptance target

Current curated sources:

- CommonMark 0.31.2 representative examples for paragraphs, thematic breaks, setext headings, fenced code blocks, lists, block quotes, and reference links
- `BlockStructureRegressionTest` for block-structure regressions across presets (lists, laziness, indented code, tabs, tables, math)
- GFM 0.29-gfm representative examples for tables, task list items, and strikethrough
- local streaming splits for plain text, quote, list, fenced code, table, and reference-link upgrade paths

## Renderer Audit

- block rendering is stable-ID keyed and updates changed blocks instead of rebuilding the whole renderer state list
- inline annotations preserve links, emphasis, strong, code, and strike text styling
- code blocks, quotes, and tables render readably in the sample app
- renderer remains intentionally lightweight: no HTML, no image pipeline, no interactive task toggles; tables size columns from content and honor alignment

## Acceptance Summary

- repository can be cloned, built, tested, benchmarked, and inspected locally with documented commands
- final docs now identify supported behavior, partial coverage, non-goals, and prioritized follow-up work
- compatibility risk remains primarily around spec edge cases, not around the documented append-only streaming target
