# ADR-0001: Start With ChatFast Before Full CommonMark

- Status: Accepted
- Date: 2026-03-17

## Context

The project target is an append-only Markdown engine for streaming chat output. Full CommonMark compatibility contains features with subtle cross-line and cross-block interactions that increase implementation cost, invalidation cost, and debugging surface.

Starting with the entire CommonMark surface would delay usable streaming behavior and blur the architectural priorities of the repository.

## Decision

The first shipped dialect will be `ChatFast`, a deliberately bounded subset that covers the syntax most common in LLM and chat output:

- paragraph
- ATX headings
- fenced code blocks
- block quotes
- lists
- inline code
- emphasis and strong
- inline links and autolinks
- strikethrough
- hard and soft breaks

Features with wider backward dependencies or lower chat value are deferred, including raw HTML, reference-style links, setext headings, tables, and footnotes.

## Consequences

Positive:

- the first implementation remains aligned with the primary user experience,
- append-only incremental behavior can be proven earlier,
- syntax fallback behavior can be designed intentionally,
- compatibility expansion becomes additive instead of speculative.

Trade-offs:

- the project will not claim early full CommonMark compatibility,
- some documents will initially parse more conservatively than established Markdown tools,
- later dialect expansion must remain disciplined and documented.

## Follow-Up

`CommonMarkCore` and `GfmCompat` remain explicit roadmap targets, but only after `ChatFast` v0 and append-only reuse behavior are solid.
