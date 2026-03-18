# Dialect Presets

## `ChatFast`

- default preset used by `MarkdownEngine()`
- optimized for chat/LLM streaming output first
- raw HTML disabled
- reference-style links disabled by default
- includes setext headings, task lists, tables, strikethrough, inline links, and images-as-nodes

## `CommonMarkCore`

- enables core compatibility features that matter for ordinary Markdown documents
- keeps raw HTML disabled in this repository checkpoint
- enables reference-style links and link reference definitions
- keeps tables and task lists off so the preset stays closer to non-GFM CommonMark expectations

## `GfmCompat`

- extends the same core engine with GitHub-style affordances
- enables reference-style links, tables, task lists, and strikethrough
- still inherits the same raw-HTML-disabled policy for now

## Default Behavior Summary

| Preset | Raw HTML | Reference Links | Tables | Task Lists | Strikethrough |
| --- | --- | --- | --- | --- | --- |
| `ChatFast` | Off | Off | On | On | On |
| `CommonMarkCore` | Off | On | Off | Off | Off |
| `GfmCompat` | Off | On | On | On | On |

## Why `ChatFast` Leaves Reference Links Off

- most chat output prefers inline links over late definitions
- cross-block definition dependency tracking is more expensive than simple inline links
- callers that need reference links can opt into `CommonMarkCore` or `GfmCompat` without changing the engine architecture
