# Incremental Engine Notes

This document records the append-only engine as implemented in `markdown-core`
(`core/engine/IncrementalMarkdownEngine.kt`). The mental model is in `incremental-model.md`.

## Working State

- `BlockParser`: a resumable block parser. Every complete source line is incorporated into it exactly once;
  it keeps the open blocks (always the rightmost path of the block tree) between calls.
- `stable prefix`: the top-level blocks the parser closed. Closed blocks are final; `StableBlocks` holds their
  nodes, built once.
- `mutable tail`: the open top-level block plus whatever the unterminated last line adds, built on every call
  from a throw-away fork of the parser.
- `ReferenceDefinitions`: the definitions in effect; definitions of closed paragraphs are final, definitions of
  open ones are replaced on every call.
- `InlineCache`: inline parse results of the leaves built in the previous call, keyed by block id and checked
  against the raw text, its range and the definition revision, with a checkpoint to resume a growing paragraph.
- `BlockIdAllocator`: identity keys (kind, start offset, depth, discriminator) of the blocks built in the
  previous call, with their ids.

## Block Parser

`BlockParser` follows the commonmark.js parsing strategy (`block-parser.md`). A line only ever changes the
open blocks; once closed, a block never changes again. Two properties make it resumable:

1. **Lines are incorporated for good.** `incorporate(line)` takes complete lines only. Headings, thematic breaks
   and single-line math are closed right after their line, so every open block can still change.
2. **Forks are cheap and isolated.** `fork()` copies only the open path; children, paragraph lines and table
   rows of a copy are `ForkedList`s over the original, and code text is a `JoinedLines` that a fork appends to
   past the original's length. The engine parses the unterminated last line in the fork and `finish()`es it
   there, as if the input ended; the original never sees that line until it is complete.

`takeClosedBlocks()` hands out the top-level blocks that closed and forgets them, so the parser holds only the
open top-level block. `takeDefinitions()` hands out the link reference definitions of the paragraphs it
finalized.

## Building Nodes

`BlockTreeBuilder` turns `OpenBlock`s into `BlockNode`s with resolved inline content:

- a closed block that is not speculative (not created or copied by a fork) is final: it is built once and its
  node, the reference labels it uses and its id are cached on the block;
- an open container keeps the cached nodes of its leading closed children in a `SharedArray`, and an open table
  those of its rows; building the container appends the newly closed ones and builds only the rest, and the
  published list shares the array, so rebuilding a 6000-item list costs the same as a 2-item list;
- fenced code and math text is appended in place (`JoinedLines`), and so is paragraph text; the node then copies
  the text once;
- inline content goes through `InlineCache`: an unchanged leaf is a cache hit, and a growing paragraph is parsed
  again only from the last checkpoint of its previous parse (`InlineParser.parse(resume = ...)`): a line start
  where no bracket is open, no emphasis opener waits for a closer and no scan read further, so nothing before it
  can change whatever text follows.

## Append Path

For `append(chunk)`:

1. `ChunkNormalizer` turns `\r\n` and `\r` into `\n` (also across chunk boundaries) and holds back a high
   surrogate that ends a chunk until its low surrogate arrives;
2. the text is appended to `SourceBuffer` and `LineIndex`;
3. the lines completed by this chunk are incorporated into the parser;
4. the parser hands out the top-level blocks that closed and the definitions it finalized;
5. a fork of the parser takes the unterminated last line and is finished; its top-level blocks are the tail,
   and its definitions are the tail definitions, except those on the unterminated last line, which take effect
   once the line ends so dependent blocks do not churn per character;
6. if the definition in effect for a label changed, the cached nodes that use the label are marked stale and
   rebuilt (stable blocks through the label index of `StableBlocks`, closed children of open blocks through the
   builder), keeping their ids, and the inline cache revision moves on;
7. the closed top-level blocks are built and move into `StableBlocks`; the tail is built from the fork;
8. a new immutable `MarkdownSnapshot` and a `ParseDelta` are published. The stable prefix ends where the open
   top-level block starts, or at the start of the unterminated last line.

`finish()` flushes a held-back surrogate, incorporates the last line and closes everything; all blocks become
stable.

## Cost Per Call

Every step is proportional to the new text, the unterminated last line and the depth of the open path, plus
copying the text of the open leaf (a code block or paragraph) into its node, which the immutable `String` model
requires. There is no whole-source copy, no re-parse of earlier lines, no rebuild of closed blocks, no
flattening of the tree and no deep snapshot comparison. 100KB streamed in 4-10 character chunks takes 20-35 ms
whether it is a mixed document or one open list, paragraph, block quote, table or fence; see
`performance-notes.md`.

## Delta

- `changedBlocks`: top-level blocks that are new, moved or changed, in document order, with old and new index;
- `removedBlockIds`: ids (at any depth) built in the previous call and not built again; final blocks keep
  their ids for good;
- `dirtyRegion`: the range whose lines this call parsed, from the start of the previous unterminated last line;
- every top-level block not listed in `changedBlocks` is the same as in the previous snapshot.

`ParseStats` reports `appendedChars`, `processedLines` (lines completed by the call plus the unterminated last
line), `builtBlocks` (blocks at any depth built by the call instead of taken from a cache), `preservedBlocks`
(stable blocks carried over untouched), `inlineParsedBlockCount` and `inlineCacheHitBlockCount`. They are
diagnostics for the sample debug view and the benchmarks.
