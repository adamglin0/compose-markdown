# Block Parser

`BlockParser` (`markdown-core/.../core/block/`) turns source lines into the block tree. It follows CommonMark 0.31.2, appendix "A parsing strategy", as implemented by the commonmark.js reference parser; GFM tables follow cmark-gfm's table extension.

For the repository-wide support boundary, `docs/compatibility-report.md` is the canonical reference.

## Structure

- `BlockParser.kt`: the line-by-line state machine (`BlockParserSession`) over a mutable tree of open blocks.
- `BlockSyntax.kt`: pure scanners for block starts (thematic breaks, ATX headings, code fences, setext underlines, list markers).
- `TableSyntax.kt`: GFM row and delimiter-row splitting.
- `LinkReferenceDefinitionSyntax.kt`: link reference definitions, built on the shared link scanners in `inline/LinkSyntax.kt`.
- `BlockTreeBuilder.kt`: converts the finished tree into immutable `BlockNode`s and allocates block IDs (parent first, in document order).

## Algorithm

Each line goes through three steps:

1. **Continuation.** Walk the open blocks from the document down and match each block's continuation marker: `>` for block quotes, the item's content indentation for list items (or a blank line inside a non-empty item), four columns for indented code, a closing fence for fenced code and math. Matching stops at the first block that does not continue.
2. **Block starts.** Unless the last matched block is a leaf that takes raw lines (code or math), try new block starts in this order: block quote, ATX heading, code fence, display math, setext underline (only directly under a matched paragraph), thematic break, list item, GFM table delimiter row; an indented line can only start an indented code block, and only when no paragraph is open. Container starts repeat on the rest of the line.
3. **Text.** If some open blocks did not match, the line is not blank and the innermost open block is a paragraph, the line is a *lazy continuation* of that paragraph. Otherwise unmatched blocks close and the rest of the line goes to the matched leaf, becomes a table row, or starts a new paragraph.

Tabs advance to the next multiple of four columns; a tab that is only partly consumed by a container marker contributes the remaining columns as spaces.

When a block closes it is finalized: a paragraph moves its leading link reference definitions into the parse result (first definition of a label wins, definitions may span lines), a list decides whether it is loose, and an indented code block drops trailing blank lines.

## Block Rules Worth Knowing

- **Lists.** Items of one list share the bullet character, or the ordered delimiter (`.` / `)`); a different marker starts a new list. A list item may interrupt a paragraph only when it has content and, if ordered, starts at 1. A list is loose when an item is followed by a blank line before the next item or an item directly holds two children separated by a blank line (the commonmark.js line-position rule), so blank lines inside nested lists or fenced code keep an outer list tight. `ListBlock.type` exposes `ListType.Bullet(bulletChar)` or `ListType.Ordered(startNumber, delimiter)`.
- **Task list items** (`taskListItems` presets): `[ ]`, `[x]` or `[X]` first on an item's line, followed by a space, tab or the line end. The marker alone (`- [ ]`) is an empty task item, which keeps streaming output stable while the text is still arriving.
- **Indented code** is part of every preset, including `ChatFast`: models are trained on GitHub-rendered Markdown, where four-space indentation is code, so rendering it the same way is the least surprising choice and needs no preset switch.
- **Tables** (cmark-gfm): a delimiter row turns the *last* line of the paragraph above it into the header when both have the same number of cells; earlier paragraph lines stay a paragraph. Rows continue until a blank line or another block start, even without pipes. `\|` never splits a cell (also inside code spans) and is unescaped to `|`. Rows are padded with empty cells or truncated to the header's column count.
- **Display math** (`mathBlocks`, `GfmMath`): a line that is exactly `$$` or `\[` opens a block that ends at a line ending in `$$` / `\]` (text before the closer belongs to the formula) or at the end of its container. A line that starts with the opener and whose first closer ends the line (`$$x^2$$`, `\[a + b\]`) is a single-line block. Every other line stays paragraph text, so `\[note] see` or `$$x$$ and text` never swallow content; inline math applies there instead. Math openers interrupt paragraphs.
- **Raw HTML** is not a block construct: HTML stays literal paragraph text in every preset.

## Streaming Contract

`BlockParser.parse(isFinal, range)` parses the lines of `range`, which the engine always starts at a top-level block boundary. Unless `isFinal`, `BlockParseResult.openBlockStack` lists the open blocks that later input can still change, outermost first, each with the offset of its first line:

- a paragraph is open until a blank line or another block closes it, so a trailing line ending does not make it stable (`Foo\n` can still become a setext heading);
- tables, lists, list items, block quotes and unclosed fenced code or math stay open until a following line does not continue them;
- headings, thematic breaks, single-line math and closed fences are never open, because no later line can change them;
- when the last line has no line ending yet, it may still grow into a different block start, so the blocks it closed are reported as open.

The engine uses the first frame's offset as the end of the stable prefix.

## Limitations

- Raw HTML blocks are out of scope by design.
- Code block literals do not keep a trailing line ending, so a fenced block holding one empty line has the same `literal` as an empty block.
- Inline node ranges are approximate inside containers: the text node of a paragraph spans from its first content character to the end of its last line, while container markers between lines are not part of the literal.
