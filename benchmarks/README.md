# benchmarks

This module contains the JVM benchmark runner used for local parse and append-path measurement.

## Commands

```bash
./gradlew :benchmarks:run
./gradlew :benchmarks:run --args="--smoke"
./gradlew :benchmarks:run --args="--hold-unsettled-lines"
```

`--hold-unsettled-lines` creates every engine with `holdUnsettledLines = true`.

The runner covers:

- one-shot large-document parsing
- 120/240 small-append streaming runs
- documents of 25-200KB streamed in 4-10 character chunks (LLM token size), and one open 100KB fence, list, paragraph, block quote and table
- mixed block content with paragraphs, lists, quotes, fenced code blocks, and tables

Results are printed as a Markdown table and can also be written to a file with:

```bash
./gradlew :benchmarks:run --args="--output=benchmarks/results/latest.md"
```

Canonical notes live in `../docs/performance-notes.md`.
