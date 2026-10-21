# markdown-compose

Compose Multiplatform rendering layer built on top of `markdown-core`.

Public API:

- `Markdown(text)` and `Markdown(document)` render Markdown; `LazyListScope.markdown(document, key)` adds one lazy item per top-level block, keyed by `key` plus the block id
- `MarkdownState` / `rememberMarkdownState()` stream chunks into a document, publishing each append synchronously; an append costs the new text, not the document, and only the blocks it changed recompose
- `MarkdownTheme(colors, typography, blockSpacing)` is the single theming mechanism (`MarkdownColors.Light` / `.Dark`, `MarkdownTypography.Default`)
- `MarkdownComponents` has a slot per element (paragraph, heading, code block, math block, quote, list, list item, list marker, task checkbox, table, thematic break, image); each slot gets the element's data and its built-in `DefaultContent()`, so it can decorate or replace it, and containers render nested blocks with `Children()`
- plug-ins: `CodeHighlighter` (`DefaultCodeHighlighter`, or `null` to disable), `MathRenderer`, `onLinkClick` (defaults to `LocalUriHandler`)

Rendering covers paragraphs, headings, quotes, ordered/bullet/task lists, fenced code (highlighted, horizontally scrollable), math, thematic breaks and tables (horizontally scrollable when too wide).

Streaming cost: `LazyListScope.markdown` composes only the visible blocks a chunk changed, whatever the length of the document. `Markdown(document)` renders one `Column`, which skips unchanged blocks but still checks, measures and draws every top-level block on each append (about 4 ms per append at 25KB and 19 ms at 200KB on the JVM test harness), so use the lazy entry for long or streamed documents. `MarkdownStreamingCostTest` measures both.

Intentional limits:

- no HTML rendering or export
- task lists render as read-only checkboxes
- no built-in image loading; supply `MarkdownComponents(image = ...)`

See `../docs/api.md` for signatures and `../docs/compatibility-report.md` for the renderer audit.
