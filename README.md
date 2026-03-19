# markstream

`markstream` is a Kotlin Multiplatform Markdown parser plus Compose renderer built for append-only streaming output first.

The repository is organized around a small public surface:

- `MarkdownEngine` accepts chunked appends and produces immutable `MarkdownSnapshot` / `ParseDelta` values;
- `markdown-core` owns block parsing, inline parsing, dialect presets, stable IDs, and incremental bookkeeping;
- `markdown-compose` renders snapshots by stable block identity instead of redrawing the whole document;
- `sample-chat` exercises the streaming path with a desktop sample;
- `benchmarks` provides JVM-only parse and append benchmark coverage.

## Final Status

- append-only streaming parsing is implemented and tested;
- three presets ship today: `ChatFast`, `CommonMarkCore`, and `GfmCompat`;
- compatibility and limitation notes are documented in `docs/compatibility-report.md` and `docs/known-limitations.md`;
- a lightweight curated regression suite covers representative CommonMark/GFM cases plus streaming-specific chunk splits;
- raw HTML, arbitrary in-place editing, and full CommonMark parity remain out of scope for this checkpoint.

## Project Tree

```text
.
|- README.md
|- docs/
|  |- architecture.md
|  |- compatibility-report.md
|  |- dialect-matrix.md
|  |- known-limitations.md
|  |- next-steps.md
|  |- performance-notes.md
|  `- ...
|- markdown-core/
|  |- src/commonMain/kotlin/dev/markstream/core/
|  |  |- api/
|  |  |- block/
|  |  |- dialect/
|  |  |- engine/
|  |  |- inline/
|  |  `- model/
|  `- src/commonTest/kotlin/dev/markstream/core/
|- markdown-compose/
|  |- src/commonMain/kotlin/dev/markstream/compose/
|  `- src/commonTest/kotlin/dev/markstream/compose/
|- sample-chat/
|  |- src/commonMain/kotlin/dev/markstream/sample/chat/
|  `- src/desktopMain/kotlin/dev/markstream/sample/chat/
`- benchmarks/
   `- src/main/kotlin/dev/markstream/benchmarks/
```

## Build, Test, Run

Build everything:

```bash
./gradlew build
```

Run tests:

```bash
./gradlew test
```

Run the sample app:

```bash
./gradlew :sample-chat:run
```

Run the sample task graph without launching UI:

```bash
./gradlew :sample-chat:run --dry-run
```

Run the benchmark suite:

```bash
./gradlew :benchmarks:run
```

Run the lightweight benchmark smoke pass:

```bash
./gradlew benchmarkSmoke
```

## Core API

```kotlin
import dev.markstream.core.api.MarkdownEngine
import dev.markstream.core.dialect.MarkdownDialect

val engine = MarkdownEngine(dialect = MarkdownDialect.ChatFast)
val delta = engine.append("Hello **streaming** markdown")
val snapshot = delta.snapshot
engine.finish()
```

`append()` and `finish()` always return a renderable snapshot. Mutable parser internals stay inside `markdown-core`.

## Compose Example

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.markstream.compose.Markdown
import dev.markstream.core.api.MarkdownEngine

@Composable
fun Message(content: String) {
    val engine = remember { MarkdownEngine() }
    engine.reset()
    val snapshot = engine.append(content).snapshot
    Markdown(snapshot = snapshot)
}
```

## Streaming Model

```kotlin
import dev.markstream.core.api.MarkdownEngine

val engine = MarkdownEngine()
for (chunk in listOf("# Hel", "lo\n\n", "- item", " one\n")) {
    val delta = engine.append(chunk)
    println("changed=${delta.changedBlocks.size} preserved=${delta.stats.preservedBlocks}")
}
engine.finish()
```

The engine keeps a stable prefix, reparses only the mutable tail, and reports preserved vs reparsed work through `ParseStats`.

## Presets

| Preset | Best fit | Highlights | Main trade-offs |
| --- | --- | --- | --- |
| `ChatFast` | chat / LLM output | tables, task lists, strikethrough, append-first defaults | reference links are intentionally off |
| `CommonMarkCore` | compatibility-oriented plain Markdown | reference links and definitions enabled, GitHub block extras off | no tables or task lists |
| `GfmCompat` | README-like documents | tables, task lists, strikethrough, reference links | still a pragmatic subset, not full GitHub parity |

See `docs/dialect-matrix.md` for the preset switch table and `docs/compatibility-report.md` for the final support audit.

## Support Snapshot

### ChatFast

| Area | Status | Notes |
| --- | --- | --- |
| Core blocks | Supported | paragraphs, headings, quotes, lists, fences, thematic breaks, tables |
| Common inline syntax | Supported | emphasis, strong, code, links, autolinks, strikethrough, images |
| Reference links | Not supported | left disabled by preset design |
| Streaming path | Supported | curated regression covers text, quote, list, fence, table |

### CommonMarkCore

| Area | Status | Notes |
| --- | --- | --- |
| Core CommonMark-style blocks | Supported | paragraphs, headings, quotes, lists, fences, thematic breaks |
| Reference links / definitions | Supported | one-line definitions only in this checkpoint |
| GFM extras | Not supported | tables, task lists, strikethrough stay off |
| Streaming path | Supported | curated regression covers text, quote, list, fence, reference links |

### GfmCompat

| Area | Status | Notes |
| --- | --- | --- |
| Core blocks + GFM extras | Supported | CommonMark-style blocks plus tables and task lists |
| Inline extras | Supported | strikethrough and reference links enabled |
| Renderer fidelity | Partial | readable fallback table/task rendering, not pixel-parity with GitHub |
| Streaming path | Supported | curated regression covers text, quote, list, fence, table, reference links |

## Fit And Non-goals

Good fit:

- append-only chat and assistant output;
- streaming previews that benefit from stable block IDs;
- Compose apps that want a small parser surface plus readable Markdown rendering.

Not a fit:

- arbitrary in-place Markdown editing;
- raw HTML rendering or HTML export;
- full CommonMark/GFM conformance work where every edge case must match spec output exactly.

## Regression Coverage

- unit tests cover model invariants, block parsing, inline parsing, dialect differences, incremental stats, renderer state updates, and JVM platform wiring;
- `markdown-core/src/commonTest/kotlin/dev/markstream/core/api/CompatibilityRegressionTest.kt` runs curated CommonMark 0.31.2 and GFM 0.29-gfm representative cases;
- the same regression suite includes streaming-specific chunk splits for plain text, quote, list, fenced code, table, and reference-link paths.

## Key Docs

- `docs/architecture.md`
- `docs/compatibility-report.md`
- `docs/known-limitations.md`
- `docs/next-steps.md`
- `docs/dialect-matrix.md`
- `docs/performance-notes.md`
- `docs/release-plan.md`
