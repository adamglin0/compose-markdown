# ADR-0002: Use A Staged Order Of Block First, Inline Later, Incremental Last

- Status: Accepted
- Date: 2026-03-17

## Context

The repository needs a parser that is correct enough to evolve, incremental enough to support streaming, and maintainable enough to benchmark and extend.

Trying to build full parsing and incremental reuse at the same time would mix three sources of complexity:

- source model correctness,
- Markdown syntax correctness,
- invalidation and cache reuse correctness.

## Decision

Implementation will proceed in the following order:

1. establish the append-only source model and immutable public model,
2. implement block parsing,
3. implement inline parsing on top of stable block payloads,
4. add append-only incremental reuse and cache invalidation,
5. build renderer layers after parser outputs are stable.

This is summarized as block-first, inline-later, incremental-last within the parsing stack.

## Consequences

Positive:

- each stage has a narrow correctness target,
- tests can isolate failures by layer,
- parser outputs stabilize before UI concerns depend on them,
- incremental logic can be measured against an already working non-incremental baseline.

Trade-offs:

- early stages are not end-to-end feature complete,
- some refactoring is still expected when incremental reuse is introduced,
- renderer work starts later than in UI-first prototypes.

## Rejected Alternative

Building an all-at-once incremental parser and renderer from day one was rejected because it increases the risk of hidden coupling and makes correctness debugging much harder.

## Follow-Up

Every stage must stop before beginning the next one, and each stage must leave behind tests and documentation, not just code.
