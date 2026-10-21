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
- its blocks keep their index, id, structure and ranges, and are never parsed or built again;
- their inline content can still change, but only when a link reference definition later in the document
  defines a label they use (CommonMark resolves references document-wide).

In CommonMark only the last top-level block can be open, so the stable prefix is everything before the last
top-level block while that block is open, and everything up to the last complete line once it is closed.
The block parser is resumable and never closes a block because of the unterminated last line, so a closed
block is final.

### Mutable Tail

The top-level blocks after the stable prefix. Typical reasons a block stays in the tail:

- a paragraph, which can still gain lines, become a setext heading or a table header, or turn out to be link
  reference definitions;
- an open fenced code or math block;
- a list, list item, block quote or table, which can absorb further lines;
- any block closed only by the unterminated last line, which may still turn into something else.

### Dirty Region

The source range parsed by the latest call: from the start of the previous unterminated last line to the end
of input. Lines before it are never parsed again.

## Incremental Flow

For every `append(chunk)`:

1. normalize line endings and hold back a split surrogate pair;
2. append the text to the source buffer and line index;
3. add the completed lines to the resumable block parser, and parse the unterminated last line in a fork;
4. move the top-level blocks the parser closed into the stable prefix;
5. update the link reference definitions in effect and rebuild cached blocks that use a changed label;
6. build the tail from the fork, reusing the cached nodes of closed blocks and cached inline results;
7. emit a new immutable `MarkdownSnapshot` and `ParseDelta`.

## Stable ID Rules

- closed blocks keep their ids for good, also when they are rebuilt for a changed definition;
- a block of the tail keeps its id when it has the same identity key (kind, start offset, depth and a
  discriminator such as heading level or list marker type) as a block built in the previous call;
- ids are unique within a snapshot; ids of the previous tail that are not built again are reported as removed.

## Non-Goals

- arbitrary insertion or deletion inside the source;
- HTML DOM diffing.
