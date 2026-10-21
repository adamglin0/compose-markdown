# Append-Only Incremental Model

This document defines the mental model used by `MarkdownEngine`. The engine is optimized for appended text, not
arbitrary edits. Implementation details are in `incremental-engine.md`.

## Guarantee

Streaming is invisible: after every `append`, the snapshot equals what a fresh engine produces for the same
source in one `append`, and after `finish()` it equals a one-shot parse followed by `finish()`, in every
dialect and for every way of splitting the input into chunks.

## Core Terms

### Stable Prefix

The source prefix `[0, stablePrefixEnd)` whose top-level blocks are final.

- it ends where the first top-level block that later input can change begins;
- it never moves backward within a session and covers the whole source after `finish()`;
- its blocks keep their index, id, structure and ranges, and are never re-parsed;
- their inline content can still change, but only when a link reference definition later in the document
  defines a label they use (CommonMark resolves references document-wide).

In CommonMark only the last top-level block can be open, so the stable prefix is everything before the last
top-level block while that block is open, and everything up to the last complete line once it is closed.

### Mutable Tail

The top-level blocks after the stable prefix. Typical reasons a block stays in the tail:

- a paragraph, which can still gain lines, become a setext heading or a table header, or turn out to be link
  reference definitions;
- an open fenced code or math block;
- a list, list item, block quote or table, which can absorb further lines;
- any block closed only by the unterminated last line, which may still turn into something else.

### Dirty Region

The source range parsed by the latest call: from the previous stable prefix end to the end of input.

## Incremental Flow

For every `append(chunk)`:

1. normalize line endings and hold back a split surrogate pair;
2. append the text to the source buffer and line index;
3. parse the mutable tail;
4. move the blocks before the first open block into the stable prefix;
5. update the link reference definitions in effect and re-resolve stable blocks that use a changed label;
6. resolve inline content of the tail, reusing cached results for unchanged blocks;
7. emit a new immutable `MarkdownSnapshot` and `ParseDelta`.

## Stable ID Rules

- stable blocks keep their ids;
- a re-parsed block keeps its id when the parser requests the same identity key (kind, start offset and a
  discriminator such as heading level or list marker type) as in the previous tail parse;
- ids are unique within a snapshot; ids of the previous tail that are not reused are reported as removed.

## Non-Goals

- arbitrary insertion or deletion inside the source;
- parsing a single huge open block in time independent of its size (it is re-parsed from its start on every
  append; see `incremental-engine.md`);
- HTML DOM diffing.
