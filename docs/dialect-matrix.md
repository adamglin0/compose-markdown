# Dialect Matrix

`compose-markdown` ships four presets on top of the same append-only engine and block parser.

## Choosing a Preset

- choose `ChatFast` for chat / LLM output and the default lowest-friction API;
- choose `CommonMarkCore` when reference-style links matter more than GitHub block extras;
- choose `GfmCompat` for README-like content with tables, task lists, and strikethrough;
- choose `GfmMath` for `GfmCompat` plus display and inline math.

## Feature Matrix

| Feature | ChatFast | CommonMarkCore | GfmCompat | GfmMath | Notes |
| --- | --- | --- | --- | --- | --- |
| Paragraphs | Yes | Yes | Yes | Yes | Including lazy continuation lines |
| ATX headings | Yes | Yes | Yes | Yes | Shared block parser |
| Setext headings | Yes | Yes | Yes | Yes | Uses localized backward invalidation |
| Fenced code blocks | Yes | Yes | Yes | Yes | Open fences remain renderable during streaming |
| Indented code blocks | Yes | Yes | Yes | Yes | Four-column indentation, as GitHub renders it |
| Block quotes | Yes | Yes | Yes | Yes | CommonMark continuation and laziness |
| Ordered / unordered lists | Yes | Yes | Yes | Yes | CommonMark list items; tight and loose lists |
| Task list items | Yes | No | Yes | Yes | Kept in `ChatFast` because chat output uses them often |
| Tables | Yes | No | Yes | Yes | cmark-gfm table extension rules |
| Thematic breaks | Yes | Yes | Yes | Yes | Dedicated block nodes |
| Display math blocks | No | No | No | Yes | `$$` / `\[` fences and single-line `$$x$$` |
| Inline code | Yes | Yes | Yes | Yes | Shared inline parser |
| Emphasis / strong | Yes | Yes | Yes | Yes | CommonMark delimiter algorithm |
| Strikethrough | Yes | No | Yes | Yes | GFM `~` / `~~` |
| Inline math | No | No | No | Yes | `$...$`, `\(...\)` |
| Inline links | Yes | Yes | Yes | Yes | `[text](url "title")` |
| Reference-style links | No | Yes | Yes | Yes | Uses dependency index and late-definition reprocessing |
| Reference definitions | No | Yes | Yes | Yes | Including definitions that span several lines |
| Autolinks (`<...>`) | Yes | Yes | Yes | Yes | Any scheme and email addresses |
| GFM extended autolinks (bare URLs) | Yes | No | Yes | Yes | `www.`, `http(s)://`, `ftp://`, emails |
| Images | Yes | Yes | Yes | Yes | Parsed in core; rendering remains minimal |
| Raw HTML | No | No | No | No | HTML stays literal text |

## Incremental Invalidation Rules

- setext headings: when the underline arrives, the engine backs up to the previous paragraph block;
- tables: when the delimiter row arrives, the engine backs up to the candidate header paragraph only;
- quote/list continuation: future lines can widen the reparse boundary to the surrounding top-level container;
- reference definitions: new labels rehydrate only preserved blocks that previously reported unresolved references for that label.

## Practical Guidance

- use `ChatFast` unless you know you need reference links;
- `CommonMarkCore` is the best fit for stricter markdown text without GitHub extras;
- `GfmCompat` is the benchmark preset because it exercises the broadest Stage 8 block/inline surface.

## Deliberate Limits

- no raw HTML in any preset;
- no footnotes or other GFM extensions beyond tables, task lists, strikethrough and extended autolinks.
