# Dialect Matrix

`markstream` currently ships three presets on top of the same append-only engine.

## Choosing a Preset

- choose `ChatFast` for chat / LLM output and the default lowest-friction API;
- choose `CommonMarkCore` when reference-style links matter more than GitHub block extras;
- choose `GfmCompat` for README-like content with tables, task lists, and strikethrough.

## Feature Matrix

| Feature | ChatFast | CommonMarkCore | GfmCompat | Notes |
| --- | --- | --- | --- | --- |
| Paragraphs | Yes | Yes | Yes | Baseline block support |
| ATX headings | Yes | Yes | Yes | Shared block parser |
| Setext headings | Yes | Yes | Yes | Uses localized backward invalidation |
| Fenced code blocks | Yes | Yes | Yes | Open fences remain renderable during streaming |
| Block quotes | Yes | Yes | Yes | Continuation stays localized to affected containers |
| Ordered / unordered lists | Yes | Yes | Yes | Shared list parser |
| Task list items | Yes | No | Yes | Kept in `ChatFast` because chat output uses them often |
| Tables | Yes | No | Yes | Lightweight pipe-table implementation |
| Thematic breaks | Yes | Yes | Yes | Dedicated block nodes |
| Inline code | Yes | Yes | Yes | Shared inline parser |
| Emphasis / strong | Yes | Yes | Yes | Common cases first |
| Strikethrough | Yes | No | Yes | GFM-oriented extension |
| Inline links | Yes | Yes | Yes | `[text](url)` |
| Reference-style links | No | Yes | Yes | Uses dependency index and late-definition reprocessing |
| Reference definitions | No | Yes | Yes | One-line definitions only in current checkpoint |
| Autolinks / bare URLs | Yes | Yes | Yes | Conservative heuristics |
| Images | Yes | Yes | Yes | Parsed in core; rendering remains minimal |
| Raw HTML | No | No | No | Explicitly disabled |

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
- no full CommonMark delimiter algorithm yet;
- no multi-line reference definitions;
- no advanced table layout fidelity beyond common pipe-table syntax.
