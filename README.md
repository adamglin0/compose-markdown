# markstream

`markstream` is a Kotlin Multiplatform Markdown parser and Compose renderer built for append-only streaming output first.

The project keeps the public API small: callers append text through `MarkdownEngine`, observe immutable `MarkdownSnapshot` / `ParseDelta`, and render blocks through `markdown-compose`. Stage 8 adds a real JVM benchmark runner, performance notes, lightweight CI, and release planning.

## Project Goals

- parse Markdown in `commonMain` and keep platform-specific code minimal;
- preserve stable block identity so Compose can update by block instead of repainting the whole document;
- optimize for append-only chat / LLM output rather than arbitrary in-place editing;
- support multiple dialect presets without changing the engine API;
- measure before tuning and document the trade-offs.

## Modules

- `markdown-core`: source buffer, line index, block parser, inline parser, incremental engine, immutable document model;
- `markdown-compose`: Compose Multiplatform renderer driven by block snapshots and stable IDs;
- `sample-chat`: desktop sample that streams chunks into the engine and shows snapshot/debug output;
- `benchmarks`: JVM benchmark runner for one-shot parse and append-heavy scenarios;
- `docs`: architecture, incremental model, dialect matrix, limits, performance notes, ADRs, release planning.

## Quick Start

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

Run the benchmark suite:

```bash
./gradlew :benchmarks:run
```

Run a lightweight benchmark smoke pass:

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

`append()` and `finish()` always return a renderable snapshot. The public API does not expose mutable parser internals.

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

## Streaming Example

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

## Dialects

- `ChatFast`: default preset for streaming chat; tables, task lists, setext headings, and common inline syntax enabled; reference links stay off by default;
- `CommonMarkCore`: narrower compatibility preset with reference links/definitions enabled and GitHub-specific blocks disabled;
- `GfmCompat`: compatibility-oriented preset with tables, task lists, and strikethrough enabled.

See `docs/dialect-matrix.md` for the full matrix and invalidation rules.

## Performance Workflow

Stage 8 performance work follows a strict loop:

1. measure with `:benchmarks:run`;
2. inspect `ParseStats` plus JVM allocation counters;
3. optimize obvious hot paths only;
4. record baseline vs optimized results in `docs/performance-notes.md`.

## Current Limits

- append-only editing only; arbitrary middle-of-document edits are out of scope for this checkpoint;
- raw HTML is disabled for all presets;
- delimiter handling targets common chat / documentation cases first, not full CommonMark parity;
- tables support common pipe-table syntax only;
- image parsing exists, but Compose rendering stays intentionally lightweight.

See `docs/known-limitations.md` for the fuller list.

## Release Planning

- Gradle `group`: `dev.markstream`
- planned artifacts: `markstream-core`, `markstream-compose`, `markstream-benchmarks` is internal-only
- current versioning scheme: `0.y.z` while parser semantics and renderer surface are still evolving

Publishing notes and TODOs live in `docs/release-plan.md`.

## Key Docs

- `docs/architecture.md`
- `docs/incremental-model.md`
- `docs/dialect-matrix.md`
- `docs/known-limitations.md`
- `docs/performance-notes.md`
- `docs/release-plan.md`
