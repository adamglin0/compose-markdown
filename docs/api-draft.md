# Public API Draft

This document freezes the intended public surface before implementation starts. Names can still receive small adjustments during Stage 1, but the boundary and object roles should remain stable.

## Design Rules

- Public objects are immutable.
- Internal parser state stays internal.
- Public structures use source ranges and stable IDs.
- `append()` always tries to return a displayable snapshot, even if the tail is still syntactically incomplete.
- Deltas are block-oriented so UI can update incrementally.

## Draft API

```kotlin
package dev.markstream.core.api

interface MarkdownEngine {
    fun append(chunk: String): ParseDelta
    fun finish(): ParseDelta
    fun snapshot(): MarkdownSnapshot
    fun reset()
}

data class MarkdownSnapshot(
    val version: Long,
    val document: MarkdownDocument,
    val stablePrefixEnd: Int,
    val dirtyRegion: TextRange,
    val isFinal: Boolean,
)

data class MarkdownDocument(
    val sourceLength: Int,
    val blocks: List<BlockNode>,
)

data class ParseDelta(
    val version: Long,
    val changedBlocks: List<BlockChange>,
    val removedBlockIds: List<BlockId>,
    val stablePrefixEnd: Int,
    val dirtyRegion: TextRange,
    val snapshot: MarkdownSnapshot,
)
```

## Public Concepts

### `MarkdownEngine`

- `append(chunk: String)`: appends new source text and returns the block-level delta.
- `finish()`: signals end-of-input so open constructs can close deterministically.
- `snapshot()`: returns the latest immutable snapshot without mutating parser state.
- `reset()`: clears all source, caches, and parser state.

### `MarkdownSnapshot`

Represents the latest displayable state. It contains:

- a monotonically increasing version,
- the immutable document,
- the current stable prefix boundary,
- the current dirty region,
- whether the stream is final.

### `MarkdownDocument`

Represents the parsed document model at one version. It is immutable and range-based.

### `ParseDelta`

Describes what changed after one append or finish operation:

- updated or inserted blocks,
- removed block IDs,
- the dirty region used by incremental reparsing,
- the post-update snapshot.

## Additional Public Types

The following types are expected to be public in some form:

```kotlin
@JvmInline
value class BlockId(val raw: Long)

data class TextRange(
    val start: Int,
    val endExclusive: Int,
)

data class BlockChange(
    val id: BlockId,
    val newIndex: Int,
    val block: BlockNode,
)
```

`BlockNode` and inline node shapes remain intentionally narrow in Stage 0. They will be specified more concretely when Stage 1 establishes the immutable core model.

## API Invariants

- `version` increases after every successful state mutation.
- `snapshot()` is always safe to call after any `append()`.
- `stablePrefixEnd` never moves backward unless `reset()` is called.
- block IDs stay stable while a block's identity is preserved across reparses.
- `dirtyRegion` is the smallest known invalidation window for the current dialect and feature set.

## Deferred Surface Area

Not part of the initial public API:

- arbitrary text replacement APIs,
- HTML renderer APIs,
- direct cache inspection,
- mutable AST nodes,
- reference-link resolution hooks,
- editor caret or selection models.

## Stage 0 Stop Point

This draft intentionally avoids finalizing every node subtype. Stage 1 may refine node names, but it must keep the engine-centered immutable boundary intact.
