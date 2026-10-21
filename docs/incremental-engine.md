# Incremental Engine Notes

This document records the append-only engine as implemented in `markdown-core`
(`core/engine/IncrementalMarkdownEngine.kt`). The mental model is in `incremental-model.md`.

## Working State

- `stable prefix`: top-level blocks (and link reference definitions) that no later input can change. They are
  never re-parsed; `StableBlocks` holds them.
- `mutable tail`: the top-level blocks from `snapshot.stablePrefixEnd` on. The tail is re-parsed on every call.
- `dirty region`: the range the latest call parsed, from the previous stable prefix end to the end of input.
- `ReferenceDefinitions`: the definitions in effect; stable definitions are final, tail definitions are
  replaced by every parse.
- `inline cache`: inline parse results of tail leaf blocks, keyed by block id and checked against the raw text,
  its range and the definition revision.
- `BlockIdAllocator`: identity keys (kind, start offset, discriminator) the block parser requested in the
  previous tail parse, with their ids.

## Block Parser Contract

The engine depends on two properties of `BlockParser.parse(isFinal, range)`:

1. **Restartable at top-level boundaries.** Parsing may start at any offset that begins a top-level block, or at
   the end of the last complete line, once everything before it is closed. From there on it yields exactly the
   blocks and definitions a parse from the start of the document would yield.
2. **Truthful open frames.** Unless `isFinal`, `BlockParseResult.openBlockStack` lists the blocks that later
   input can still change, outermost first. The first frame's `startOffset` is the start of the line on which
   the first changeable top-level block starts; every top-level block and definition before it is final. A
   block closed only by the unterminated last line stays open, because that line can still become something
   else.

The block parser documents the rules it follows (`block-parser.md`): a paragraph stays open after a trailing
newline (it can become a setext heading or a table header), tables, lists, items, block quotes and unclosed
fences or math stay open, headings, thematic breaks, single-line math and closed fences never do, and a
paragraph that holds only link reference definitions is open but has no block node.

## Append Path

For `append(chunk)`:

1. `ChunkNormalizer` turns `\r\n` and `\r` into `\n` (also across chunk boundaries) and holds back a high
   surrogate that ends a chunk until its low surrogate arrives;
2. the text is appended to `SourceBuffer` and its line endings to `LineIndex`;
3. the tail is parsed: `BlockParser(range = stablePrefixEnd until end)`. The parser reads lines from the live
   source buffer, so only the tail's lines are copied;
4. the new stable prefix end is the first open frame's offset, capped at the start of the last line when that
   line is unterminated (even a blank unterminated line can still grow);
5. blocks before that offset move into `StableBlocks`, the rest becomes the new tail;
6. definitions before that offset become stable (the first definition of a label wins); definitions on the
   unterminated last line are ignored until the line ends, so dependent blocks do not churn per character;
7. if the definition in effect for a label changed, the stable blocks that use the label get their inline
   content resolved again from their stored raw form, and the inline cache revision moves on;
8. tail blocks get inline content, from the cache where the raw text is unchanged;
9. a new immutable `MarkdownSnapshot` and a `ParseDelta` are published.

`finish()` flushes a held-back surrogate and parses the tail once more with `isFinal = true`; everything becomes
stable.

## Cost Per Call

Every step above is proportional to the tail, except copying the top-level block list into the new snapshot
(an array copy of references). In particular there is no whole-source copy, no flattening of the whole tree,
no rebuilt dependency index and no deep snapshot comparison per call.

The tail is re-parsed from its first block, so a single growing top-level block costs time proportional to its
size on every append: an unclosed 100KB fence is about 0.1 ms per append, a 100KB list of 6000 items about
1.5 ms (block structure and ids of every item are rebuilt; inline content of unchanged items comes from the
cache), a single 100KB paragraph about 0.3 ms (its inline content is parsed again). See
`performance-notes.md`.

## Delta

- `changedBlocks`: top-level blocks that are new, moved or changed, in document order, with old and new index;
- `removedBlockIds`: ids (at any depth) of the previous tail that the new parse did not reuse;
- `dirtyRegion`: the range parsed by this call;
- every top-level block not listed in `changedBlocks` is the same as in the previous snapshot.

`ParseStats` reports `appendedChars`, `processedLines`, `reparsedBlocks` (top-level blocks the parser produced
plus stable blocks re-resolved for a definition), `preservedBlocks` (stable blocks carried over untouched),
`inlineParsedBlockCount` and `inlineCacheHitBlockCount`. They are diagnostics for the sample debug view and the
benchmarks.
