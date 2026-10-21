# Performance Notes

The JVM benchmark runner in `benchmarks` measures one-shot parsing and streaming appends with
`MarkdownDialect.GfmCompat`.

## Benchmark Command

```bash
./gradlew :benchmarks:run
./gradlew :benchmarks:run --args="--smoke"
```

## Scenarios

| Scenario | Coverage |
| --- | --- |
| `one-shot-large-paragraph` | large paragraph-heavy document parsed in a single append |
| `chunked-mixed-append` | 240 appends of about 90 characters over paragraph/list/quote/code/table content |
| `one-shot-mixed-document` | mixed block document parsed in a single append |
| `token-stream-{25,50,100,200}kb` | mixed document streamed in 4-10 character chunks, the size of LLM tokens |
| `token-stream-{open-fence,long-list,long-paragraph,long-quote}-100kb` | one open 100KB top-level block streamed in 4-10 character chunks |

Smoke mode (used by `ciCheck`) runs the first three scenarios with smaller inputs and only `token-stream-25kb`.

## Metric Definitions

- `append total ms`: time of all `append()` calls of one run, without `finish()`.
- `avg append us`: `append total ms / appendCount`.
- `finish ms`: time of the single `finish()` call.
- `total ms`: `append total ms + finish ms`.
- `built blocks per call`: `ParseStats.builtBlocks` averaged over all calls: blocks built instead of taken from
  the cache of closed blocks.

## Latest Measured Results

Full mode (3 warmup and 8 measured iterations), Apple Silicon laptop, JDK 17.

| Scenario | Appends | Append total ms | Avg append us | Total ms | Built blocks per call | Alloc bytes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `one-shot-large-paragraph` | 1 | 3.310 | 3310.422 | 3.318 | 250.0 | 3124115 |
| `chunked-mixed-append` | 240 | 3.726 | 15.523 | 3.728 | 5.7 | 3722780 |
| `one-shot-mixed-document` | 1 | 0.876 | 875.943 | 0.877 | 320.0 | 1643432 |
| `token-stream-25kb` | 3594 | 8.240 | 2.293 | 8.246 | 2.3 | 20937828 |
| `token-stream-50kb` | 7148 | 10.101 | 1.413 | 10.108 | 2.3 | 41120314 |
| `token-stream-100kb` | 14316 | 19.603 | 1.369 | 19.610 | 2.3 | 81273984 |
| `token-stream-200kb` | 28658 | 29.977 | 1.046 | 29.983 | 2.3 | 162606856 |
| `token-stream-open-fence-100kb` | 14319 | 19.947 | 1.393 | 19.959 | 1.0 | 749754856 |
| `token-stream-long-list-100kb` | 14316 | 14.630 | 1.022 | 14.854 | 5.3 | 97566784 |
| `token-stream-long-paragraph-100kb` | 14319 | 30.457 | 2.127 | 30.470 | 1.0 | 878704744 |
| `token-stream-long-quote-100kb` | 14319 | 27.689 | 1.934 | 27.703 | 2.0 | 860536192 |

Streaming costs time proportional to the new text, whether the document is made of many blocks or of one huge
open block: every line is parsed once by the resumable block parser, closed blocks are built once, an open
container reuses the nodes of its closed children, and a growing paragraph is inline-parsed again only from its
last final line start. The allocation of the open-fence and long-paragraph runs is the copy of the open leaf's
text into its new node on every append (an immutable `String`), a memory copy rather than parsing work.

## Compose Streaming Cost

`MarkdownStreamingCostTest` (`./gradlew :markdown-compose:jvmTest`) streams 4-character chunks into a chat-like
`GfmMath` document (headings, paragraphs, lists, formulas, quotes) through `MarkdownState` and through both
renderers, counting composed `MarkdownBlock` calls with a composition tracer. Apple Silicon laptop, JDK 21:

| Measure | Before | After |
| --- | ---: | ---: |
| `MarkdownState.append` into a 25KB document (850 blocks) | 52 us | 16 us |
| `MarkdownState.append` into a 200KB document (6590 blocks) | 276 us | 17 us |
| Streaming 200KB through `MarkdownState` (50015 appends) | 8.3 s | 0.10 s |
| Blocks composed per append, either renderer | at most 2 | at most 2 |
| Frame per append, `LazyListScope.markdown`, 25KB / 200KB | 1.8 / 2.0 ms | 1.6 / 2.0 ms |
| Frame per append, `Markdown(document)`, 25KB / 200KB | 4.0 / 18.6 ms | 4.1 / 18.5 ms |

- `MarkdownState` used to rebuild an id map over every top-level block and compare the whole block list on each
  append. It now publishes the engine's snapshot document as is: the engine shares stable blocks between
  snapshots and keeps every unchanged block as the same instance, so an append costs the engine's work on the
  new text.
- Unchanged blocks skip recomposition in both renderers. The lazy entry composes only the visible items whose
  block changed; its frame time does not depend on the document length (the frame times above include the test
  harness).
- Inside a growing container, closed children are the engine's cached nodes, so they keep their instances too:
  streaming items into a 200-item list, alone or inside a quote, composes at most 4 blocks per append.
- `Markdown(document)` is one `Column`, which checks, measures and draws every top-level block on each append,
  so its frame time grows with the number of blocks. Use `LazyListScope.markdown` for long documents.

## History

Same documents (GfmCompat) streamed in 4-10 character chunks, one run each, Apple Silicon laptop:

| Document | Original engine | Exact stable prefix | Resumable block parser | One-shot |
| --- | ---: | ---: | ---: | ---: |
| 25KB mixed | 3303 ms | 31 ms | 8 ms | 3 ms |
| 100KB mixed | 17806 ms | 84 ms | 20 ms | 10 ms |
| 200KB mixed | 75120 ms | 110 ms | 30 ms | 17 ms |
| 100KB unclosed fence | 1788 ms | 1711 ms | 20 ms | <1 ms |
| 100KB single list | 37786 ms | 8830 ms | 17 ms | 4 ms |
| 100KB single paragraph | 5144 ms | 8590 ms | 34 ms | 2 ms |
| 100KB single block quote | - | 7486 ms | 29 ms | 1 ms |
| 100KB single table | - | 11846 ms | 18 ms | 3 ms |
| 100KB nested list | - | 12254 ms | 19 ms | 3 ms |

- The original engine re-parsed from an inexact boundary and rebuilt document-wide state on every append
  (flattened trees, an id lookup of every block, the dependency index, a whole-source copy and a deep snapshot
  comparison).
- With an exact stable prefix, each append re-parsed the open top-level block from its start, so one huge open
  block still cost time proportional to its size per append.
- The resumable block parser parses each line once and parses the unterminated last line in a fork that copies
  only the open path.

## Remaining Hotspots

- Each append copies the text of the open code block or paragraph into its node (see above).
- A paragraph with an emphasis opener, bracket or code span that never closes is inline-parsed again from that
  point on every append.
