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
| `token-stream-open-fence-100kb` | one unclosed 100KB fenced code block streamed in 4-10 character chunks |
| `token-stream-long-list-25kb` | one 25KB bullet list streamed in 4-10 character chunks |

Smoke mode (used by `ciCheck`) runs the first three scenarios with smaller inputs and only `token-stream-25kb`.

## Metric Definitions

- `append total ms`: time of all `append()` calls of one run, without `finish()`.
- `avg append us`: `append total ms / appendCount`.
- `finish ms`: time of the single `finish()` call.
- `total ms`: `append total ms + finish ms`.
- `preserved ratio` / `reparsed ratio`: `ParseStats.preservedBlocks` and `reparsedBlocks` summed over all calls.

## Latest Measured Results

Full mode (3 warmup and 8 measured iterations), Apple Silicon laptop, JDK 17.

| Scenario | Appends | Append total ms | Avg append us | Total ms | Preserved ratio | Alloc bytes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `one-shot-large-paragraph` | 1 | 3.231 | 3230.958 | 3.242 | 0.500 | 3355751 |
| `chunked-mixed-append` | 240 | 4.333 | 18.053 | 4.335 | 0.986 | 4041664 |
| `one-shot-mixed-document` | 1 | 0.965 | 965.245 | 0.970 | 0.500 | 1627857 |
| `token-stream-25kb` | 3594 | 8.326 | 2.317 | 8.331 | 0.994 | 26983111 |
| `token-stream-50kb` | 7148 | 12.559 | 1.757 | 12.565 | 0.997 | 52167938 |
| `token-stream-100kb` | 14316 | 20.767 | 1.451 | 20.775 | 0.999 | 104097640 |
| `token-stream-200kb` | 28658 | 34.906 | 1.218 | 34.914 | 0.999 | 208213240 |
| `token-stream-open-fence-100kb` | 14319 | 1145.956 | 80.030 | 1146.130 | 0.000 | 7762343192 |
| `token-stream-long-list-25kb` | 3595 | 548.530 | 152.581 | 548.832 | 0.000 | 3075576440 |

Streaming a document of ordinary blocks costs time and allocation proportional to its length: each append
re-parses only the mutable tail (the last open top-level block), and snapshots share the stable blocks instead
of copying them.

## Before The Stable Prefix Fix

The previous engine re-parsed from an inexact boundary and rebuilt document-wide state on every append
(flattened trees, an id lookup of every block, the dependency index, a whole-source copy and a deep snapshot
comparison). Same mixed document streamed in 4-10 character chunks, one run each:

| Document | Before | After | One-shot |
| --- | ---: | ---: | ---: |
| 25KB mixed | 3303 ms | 31 ms | 3 ms |
| 50KB mixed | 6679 ms | 45 ms | 6 ms |
| 100KB mixed | 17806 ms | 84 ms | 10 ms |
| 200KB mixed | 75120 ms | 110 ms | 17 ms |
| 100KB unclosed fence | 1788 ms | 1846 ms | <1 ms |
| 100KB single list | 37786 ms | 20377 ms | 6 ms |
| 100KB single paragraph | 5144 ms | 4927 ms | 1 ms |

## Remaining Hotspots

- One huge open top-level block is re-parsed from its start on every append, so its cost per append grows with
  its size: block structure and ids of every list item are rebuilt (inline content of unchanged items comes from
  the cache), fence lines are sliced and joined again, and a single growing paragraph is inline-parsed again.
  Making this incremental needs a resumable block parser session that keeps closed children between appends.
- `MarkdownRendererState.apply` in `markdown-compose` still maps every top-level block on each delta.
