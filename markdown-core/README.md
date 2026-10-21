# markdown-core

`markdown-core` contains the append-only parser, the immutable document model, the dialects and the incremental engine. It has no Compose dependency.

Responsibilities:

- append-only source buffering and line ending normalization
- CommonMark block parsing plus the GFM table, task list and math extensions a dialect enables
- CommonMark inline parsing plus GFM strikethrough, extended autolinks and inline math where enabled
- immutable `MarkdownSnapshot` / `ParseDelta` publication with stable block IDs
- resumable parsing: each line is parsed once, closed blocks are built once, and inline results are cached

Primary docs:

- `../docs/api.md`
- `../docs/dialects.md`
- `../docs/incremental-engine.md`
- `../docs/compatibility-report.md`
