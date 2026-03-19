# Append-Only Incremental Model

This document defines the mental model used by `MarkdownEngine`. The engine is optimized for appended text, not arbitrary edits.

## Core Terms

### Stable Prefix

The longest prefix of the normalized source buffer whose interpretation is frozen for the current snapshot.

- never reparsed for the current update;
- safe to reuse from the block cache;
- grows when syntax closes cleanly;
- may stop before the physical end when the tail is still syntactically open.

### Mutable Tail

The suffix after the stable prefix.

Typical reasons a tail stays mutable:

- an unfinished paragraph without a trailing newline;
- an open fenced code block;
- list or quote continuation that may still absorb future lines;
- features with retroactive interpretation such as setext headings or pipe tables.

### Dirty Region

The smallest safe source range that must be reparsed for the latest append.

- ordinary appends start at the previous mutable-tail boundary;
- quote/list/table/setext logic may widen the start backward to a safe block boundary;
- late reference definitions can rehydrate preserved blocks without reparsing the entire document.

### Block Cache

Cached top-level block records from the previous snapshot.

Each record retains:

- block identity;
- range and line range;
- unresolved reference labels;
- stable/provisional status.

### Inline Cache

Per-block inline parse results keyed by:

- block ID;
- text range;
- literal hash;
- reference-definition revision.

## Incremental Flow

For every `append(chunk)`:

1. normalize `\r` / `\r\n` into `\n` before storing or indexing;
2. append the normalized chunk into `SourceBuffer` and `LineIndex`;
3. derive the dirty start from the previous stable-prefix / mutable-tail state;
4. preserve cached blocks before that boundary;
5. reparse only the dirty range with `BlockParser(range = dirtyRegion)`;
6. resolve inline content only for reparsed or dependency-invalidated blocks;
7. compute a new stable prefix;
8. emit a new immutable `MarkdownSnapshot` and `ParseDelta`.

## Stable ID Rules

- unchanged blocks keep the same `BlockId`;
- reparsed blocks try to reuse IDs through structural identity keys;
- unaffected prefix blocks never receive fresh IDs during append-only updates;
- split/merge scenarios allocate new IDs only inside the affected region.

## Dependency-Driven Reprocessing

Two feature groups can reinterpret earlier content:

- block-level retroactive cases: setext headings, tables, list/quote continuation;
- reference-definition cases: a later definition can resolve earlier unresolved inline references.

The current engine handles both explicitly and locally instead of falling back to whole-document reparsing.

## Stage 8 Notes

- benchmark reporting now aggregates `ParseStats.reparsedBlocks` and `ParseStats.preservedBlocks` to track reuse quality;
- source-tail inspection no longer requires extra whole-buffer `snapshot()` copies for newline checks;
- block-tree flattening was rewritten to avoid repeated recursive list creation during delta classification and cache refresh.

## Non-Goals

- arbitrary insertion or deletion inside the source;
- editor-grade token streaming for every character;
- HTML DOM diffing;
- hidden full rescans as a default recovery path.
