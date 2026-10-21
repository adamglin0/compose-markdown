# Dialect Presets

## `ChatFast`

- default preset used by `MarkdownEngine()`
- optimized for chat/LLM streaming output first
- raw HTML stays literal text
- reference-style links disabled by default
- includes the full CommonMark block structure (indented code included), task lists, tables, strikethrough, inline links, GFM extended autolinks (bare URLs and emails), and images-as-nodes

## `CommonMarkCore`

- enables core compatibility features that matter for ordinary Markdown documents
- raw HTML stays literal text, as in every preset
- enables reference-style links and link reference definitions
- keeps tables, task lists, strikethrough and GFM extended autolinks off so the preset stays closer to non-GFM CommonMark expectations; bare URLs stay text and only `<...>` autolinks are links

## `GfmCompat`

- extends the same core engine with GitHub-style affordances
- enables reference-style links, tables, task lists, strikethrough, and GFM extended autolinks
- raw HTML stays literal text, as in every preset

## `GfmMath`

- `GfmCompat` plus display math blocks and inline math (`$...$`, `\(...\)`)
- a math block opens on a line that is exactly `$$` or `\[` and closes on a line ending in `$$` / `\]`; a single line such as `$$x^2$$` is a complete block
- other lines that start with `$$` or `\[` stay paragraph text, so prose never disappears into an unclosed formula

## Default Behavior Summary

| Preset | Raw HTML | Reference Links | Tables | Task Lists | Strikethrough | Bare URL autolinks | Math |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `ChatFast` | Off | Off | On | On | On | On | Off |
| `CommonMarkCore` | Off | On | Off | Off | Off | Off | Off |
| `GfmCompat` | Off | On | On | On | On | On | Off |
| `GfmMath` | Off | On | On | On | On | On | On |

Every preset parses the same CommonMark block structure: paragraphs, headings, indented and fenced code, block quotes, lists and thematic breaks. `MarkdownBlockFeatures` only lists the block constructs that differ (task list items, tables, math blocks).

## Why Every Preset Has Indented Code

Models are trained on Markdown as GitHub renders it, where four-column indentation is a code block, so `ChatFast` renders it the same way instead of adding a preset switch. Indentation inside list items is measured from the item's content column, so continuation paragraphs under `1.` items stay paragraphs.

## Why `ChatFast` Leaves Reference Links Off

- most chat output prefers inline links over late definitions
- cross-block definition dependency tracking is more expensive than simple inline links
- callers that need reference links can opt into `CommonMarkCore` or `GfmCompat` without changing the engine architecture
