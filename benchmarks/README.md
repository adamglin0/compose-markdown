# benchmarks

This module contains the Stage 8 JVM benchmark runner.

## Commands

```bash
./gradlew :benchmarks:run
./gradlew :benchmarks:run --args="--smoke"
```

The runner covers:

- one-shot large document parsing,
- 120/240 small append streaming runs,
- mixed block content with paragraphs, lists, quotes, fenced code blocks, and tables.

Results are printed as a Markdown table and can also be written to a file with:

```bash
./gradlew :benchmarks:run --args="--output=benchmarks/results/latest.md"
```

Canonical Stage 8 notes live in `docs/performance-notes.md`.
