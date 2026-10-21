# Dialects

`MarkdownDialect` picks the syntax a parse accepts. All four dialects run on the same engine and parse the full CommonMark block and inline structure. They differ only in the GFM and math extensions they enable and in whether link reference definitions are resolved.

| Dialect | Choose it for |
| --- | --- |
| `ChatFast` (default) | Chat and LLM output. |
| `CommonMarkCore` | Plain CommonMark documents. |
| `GfmCompat` | README-style content. |
| `GfmMath` | `GfmCompat` plus LaTeX math. |

## Feature matrix

| Feature | ChatFast | CommonMarkCore | GfmCompat | GfmMath |
| --- | --- | --- | --- | --- |
| CommonMark blocks: paragraphs, ATX and setext headings, indented and fenced code, block quotes, lists, thematic breaks | Yes | Yes | Yes | Yes |
| CommonMark inlines: code spans, emphasis, strong, inline links, images, `<...>` autolinks, escapes, character references, breaks | Yes | Yes | Yes | Yes |
| Reference links and link reference definitions | No | Yes | Yes | Yes |
| GFM tables | Yes | No | Yes | Yes |
| GFM task list items | Yes | No | Yes | Yes |
| GFM strikethrough (`~` / `~~`) | Yes | No | Yes | Yes |
| GFM extended autolinks (`www.`, `http(s)://`, `ftp://`, emails) | Yes | No | Yes | Yes |
| Display math (`$$…$$`, `\[…\]`) | No | No | No | Yes |
| Inline math (`$…$`, `\(…\)`) | No | No | No | Yes |
| Raw HTML | No | No | No | No |

Raw HTML is never parsed: HTML blocks and inline tags stay literal text in every dialect. Footnotes and other GFM extensions are not implemented.

## `ChatFast`

`ChatFast` is GFM without link reference definitions. In a stream, a definition such as `[docs]: https://example.com` can arrive after the links that use it, and resolving it would turn already rendered text into links. `ChatFast` keeps such a line as paragraph text, and `[text][label]` stays text. Chat output almost always uses inline links, so little is lost.

## `CommonMarkCore`

CommonMark 0.31.2 with no extensions. Table rows stay paragraph text, task markers stay list text, `~` is literal and bare URLs are not links; only `<...>` autolinks are.

## `GfmCompat`

CommonMark plus the GFM extensions above, following cmark-gfm. This is the dialect the benchmarks use.

## `GfmMath`

`GfmCompat` plus math, parsed into `BlockNode.MathBlock` and `InlineNode.MathSpan`. The renderer passes them to the app's `MathRenderer` (see the README).

- A display math block opens on a line that is exactly `$$` or `\[`, and closes on a line that ends in `$$` or `\]`. A single line such as `$$x^2$$` or `\[a + b\]` is a complete block. Any other line that starts with `$$` or `\[` stays paragraph text, so prose never disappears into an unclosed formula.
- An inline `$` opener must not be followed by whitespace or `$`. A closing `$` must not be escaped, preceded by whitespace or followed by a digit. This keeps currency such as `$5 and $10` as text.

## Indented code in every dialect

Models learn Markdown as GitHub renders it, where four columns of indentation make a code block. Every dialect, `ChatFast` included, renders it the same way. Indentation inside list items is measured from the item's content column, so a continuation paragraph under a `1.` item stays a paragraph.

## Streaming

Every dialect has the same streaming guarantee: after every chunk the document equals a one-shot parse of the same text (see [api.md](api.md#guarantees)). In dialects with reference links, a definition that arrives later rebuilds only the blocks that use its label, and they keep their ids.
