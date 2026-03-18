# Append-Only Incremental Model

This document defines the mental model for incremental parsing. The first-class scenario is a stream of appended text, not arbitrary edits.

## Core Terms

### Stable Prefix

The longest prefix of the source buffer that is known not to change interpretation after the latest append under the current dialect and feature set.

Properties:

- never reparsed during the current update,
- safe to reuse from cached block results,
- grows monotonically during normal append flow,
- may stop before the physical end of the document when the tail is syntactically open.

### Mutable Tail

The suffix after the stable prefix. This is the only region eligible for reparsing after an append.

Typical reasons a tail stays mutable:

- an unclosed fenced code block,
- an unfinished list continuation,
- a block quote sequence still being extended,
- a paragraph whose final inline interpretation may still change at the end.

### Dirty Region

The minimal source range inside the mutable tail that must be reparsed for the latest append.

The dirty region may start earlier than the new chunk if the append can retroactively change the interpretation of recent source.

Examples:

- adding closing backticks for an existing fence,
- continuing a list item that was previously ambiguous,
- appending link destination text inside an unfinished inline link.

### Block Cache

Cache of previously parsed block records keyed by stable block identity and source range.

Each cached record is expected to retain:

- block ID,
- block kind,
- source range,
- structural metadata needed for reparse alignment,
- closed or provisional status.

### Inline Cache

Cache of inline parse results attached to block content ranges and dialect flags.

The inline cache is invalidated when:

- the owning block text changes,
- the owning block type changes,
- a future dependency invalidates that block's inline interpretation.

### Future Dependency Map

A reserved dependency structure for features that may require later input to reinterpret earlier blocks.

Examples include:

- reference-style links,
- setext headings,
- tables with alignment rows,
- footnote definitions.

`ChatFast` v0 avoids these features so the dependency map can remain dormant at first, but the architecture reserves a place for it.

## Incremental Flow

For each `append(chunk)`:

1. Append text to `SourceBuffer`.
2. Extend `LineIndex` only for the new chunk.
3. Identify the earliest reparse boundary inside the mutable tail.
4. Reparse block structure only from that boundary onward.
5. Reuse unaffected cached blocks before the boundary.
6. Reparse inline content only for changed or invalidated blocks.
7. Recompute stable prefix and mutable tail.
8. Emit `ParseDelta` containing block-level changes.

## Stable ID Rules

- unchanged blocks keep their block IDs,
- blocks that move due to reparsing but preserve identity should keep the same ID when safe,
- blocks that split or merge receive new IDs for the affected region only,
- unchanged prefix blocks never receive new IDs during append-only updates.

## Snapshot Rule

Every append must yield a displayable snapshot.

That means:

- unfinished syntax stays representable,
- fenced code blocks may remain provisional before `finish()`,
- incomplete inline delimiters may be shown as plain text until later input resolves them,
- no append should require withholding the whole document from rendering.

## Minimal Invalidation Principle

The engine should invalidate the smallest known safe window, not the full document.

Practical guardrails:

- reparse from a safe block boundary rather than an arbitrary character offset,
- widen backward only when syntax can affect prior interpretation,
- keep feature-specific invalidation logic explicit,
- avoid hidden global rescans.

## Why Block Cache And Inline Cache Are Separate

Block structure and inline structure change at different rates.

- block parsing depends heavily on line layout and container structure,
- inline parsing depends on block-local text,
- caching them separately lets the engine reuse a paragraph block even when only its trailing inline span changed,
- later features can widen inline invalidation without forcing full block invalidation.

## Why Future Dependency Map Exists Early

The project does not implement reference links, setext headings, or tables in `ChatFast` v0, but these features are known to introduce backward or cross-block dependencies.

By reserving a dependency map now, later dialect expansion can remain incremental instead of forcing a redesign.

## Non-Goals Of The Incremental Model

- arbitrary insertion or deletion in the middle of the source,
- editor-grade incremental tokenization,
- HTML DOM diffing,
- global post-processing passes that always revisit the whole document.

## Stage 0 Stop Point

This document freezes the append-only incremental strategy. Implementation details may evolve, but the concepts of stable prefix, mutable tail, dirty region, separate caches, and future dependency tracking are now project invariants.
