# Reference Links

How the append-only engine resolves reference-style links (`CommonMarkCore`, `GfmCompat`, `GfmMath`). In CommonMark a definition can appear after the links that use it, so a streamed definition may change blocks that are already stable.

## State

- `ReferenceDefinitions`: label to definition in effect. Definitions of closed paragraphs are final, and the first definition of a label wins. Definitions of the open paragraph are replaced on every call.
- `StableBlocks`: for each stable top-level block that uses reference labels, the labels it uses.
- Closed children of an open container record the labels they use as well.

Labels are compared case-insensitively, with internal whitespace collapsed.

## Resolution flow

1. When the block parser finalizes a paragraph, it removes the link reference definitions at its start (they may span several lines) and hands them to the engine. Definitions never appear in the public block list.
2. The inline parser resolves `[text][label]`, `[label][]`, `[label]` and their image forms against the definitions in effect, and records every label a block uses, also unresolved ones.
3. When a call changes the definition in effect for a label, only the blocks that use that label are rebuilt. They keep their ids and are reported in `changedBlocks` at the same index.
4. A definition on the unterminated last line takes effect once the line ends, or at `finish()`, so dependent blocks do not churn per character.

## Limits

- `ChatFast` does not parse definitions: `[label]: url` lines stay paragraph text.
- Text that is not a valid definition stays paragraph text.
