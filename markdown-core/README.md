# markdown-core

`markdown-core` contains the append-only parser, immutable document model, dialect presets, and incremental engine.

Current responsibilities:

- append-only source buffering and newline normalization
- block parsing for paragraphs, headings, quotes, lists, fences, thematic breaks, and preset-dependent extensions
- inline parsing for common emphasis, links, autolinks, breaks, images, and preset-dependent extras
- immutable `MarkdownSnapshot` / `ParseDelta` publication with stable block IDs
- localized reparsing of the mutable tail plus inline-cache reuse

Primary docs:

- `../docs/compatibility-report.md`
- `../docs/incremental-engine.md`
- `../docs/dialect-matrix.md`
