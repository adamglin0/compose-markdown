# Performance Notes

Stage 8 introduces a real JVM benchmark runner in `benchmarks` and records measured results for the current incremental parser implementation.

## Benchmark Command

```bash
./gradlew :benchmarks:run
```

Measured on this repository with the built-in runner and `MarkdownDialect.GfmCompat`.

## Scenarios

| Scenario | Coverage |
| --- | --- |
| `one-shot-large-paragraph` | large paragraph-heavy document parsed in a single append |
| `chunked-mixed-append` | 240 small appends over paragraph/list/quote/code/table content |
| `one-shot-mixed-document` | mixed block document parsed in a single append |

## Metric Definitions

- `append total ms`: one scenario中全部 `append()` 调用耗时之和，不含 `finish()`。
- `avg append us`: `append total ms / appendCount`，只反映单次 `append()` 的平均成本。
- `finish ms`: 单次 `finish()` 调用耗时，单独统计，避免掺入 append 平均值。
- `total ms`: `append total ms + finish ms`。

## Latest Measured Results

下表应与当前 benchmark runner 输出口径一致。

| Scenario | Append total ms | Avg append us | Finish ms | Total ms | Alloc bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| `one-shot-large-paragraph` | 12.257 | 12256.844 | 1.094 | 13.350 | 3027493 |
| `chunked-mixed-append` | 87.132 | 363.052 | 0.747 | 87.879 | 155578030 |
| `one-shot-mixed-document` | 1.500 | 1499.521 | 0.458 | 1.957 | 2882048 |

## Preserved vs Reparses

| Scenario | Preserved ratio | Reparsed ratio | Notes |
| --- | ---: | ---: | --- |
| `one-shot-large-paragraph` | 0.500 | 0.500 | ratio aggregates every emitted delta, including the final `finish()` delta |
| `chunked-mixed-append` | 0.974 | 0.026 | ratio aggregates all 240 appends plus the final `finish()` delta |
| `one-shot-mixed-document` | 0.492 | 0.508 | ratio also includes the single `finish()` delta after the one-shot append |

The `chunked-mixed-append` run is the main proof point that the append-only model preserves most previously parsed blocks while new chunks extend the mutable tail.

## Targeted Optimizations Applied

1. `SourceBuffer` now caches `snapshot()` and exposes direct tail inspection helpers so newline checks no longer allocate extra full-source strings.
2. `IncrementalMarkdownEngine.normalizeChunk()` avoids an intermediate substring before CR/CRLF normalization.
3. block-tree flattening now uses an accumulator instead of recursive `buildList { addAll(...) }` chains, reducing temporary list churn during delta classification and cache refresh.
4. reference-label normalization moved to a shared manual normalizer instead of regex `split/join` in both block and inline parsers.
5. table cell splitting now scans the line directly instead of building intermediate `compact`, `raw`, and `split('|')` strings.

## Hotspots Observed

- the biggest remaining allocation hotspot is still append-heavy tree rebuilding around immutable block snapshots and delta bookkeeping;
- substring-heavy paths remain in line slicing and inline literal extraction, but the worst no-op copies were removed in Stage 8;
- the large drop in `chunked-mixed-append` allocated bytes points mainly to the flattening and table-splitting changes;
- one-shot mixed latency stays close to noise-level variance, so Stage 8 should be read mostly as an allocation and append-path cleanup, not a dramatic whole-parser rewrite.

## Remaining Opportunities

- reduce per-line substring creation in `LineIndex.lines()` / `ParserLine` if future benchmarks show line slicing dominating;
- avoid rebuilding unchanged top-level metadata structures when no block order changed;
- add JVM profiling snapshots or async-profiler integration only if the lightweight runner stops being enough.
