# Public API Draft

This document now reflects the Stage 5 append-only incremental checkpoint. Names may still receive small adjustments later, but the boundary and object roles should remain stable.

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
    val dialect: MarkdownDialect

    fun append(chunk: String): ParseDelta
    fun finish(): ParseDelta
    fun snapshot(): MarkdownSnapshot
    fun reset()
}

data class MarkdownSnapshot(
    val version: Long,
    val dialect: MarkdownDialect,
    val document: MarkdownDocument,
    val stablePrefixRange: TextRange,
    val dirtyRegion: TextRange,
    val isFinal: Boolean,
)

data class MarkdownDocument(
    val sourceLength: Int,
    val lineCount: Int,
    val blocks: List<BlockNode>,
)

data class ParseDelta(
    val version: Long,
    val changedBlocks: List<BlockChange>,
    val insertedBlockIds: List<BlockId>,
    val updatedBlockIds: List<BlockId>,
    val removedBlockIds: List<BlockId>,
    val stablePrefixRange: TextRange,
    val dirtyRegion: TextRange,
    val snapshot: MarkdownSnapshot,
    val stats: ParseStats,
    val hasStateChange: Boolean,
)
```

## Public Concepts

### `MarkdownEngine`

- `append(chunk: String)`: appends new source text and returns the block-level delta.
- `finish()`: signals end-of-input so open constructs can close deterministically.
- `append()` is invalid after `finish()` until `reset()` is called or a new engine is created.
- `snapshot()`: returns the latest immutable snapshot without mutating parser state.
- `reset()`: clears all source, caches, and parser state.

### `MarkdownSnapshot`

Represents the latest displayable state. It contains:

- a monotonically increasing version,
- the active dialect,
- the immutable document,
- the current stable prefix boundary,
- the current dirty region,
- whether the stream is final.

### `MarkdownDocument`

Represents the parsed document model at one version. It is immutable and range-based.

### `ParseDelta`

Describes what changed after one append or finish operation:

- updated or inserted blocks,
- explicit inserted and updated block ID sets,
- removed block IDs,
- the dirty region used by incremental reparsing,
- whether the operation changed externally observable engine state even when block diffs stayed empty,
- the post-update snapshot.

## Additional Public Types

The following types are expected to be public in some form:

```kotlin
@JvmInline
value class BlockId(val raw: Long)

@JvmInline
value class InlineId(val raw: Long)

data class TextRange(
    val start: Int,
    val endExclusive: Int,
)

data class LineRange(
    val startLine: Int,
    val endLineExclusive: Int,
)

data class BlockChange(
    val id: BlockId,
    val oldIndex: Int?,
    val newIndex: Int,
    val block: BlockNode,
)

data class ParseStats(
    val parsedBlockCount: Int,
    val changedBlockCount: Int,
    val reusedBlockCount: Int,
    val inlineParsedBlockCount: Int,
    val inlineCacheHitBlockCount: Int,
    val appendedChars: Int,
    val processedLines: Int,
    val reparsedBlocks: Int,
    val preservedBlocks: Int,
    val fallbackCount: Int,
    val fallbackReason: String?,
)
```

`BlockNode` currently covers: `Document`, `Paragraph`, `Heading`, `FencedCodeBlock`, `BlockQuote`, `ListBlock`, `ListItem`, `ThematicBreak`, `UnsupportedBlock`, and `RawTextBlock`.

`InlineNode` currently exposes the planned public node set: `Text`, `Emphasis`, `Strong`, `CodeSpan`, `Link`, `SoftBreak`, `HardBreak`, `Strikethrough`, and `UnsupportedInline`.

## API Invariants

- `version` increases after every successful state mutation.
- `snapshot()` is always safe to call after any `append()`.
- `stablePrefixRange` never moves backward unless `reset()` is called.
- `append()` after `finish()` fails with a clear error until `reset()` is called or a new engine is created.
- block IDs stay stable while a block's identity is preserved across reparses.
- `hasStateChange` is true whenever the returned snapshot differs from the previous snapshot, including `finish()` transitions that only change `isFinal` or `stablePrefixRange`.
- `dirtyRegion` covers the earliest source offset the current engine actually reparsed; in the normal append path this is the mutable-tail boundary rather than `0`.
- snapshots are safe for UI read access because they expose immutable values only.

## Stage 5 Incremental Metrics

- `ParseDelta.changedBlocks` is reported at the top-level block list only; nested children are reflected through the replacement block payloads.
- `insertedBlockIds` and `updatedBlockIds` are derived from the top-level delta classification.
- `ParseStats.parsedBlockCount` / `reparsedBlocks` count reparsed top-level tail blocks for the current update.
- `ParseStats.reusedBlockCount` / `preservedBlocks` count top-level blocks preserved from block cache.
- `ParseStats.inlineParsedBlockCount` and `inlineCacheHitBlockCount` are counted on text-bearing reparsed blocks (`Paragraph`/`Heading`) during inline hydration.
- `fallbackCount` and `fallbackReason` remain reserved diagnostic fields; the current Stage 5 engine keeps them at `0` / `null`.

## Deferred Surface Area

Not part of the initial public API:

- arbitrary text replacement APIs,
- HTML renderer APIs,
- direct cache inspection,
- mutable AST nodes,
- reference-link resolution hooks,
- editor caret or selection models.

## Stage 5 Stop Point

Stage 5 freezes the append-only incremental contract: stable prefix reuse, mutable-tail reparsing, newline normalization before parsing, and explicit dirty-region reporting are now part of the expected engine behavior.
