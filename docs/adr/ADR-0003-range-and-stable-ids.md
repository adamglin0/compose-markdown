# ADR-0003: Use Source Ranges And Stable Block IDs In The Public Model

- Status: Accepted
- Date: 2026-03-17

## Context

The project target is incremental rendering under append-only updates. Repeated substring extraction and whole-document replacement would create unnecessary allocation, make reparsing more expensive, and force UI churn.

Consumers also need a stable way to identify blocks across updates so Compose rendering can update only the blocks that changed.

## Decision

The public core model will use:

- source ranges instead of storing duplicated text everywhere,
- stable block IDs for block-level identity across reparses,
- immutable snapshots and deltas that expose changed blocks directly.

Public APIs will center on document snapshots, block IDs, text ranges, and parse deltas rather than mutable parser internals.

## Consequences

Positive:

- fewer avoidable string copies,
- better fit for append-only reparsing,
- block-level UI updates become straightforward,
- benchmarks can measure reuse explicitly.

Trade-offs:

- consumers that want extracted text will sometimes need helper APIs,
- range correctness becomes a critical invariant,
- stable-ID reuse rules must be clearly documented and tested.

## Rejected Alternative

An API centered on fully materialized block strings and whole-document display text was rejected because it hides source identity, complicates incremental reuse, and encourages whole-document replacement in the renderer.

## Follow-Up

Stage 1 must define the concrete range and ID types and add invariant tests around them.
