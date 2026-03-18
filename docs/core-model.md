# Core Model Invariants

This document freezes the Stage 2 core model boundary in `markdown-core`.

## Public Surface

- `TextRange` and `LineRange` are immutable half-open ranges.
- `BlockId` is required on every public block node.
- `InlineId` is available for inline nodes, but only block IDs currently carry a stability contract.
- `MarkdownDocument`, `MarkdownSnapshot`, `ParseDelta`, and node trees are immutable value objects.
- `MarkdownEngine` stays append-only: `append`, `finish`, `snapshot`, and `reset` define the stable control surface.

## Range Rules

- `TextRange(start, endExclusive)` is half-open: `start` is included and `endExclusive` is excluded.
- `LineRange(startLine, endLineExclusive)` uses the same half-open rule.
- Empty ranges are valid when `start == endExclusive`.
- Snapshot ranges always stay within `document.sourceLength`.

## Stable Block ID Rules

- A block ID must stay stable when append-only reparsing preserves the same block identity.
- In the placeholder engine, identity is anchored to block kind plus source start offset and a small discriminator.
- If a block changes kind, splits, merges, or otherwise loses identity, it may receive a new ID.
- `reset()` clears all stability guarantees because the source buffer is replaced.

## Snapshot Safety

- `MarkdownSnapshot` is safe to hand to UI readers because it only exposes immutable public data.
- A later `append()` produces a new snapshot object; previously returned snapshots remain unchanged.
- The current guarantee is read safety, not concurrent mutation of the engine itself. `MarkdownEngine` is still a single-writer abstraction.

## Append-Only Engine Model

- `append(chunk)` only appends source text to the tail.
- `finish()` closes the current append session and marks the entire source as stable.
- `stablePrefixRange` tracks the prefix that the current engine implementation considers reusable without reinterpretation.
- `ParseDelta.hasStateChange` marks snapshot-level mutations that remain observable even when block diffs are empty.
- `dirtyRegion` marks the earliest source offset the engine actually reparsed for the latest operation; the current placeholder engine reparses the whole document, so non-no-op rebuilds report the full `0..sourceLength` range.

## Placeholder Delta Metrics

- `changedBlocks` is top-level only; nested structure changes are represented through the changed parent block payload.
- `ParseStats` currently counts parsed, changed, and reused blocks at that same top-level granularity.

## Internal Reserved State

The internal package keeps mutable placeholders for future incremental parsing work:

- open block stack,
- line index,
- block cache / ID reuse state,
- append-only source buffer.

These types are intentionally not part of the public API so a real parser can replace the placeholder implementation without breaking consumers.
