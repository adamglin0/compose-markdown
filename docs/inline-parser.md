# Inline Parser

For the repository-wide support boundary, use `docs/compatibility-report.md` as the canonical reference.

## Scope

- `InlineParser` (`markdown-core/.../core/inline/InlineParser.kt`) is independent from Compose and runs inside `markdown-core`.
- `BlockParser` owns block segmentation and block ranges; inline parsing runs on the text payloads of `Paragraph`, `Heading` and `TableCell` nodes, including those nested inside `BlockQuote` and `ListItem`.
- `FencedCodeBlock` and `MathBlock` remain raw text and do not run inline parsing.
- Every construct is gated by `MarkdownDialect.inlineFeatures`.

## Algorithm

The parser implements the CommonMark 0.31.2 reference algorithm (spec appendix "A parsing strategy"):

1. A single left-to-right scan emits text, code spans, math spans, autolinks, character references, escapes and line breaks directly.
2. Runs of `*`, `_` (and `~` for GFM strikethrough) become text nodes that are also pushed onto a **delimiter stack**, with `can open` / `can close` computed from the left/right-flanking rules (Unicode whitespace and Unicode punctuation, i.e. general categories P and S).
3. `[` and `![` are pushed onto a **bracket stack**. A `]` runs "look for link or image": it tries an inline link (`(destination "title")`), then a full, collapsed or shortcut reference. On a match the bracket content becomes the link/image, emphasis inside it is processed, and earlier link openers are deactivated (links never nest).
4. "Process emphasis" resolves the delimiter stack using the `openers_bottom` table and the rule of 3. GFM strikethrough matches `~`/`~~` openers and closers of equal length; runs of three or more tildes are literal.
5. The mutable draft tree is converted to the immutable `InlineNode` model, merging adjacent text and, when `bareAutolinks` is enabled, splitting GFM email autolinks out of text that is not inside a link or image.

Shared building blocks:

- `LinkSyntax.kt`: link destination, title and label scanners, also used by `BlockParser` for link reference definitions.
- `CharacterReferences.kt` + generated `HtmlEntityTable.kt`: backslash escapes (all ASCII punctuation), HTML5 named entities and numeric references.
- `Autolinks.kt`: CommonMark `<scheme:...>` / `<email>` autolinks and GFM extended `www.`, `http(s)://`, `ftp://` and email autolinks with cmark-gfm path validation (trailing punctuation, unbalanced `)`, trailing `&entity;`).
- Regenerate the entity table with `python3 markdown-core/scripts/generate_html_entities.py`.

## Supported Inline Nodes

- Text (with backslash escapes and entity / numeric character references decoded)
- Emphasis, Strong
- Code span
- Inline links and images with titles; reference links and images (`CommonMarkCore`, `GfmCompat`, `GfmMath`)
- Autolinks: `<scheme:...>` and `<email>`; GFM extended autolinks where `bareAutolinks` is enabled (`ChatFast`, `GfmCompat`, `GfmMath`)
- Soft break, Hard break (two or more trailing spaces, or a backslash before the line ending)
- Strikethrough (`~text~` and `~~text~~`)
- Math span (`GfmMath`): `$...$` and `\(...\)`

## Precedence

- Code spans, math spans and autolinks bind tighter than link brackets and emphasis delimiters, because they are consumed during the scan (the leftmost opener wins).
- Link brackets bind tighter than emphasis: emphasis inside a link is resolved when the link closes.
- Inline math follows code-span-like rules: a `$` opener must not be followed by whitespace or `$`; a closing `$` must not be escaped, preceded by whitespace or followed by a digit. This keeps currency such as `$5 and $10` as text.

## Linear-Time Guarantees

Every scan is bounded so that pathological input stays linear:

- code span closers come from a precomputed index of maximal backtick runs with forward-only cursors;
- math closers (`$`, `\)`) come from forward-only finders over position-local predicates;
- bracket openers are never re-scanned; link destinations cap parenthesis nesting at 32 levels and link labels at 999 characters;
- link openers are chained past image openers and deactivation stops at the first already inactive one, so each opener is deactivated once;
- closing a link only visits the delimiters inside it when processing emphasis;
- extended autolink domain scans are memoized per domain run.

`InlineParserPerformanceTest` asserts this on inputs of 40k-300k characters (nested brackets, unmatched backticks, `\(`, `$`, `[`, delimiter runs, autolink-like text).

Inline nodes nested deeper than 32 levels (emphasis, links, images) are kept as literal source text so that the resulting tree, and every recursive consumer of it, has bounded depth.

## Streaming Behavior

- A streamed prefix produces exactly the nodes a one-shot parse of that prefix produces; incomplete delimiters, links and code spans stay literal text until they are closed (`InlineParserRegressionTest.everyStreamingPrefixMatchesOneShotParse`).
- `finish()` reuses cached inline results when block text is unchanged.

## ParseStats Note

- `ParseStats` keeps block-level counters and includes inline counters:
  - `inlineParsedBlockCount`: blocks that actually ran inline parsing in this rebuild.
  - `inlineCacheHitBlockCount`: unchanged blocks resolved by inline cache reuse.

## Requirement -> Tests

- CommonMark / GFM conformance: `markdown-core/src/commonTest/kotlin/com/adamglin/compose/markdown/core/spec/SpecConformanceTest.kt`
- Audit regressions and streaming prefixes: `markdown-core/src/commonTest/kotlin/com/adamglin/compose/markdown/core/inline/InlineParserRegressionTest.kt`
- Pathological input: `markdown-core/src/commonTest/kotlin/com/adamglin/compose/markdown/core/inline/InlineParserPerformanceTest.kt`
- Inline math: `markdown-core/src/commonTest/kotlin/com/adamglin/compose/markdown/core/inline/InlineMathParserTest.kt`
- Inline node and break ranges: `markdown-core/src/commonTest/kotlin/com/adamglin/compose/markdown/core/api/InlineParserMvpTest.kt`
- Stable prefix cache reuse: `markdown-core/src/commonTest/kotlin/com/adamglin/compose/markdown/core/api/MarkdownEngineTest.kt`

## Known Limitations

- Raw inline HTML is not parsed (by design, every preset has `rawHtml = false`); tags stay literal text.
- GFM extended `www.` / URL autolinks are not recognized while a `[` or `![` bracket is open, matching cmark-gfm; this keeps `[www.example.com](url)` a single link but also leaves URLs inside unmatched brackets as text.
- Flanking checks classify UTF-16 code units, so characters outside the Basic Multilingual Plane (for example emoji) next to a delimiter count as neither whitespace nor punctuation.
- Unicode case folding of reference labels is approximated by lowercase-then-uppercase.
- Source ranges of inline nodes are offsets into the block's joined inline text; for paragraphs whose lines carry container prefixes (block quotes, list indentation) they may not map back to exact source offsets.
