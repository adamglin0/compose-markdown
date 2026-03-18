# Dialect Matrix

This repository now ships three concrete presets on top of the same append-only, snapshot-based core engine.

## Preset Intent

### `ChatFast`

- default preset for streaming chat output
- favors low-latency append behavior and graceful fallback
- raw HTML stays disabled
- reference-style links are off by default

### `CommonMarkCore`

- tighter CommonMark-oriented preset without enabling GitHub-only block syntax
- raw HTML still disabled in this repository checkpoint
- reference-style links and definitions are enabled

### `GfmCompat`

- compatibility-oriented preset for common GitHub-flavored content
- keeps the same append-only engine model
- enables tables, task lists, and strikethrough

## Feature Matrix

| Feature | ChatFast | CommonMarkCore | GfmCompat | Notes |
| --- | --- | --- | --- | --- |
| Paragraphs | Yes | Yes | Yes | Baseline block support |
| ATX headings | Yes | Yes | Yes | Shared core block parser |
| Setext headings | Yes | Yes | Yes | Uses a one-block retroactive invalidation window |
| Fenced code blocks | Yes | Yes | Yes | Streaming-safe open fence handling remains supported |
| Block quotes | Yes | Yes | Yes | Quote/list boundaries stay localized to affected containers |
| Ordered / unordered lists | Yes | Yes | Yes | List continuation and sibling boundaries are corrected conservatively |
| Task list items | Yes | No | Yes | `ChatFast` keeps them because chat output uses them often |
| Tables | Yes | No | Yes | `CommonMarkCore` leaves them disabled; `ChatFast`/`GfmCompat` use a minimal retroactive table window |
| Thematic breaks | Yes | Yes | Yes | Parsed as dedicated block nodes |
| Inline code | Yes | Yes | Yes | Shared inline parser |
| Emphasis / strong | Yes | Yes | Yes | Delimiter rules still target common cases first |
| Strikethrough | Yes | No | Yes | CommonMark core preset keeps this off |
| Inline links | Yes | Yes | Yes | `[text](url)` |
| Reference-style links | No | Yes | Yes | Dependency index powers late-definition resolution |
| Reference definitions | No | Yes | Yes | Definitions are non-rendering and update only dependent blocks |
| Autolinks / bare URLs | Yes | Yes | Yes | Conservative URL heuristics stay in place |
| Images | Yes | Yes | Yes | Parsed as inline nodes only; renderer currently falls back to alt text |
| Raw HTML | No | No | No | Explicitly disabled, with plain-text fallback |

## Incremental Invalidation Rules

- setext headings: when a delimiter line arrives, the engine backs up to the previous top-level paragraph block instead of reparsing the whole document
- tables: when a delimiter row arrives, the engine backs up to the previous top-level paragraph block and reinterprets it as a table header
- quote/list continuation: when a new sibling line can extend the previous quote or list, the engine backs up to that previous top-level container only
- reference definitions: the engine keeps `label -> definition` plus `unresolved label -> dependent top-level block IDs`; a newly arrived definition reparses only the affected preserved blocks

## Deliberate Limits

- raw HTML remains disabled for all presets; there is no partial HTML support path
- table parsing currently targets common pipe-table syntax and lightweight rendering, not full layout fidelity
- reference definitions are currently one-line definitions; unsupported variants degrade to plain text
- image nodes are parsed, but Compose/sample rendering intentionally stays minimal for this stage
