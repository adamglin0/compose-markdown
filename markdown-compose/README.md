# markdown-compose

Compose Multiplatform rendering layer built on top of `markdown-core`.

Public API:

- `Markdown(text)` and `Markdown(document)` render Markdown; `LazyListScope.markdown(document)` adds one lazy item per top-level block, keyed by block id
- `MarkdownState` / `rememberMarkdownState()` stream chunks into a document, publishing each append synchronously and reusing unchanged blocks
- `MarkdownTheme(colors, typography, blockSpacing)` is the single theming mechanism (`MarkdownColors.Light` / `.Dark`, `MarkdownTypography.Default`)
- plug-ins: `CodeHighlighter` (`DefaultCodeHighlighter`, or `null` to disable), `MathRenderer`, `imageContent`, `onLinkClick` (defaults to `LocalUriHandler`)

Rendering covers paragraphs, headings, quotes, ordered/bullet/task lists, fenced code (highlighted, horizontally scrollable), math, thematic breaks and tables (horizontally scrollable when too wide).

Intentional limits:

- no HTML rendering or export
- task lists render as read-only checkboxes
- no built-in image loading; supply `imageContent`

See `../docs/api-draft.md` for signatures and `../docs/compatibility-report.md` for the renderer audit.
