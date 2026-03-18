# Dialect Matrix

The repository follows a three-step dialect strategy instead of trying to ship full compatibility at once.

## Dialect Route

### `ChatFast`

The default dialect for v0 and the first implementation target.

Focus:

- predictable parsing,
- low invalidation cost under append-only streaming,
- good results for LLM-generated chat content,
- graceful fallback when syntax is unsupported or incomplete.

### `CommonMarkCore`

The second dialect target.

Focus:

- cover the CommonMark behaviors that users broadly expect,
- tighten edge-case behavior,
- preserve the incremental architecture instead of bypassing it.

### `GfmCompat`

The third dialect target.

Focus:

- targeted GitHub-style extensions,
- opt-in compatibility features,
- explicit feature-specific invalidation rules.

## ChatFast v0 Support Boundary

### Supported In Scope

- paragraph
- ATX headings
- fenced code blocks
- block quotes
- ordered and unordered lists
- inline code
- emphasis
- strong
- inline links
- autolinks
- strikethrough
- hard breaks
- soft breaks

### Supported With Deliberate Simplicity

- lists prioritize chat-style common cases over every CommonMark corner case
- autolinks prioritize angle-bracket links and bare `http://` or `https://` URL heuristics
- block quotes and lists may use conservative continuation rules in v0
- incomplete delimiters remain displayable as plain text or provisional syntax until later input resolves them

### Deferred Or Out Of Scope For ChatFast v0

- raw HTML
- reference-style links
- setext headings
- images beyond simple future planning
- footnotes
- tables
- task lists
- HTML blocks
- link reference definitions
- full email autolink coverage

## Graceful Degradation Rule

Unsupported syntax must degrade to plain text or simpler block structure. It must not crash the parser and must not force whole-document invalidation by default.

## Feature Matrix

| Feature | ChatFast v0 | CommonMarkCore | GfmCompat | Notes |
| --- | --- | --- | --- | --- |
| Paragraphs | Yes | Yes | Yes | Baseline block support |
| ATX headings | Yes | Yes | Yes | Included early |
| Setext headings | No | Planned | Planned | Deferred due to backward dependency on prior line |
| Fenced code blocks | Yes | Yes | Yes | High priority for chat output |
| Indented code blocks | No | Planned | Planned | Lower priority in chat scenarios |
| Block quotes | Yes | Yes | Yes | Conservative continuation rules first |
| Lists | Yes | Yes | Yes | ChatFast favors common cases over edge cases |
| Inline code | Yes | Yes | Yes | Included early |
| Emphasis / strong | Yes | Yes | Yes | Included early |
| Inline links | Yes | Yes | Yes | Reference links deferred |
| Reference links | No | Planned | Planned | Needs future dependency tracking |
| Autolinks | Yes | Yes | Yes | Bare URL heuristic first in ChatFast |
| Strikethrough | Yes | No | Planned | ChatFast keeps it because chat content uses it often |
| Hard / soft breaks | Yes | Yes | Yes | Important for streaming chat display |
| Raw HTML | No | Optional later | Optional later | Disabled by default |
| Tables | No | No | Planned | Requires wider invalidation rules |
| Task lists | No | No | Planned | GFM extension only |
| Footnotes | No | No | Planned | Low priority |

## Stage 0 Stop Point

This document freezes the first-dialect boundary. New syntax should not be added casually during early implementation stages without updating this matrix and the related ADRs.
