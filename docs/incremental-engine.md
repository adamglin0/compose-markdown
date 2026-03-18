# Incremental Engine Notes

This document records the Stage 5 append-only engine behavior as implemented in `markdown-core`.

## Working State Model

- `stable prefix`: cached top-level blocks whose interpretation is frozen for the current snapshot.
- `mutable tail`: the suffix starting at `snapshot.stablePrefixEnd`.
- `dirty region`: the actual source range reparsed for the latest update.
- `block cache`: cached top-level block records reused before the dirty start.
- `inline cache`: per-block inline payload cache keyed by block ID plus text hash.

## Append Path

For a normal `append(chunk)`:

1. append text into `SourceBuffer`;
2. normalize chunk newlines to `\n` before they reach `SourceBuffer`, `LineIndex`, or `BlockParser` (including CRLF pairs split across append boundaries);
3. append newline offsets into `LineIndex` only for the normalized chunk;
4. derive `dirtyRegion.start` from the previous mutable tail boundary;
5. preserve cached blocks whose `range.endExclusive <= dirtyRegion.start`;
6. reparse only `dirtyRegion` with `BlockParser(range = dirtyRegion)`;
7. rehydrate inline nodes only for reparsed blocks, with inline cache reuse when block ID and text are unchanged;
8. publish a new immutable `MarkdownSnapshot` and `ParseDelta`.

The engine does not default to whole-document reparsing on ordinary append traffic.

## Stable Prefix Rules

- Open containers (`fence`, `list`, `quote`, trailing paragraph) pull the stable-prefix boundary back to the earliest open frame.
- If no open frame exists but the source does not end in `\n`, the final physical line remains mutable and is reparsed on the next append.
- `finish()` promotes the stable prefix to the full source length without exposing parser cursor state publicly.

## Cache Behavior

### Block Cache

- stores cached top-level block records from the last snapshot;
- is used to preserve unchanged prefix blocks without reparsing them;
- is also used as the source of stable block IDs for reparsed tail blocks.

### Inline Cache

- stores parsed inline children by block ID;
- is reused when a reparsed block keeps the same ID and the same literal text;
- is pruned when blocks disappear.

## Newline Semantics

- the engine stores normalized source text only, with `\r` and `\r\n` collapsed to `\n` before indexing or parsing;
- a chunk ending in `\r` is normalized immediately and suppresses one leading `\n` on the next append so split CRLF input still becomes exactly one logical newline;
- block ranges, line ranges, and `lineCount` therefore always describe the normalized source, not the raw transport bytes.

## Bookkeeping Cost

- Stage 5 localizes block reparsing to the mutable tail, but it still rebuilds the immutable snapshot, delta classification, and cache tables in O(n) over the current top-level block list;
- `ParseStats` is a diagnostics surface, not a guarantee that every internal step is sublinear.

## Delta And Stats

`ParseDelta` now reports:

- `insertedBlockIds`
- `updatedBlockIds`
- `removedBlockIds`

`ParseStats` now reports:

- `appendedChars`
- `processedLines`
- `reparsedBlocks`
- `preservedBlocks`
- `fallbackCount`
- `fallbackReason`

`fallbackCount` and `fallbackReason` remain reserved diagnostic fields; the current Stage 5 implementation does not use a full-reparse fallback path after newline normalization.

These metrics are intended for diagnostics and for the sample debug surface, not as a hard performance API.
