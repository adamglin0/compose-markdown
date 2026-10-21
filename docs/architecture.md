# Architecture

`compose-markdown` parses Markdown that arrives in chunks, such as an LLM reply, and renders it with Compose Multiplatform. The design follows from three goals:

- every chunk produces a renderable document that equals a one-shot parse of the same text;
- a chunk costs work proportional to the new text, not to the document;
- the public model is immutable, so a UI can read snapshots while the engine keeps parsing.

It is not a rich-text editor: the source is append-only.

## Modules

```text
markdown-core <- markdown-compose <- sample-chat
markdown-core <- benchmarks
```

- `markdown-core` (published): parser, immutable model, dialects and incremental engine. No UI or Compose dependency.
- `markdown-compose` (published): Compose Multiplatform renderer, `MarkdownState` and theming.
- `sample-chat`: desktop, Android, iOS and web sample that streams scripted replies and plugs in LaTeX renderers.
- `benchmarks`: JVM benchmark runner; `benchmarkSmoke` runs in CI.

## Parsing pipeline (`markdown-core`)

1. **Source.** `ChunkNormalizer` turns `\r\n` and `\r` into `\n`, also across chunk boundaries, and holds back a split surrogate pair. `SourceBuffer` stores the text, and `LineIndex` records line boundaries so only new lines are copied out.
2. **Block parser** ([block-parser.md](block-parser.md)). A resumable CommonMark block parser plus GFM tables, task list items and display math. Each complete line is incorporated once. The unterminated last line is parsed in a cheap fork that copies only the open blocks.
3. **Inline parser** ([inline-parser.md](inline-parser.md)). The CommonMark reference algorithm with delimiter and bracket stacks, plus GFM strikethrough and extended autolinks and inline math. It runs on the text of paragraphs, headings and table cells, and every scan is bounded so pathological input stays linear.
4. **Incremental engine** ([incremental-engine.md](incremental-engine.md)). Closed top-level blocks form the stable prefix and are built once. The open top-level block is rebuilt from the cached nodes of its closed children. Inline results are cached per block, and a growing paragraph resumes from its last final line start. A link reference definition that arrives later rebuilds only the blocks that use its label. Each call publishes an immutable `MarkdownSnapshot` and a `ParseDelta`.

The guarantees this gives consumers are listed in [api.md](api.md#guarantees), and the model behind them is in [incremental-model.md](incremental-model.md).

## Renderer (`markdown-compose`)

- Composables map snapshot nodes straight to UI, with no intermediate render model. Every block is keyed by its stable id, at the top level, inside quotes and lists, and as lazy items with `LazyListScope.markdown`.
- `MarkdownState` publishes the engine's snapshot document for each chunk without copying it. The engine keeps every unchanged block as the same instance, and Compose compares core model values by identity, so every block a chunk did not touch skips recomposition. `LazyListScope.markdown` composes only the visible changed blocks; `Markdown(document)` still checks, measures and draws every top-level block on each append (see [performance-notes.md](performance-notes.md#compose-streaming-cost)).
- Code highlighting runs off the main thread with a shared cache. LaTeX and images go through app-supplied plug-ins (`MathRenderer`, `imageContent`), so the library ships no math engine and no image loader.

## Build and verification

- Both published modules use Kotlin explicit API mode and Kotlin's ABI validation. The reference dumps live in each module's `api/` directory.
- `./gradlew ciCheck` builds every module, runs the tests on every target the host supports, and runs the benchmark smoke run. CI runs it on Linux, plus the library iOS simulator tests on macOS.
- The tests cover the CommonMark 0.31.2 and GFM extension spec examples (`SpecConformanceTest`), streaming equivalence (`StreamingDifferentialTest`, and the JVM-only seeded `StreamingFuzzTest`), pathological input, and Compose UI behavior and recomposition (`MarkdownUiTest`, JVM).

## Non-goals

- editing in the middle of the source;
- raw HTML parsing or HTML output;
- parser generators;
- changing the public API for benchmark results.
