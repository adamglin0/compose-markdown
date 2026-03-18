# Inline Parser MVP

Stage 4 adds the ChatFast inline layer in `markdown-core`.

## Scope

- `InlineParser` is independent from Compose and runs inside `markdown-core`.
- `BlockParser` still owns only block segmentation and block ranges.
- Inline parsing is applied to text-bearing block payloads (`Paragraph` and `Heading` nodes, including those nested inside `BlockQuote` and `ListItem`).
- `FencedCodeBlock` remains raw text and does not run inline parsing.

## Supported Inline Nodes

- Text
- Emphasis
- Strong
- Code span
- Inline links (`[text](url)`)
- Autolinks (`<https://...>` and bare `http(s)://...` heuristic)
- Backslash escapes
- Soft break
- Hard break
- Strikethrough

## Streaming Behavior

- Incomplete delimiters degrade to text until enough input arrives.
- Incomplete inline links and code spans are safe in middle-state chunks and do not crash parsing.
- `finish()` reuses cached inline results when block text is unchanged.

## ParseStats Note

- `ParseStats` keeps block-level counters and now includes inline counters:
  - `inlineParsedBlockCount`: blocks that actually ran inline parsing in this rebuild.
  - `inlineCacheHitBlockCount`: unchanged blocks resolved by inline cache reuse.

## Requirement -> Tests

- Stable prefix cache reuse + dirty tail reparse:
  - `markdown-core/src/commonTest/kotlin/dev/markstream/core/api/MarkdownEngineTest.kt` (`unchangedStablePrefixBlockUsesInlineCacheWhileTailIsReparsed`)
- Finalized new block + dirty tail block must parse:
  - `markdown-core/src/commonTest/kotlin/dev/markstream/core/api/MarkdownEngineTest.kt` (`finalizedNewBlockAndDirtyTailBlockAreParsed`)
- Inline node ranges (Emphasis/Strong/CodeSpan/Link/Strikethrough):
  - `markdown-core/src/commonTest/kotlin/dev/markstream/core/api/InlineParserMvpTest.kt` (`inlineNodeRangesAreCorrectForCommonMarkers`)
- SoftBreak and HardBreak ranges:
  - `markdown-core/src/commonTest/kotlin/dev/markstream/core/api/InlineParserMvpTest.kt` (`inlineBreakRangesTrackOriginalSourceOffsets`)

## Known Limitations

- Delimiter flanking and precedence are simplified and do not match all CommonMark edge cases.
- Link titles are not parsed.
- Reference-style links are out of scope in ChatFast v0.
- Autolink detection is heuristic and can differ from strict spec behavior.
- Escape handling is focused on common punctuation and chat-friendly behavior.
