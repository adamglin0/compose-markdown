# markdown-compose

Compose Multiplatform rendering layer built on top of `markdown-core`.

Current responsibilities:

- render `MarkdownSnapshot`, `MarkdownDocument`, or `MarkdownRendererState`
- preserve stable block identity through keyed renderer-state updates
- provide readable rendering for paragraphs, headings, lists, quotes, fenced code blocks, thematic breaks, and tables
- map inline links into clickable `AnnotatedString` annotations

Intentional limits:

- no HTML rendering or export
- task lists render as read-only checkboxes; interactive toggles are still out of scope
- no built-in image loading pipeline

See `../docs/compatibility-report.md` for the final renderer audit.
