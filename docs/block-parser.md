# Block Parser MVP

Stage 3 introduces the first real ChatFast block layer in `markdown-core`.

## Scope

- `SourceBuffer` stores append-only source text and accepts incomplete tail lines.
- `LineIndex` records newline offsets and exposes line metadata without requiring Compose.
- `BlockParser` reparses the current buffer as a line-based tree after every `append()` or `finish()`.
- The parser uses a mutable open block stack internally, but snapshots stay immutable.

## Supported Blocks

- Paragraph
- ATX heading
- Fenced code block
- Block quote
- Unordered list
- Ordered list
- List item
- Thematic break
- Blank-line paragraph termination

## Current MVP Behavior

- `append(chunk)` may stop in the middle of a line, list item, or fenced code block.
- Unclosed fenced code blocks are emitted with `isClosed = false` until a matching closing fence appears.
- `finish()` finalizes the snapshot and clears internal open block frames at EOF.
- After `finish()`, the current engine session is closed; callers must `reset()` or create a new engine before calling `append()` again.
- Stable block IDs are reused by block kind + start offset + small discriminator when append-only growth preserves identity.
- Inline parsing is still placeholder-only: block nodes currently expose text children rather than a full inline AST.

## Deliberate Limitations

- Setext headings, tables, reference definitions, raw HTML blocks, and footnotes are still unsupported.
- List tight/loose behavior is basic and only tracks a coarse `isLoose` flag.
- The engine still reparses the whole document after each append; the current data structures are staged for later incremental parsing work.
