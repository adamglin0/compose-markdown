# Core Model Invariants

This document tracks the Stage 7 core model boundary in `markdown-core`.

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
- In the current block parser, identity is anchored to block kind plus source start offset and a small discriminator.
- If a block changes kind, splits, merges, or otherwise loses identity, it may receive a new ID.
- `reset()` clears all stability guarantees because the source buffer is replaced.

## Snapshot Safety

- `MarkdownSnapshot` is safe to hand to UI readers because it only exposes immutable public data.
- A later `append()` produces a new snapshot object; previously returned snapshots remain unchanged.
- The current guarantee is read safety, not concurrent mutation of the engine itself. `MarkdownEngine` is still a single-writer abstraction.

## Append-Only Engine Model

- `append(chunk)` only appends source text to the tail.
- `finish()` closes the current append session and marks the entire source as stable.
- After `finish()`, `append()` is invalid until `reset()` is called or a new engine is created.
- `stablePrefixRange` tracks the prefix that the current engine implementation considers reusable without reinterpretation.
- `ParseDelta.hasStateChange` marks snapshot-level mutations that remain observable even when block diffs are empty.
- `dirtyRegion` marks the earliest source offset the engine actually reparsed for the latest operation.
- Append-only reparsing stays localized to the mutable tail, plus small retroactive windows for setext headings, pipe tables, and quote/list continuation.
- Late reference definitions do not trigger whole-document reparsing; the engine reprocesses only dependent preserved top-level blocks through the dependency index.

## Delta Metrics

- `changedBlocks` is top-level only; nested structure changes are represented through the changed parent block payload.
- `ParseStats` counts parsed, changed, and reused blocks at that same top-level granularity.

## Internal Reserved State

The internal packages keep mutable append-time state for future incremental parsing work:

- open block stack,
- line index,
- block cache / ID reuse state,
- inline cache state,
- append-only source buffer,
- reference-definition dependency index.

## Stage 7 Block And Inline Notes

- `BlockNode` snapshots come from a line-based parser that keeps the same append-only / snapshot / `ParseDelta` boundary.
- Unfinished fenced code blocks may appear in non-final snapshots with `isClosed = false`.
- `finish()` finalizes EOF state, but `isClosed = false` still means the source never emitted an explicit closing fence.
- `BlockNode.Heading` now records `HeadingStyle` so ATX and setext upgrades stay distinguishable.
- `BlockNode.ListItem` can carry nullable `taskState` for task-list syntax.
- `BlockNode.TableBlock`, `BlockNode.TableRow`, and `BlockNode.TableCell` represent pipe tables.
- `InlineNode.Link` and `InlineNode.Image` can record `referenceLabel` when resolved from definitions.
- Reference definitions are intentionally non-rendering; they live in the engine dependency index instead of the public document block list.
- Raw HTML remains disabled for every preset in this stage.

These types are intentionally not part of the public API so a real parser can replace the placeholder implementation without breaking consumers.
