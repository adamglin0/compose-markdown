# markdown-compose

Compose Multiplatform rendering layer built on top of `markdown-core`.

Stage 7 status:

- public entry points render from `MarkdownSnapshot`, `MarkdownDocument`, or `MarkdownRendererState`,
- rendering happens per top-level block with stable block ID keys; nested children are updated through whole-block replacement payloads,
- `MarkdownRendererState.apply(delta)` only replaces changed blocks and removes deleted ones,
- paragraphs, headings, lists, block quotes, fenced code blocks, and thematic breaks have baseline readable styling,
- tables and task-list markers have lightweight fallback rendering so new core nodes do not crash the UI,
- inline content maps to per-block `AnnotatedString` values with clickable links and selectable text,
- `rememberMarkdownState()` remains available as the engine-driving helper used by the sample app.

Non-goals for this stage:

- no rich table layout, checkbox interaction, or image loading,
- no core/UI boundary changes beyond consuming `MarkdownSnapshot`, `ParseDelta`, and `MarkdownDocument`.
