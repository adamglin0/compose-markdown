# Dialect Matrix

The repository follows a three-step dialect strategy instead of trying to ship full compatibility at once.

The route below describes the planned dialect surface, and the current repository stop point is Stage 4: inline parser MVP for ChatFast.

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

## ChatFast v0 Target Boundary

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

## Current Stage 4 Implementation Boundary

Stage 4 keeps the Stage 3 block parser and adds inline parsing for the ChatFast MVP surface.

### Implemented Now

- paragraph
- ATX headings
- fenced code blocks
- block quotes
- ordered and unordered lists
- list items
- thematic breaks
- blank-line paragraph termination
- inline code span
- emphasis / strong
- inline links (`[text](url)`)
- autolinks (`<https://...>` and bare `http(s)://...` heuristic)
- strikethrough (`~~`)
- backslash escapes
- hard breaks and soft breaks

### Known Limitations In Stage 4

- emphasis delimiter flanking rules are simplified and do not yet match full CommonMark edge behavior
- nested bracket/parenthesis handling in links targets common chat cases, not full spec corner cases
- link titles and reference-style links are not supported
- autolink detection is heuristic and intentionally conservative for chat streaming stability
- underscore emphasis inside words may parse differently from CommonMark in some edge cases
- parser stability is prioritized over exact compatibility for incomplete tail tokens during streaming

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

| Feature | Current Repo (Stage 4) | ChatFast v0 Target | CommonMarkCore | GfmCompat | Notes |
| --- | --- | --- | --- | --- | --- |
| Paragraphs | Yes | Yes | Yes | Yes | Baseline block support |
| ATX headings | Yes | Yes | Yes | Yes | Included in block parser MVP |
| Setext headings | No | No | Planned | Planned | Deferred due to backward dependency on prior line |
| Fenced code blocks | Yes | Yes | Yes | Yes | High priority for chat output |
| Indented code blocks | No | No | Planned | Planned | Lower priority in chat scenarios |
| Block quotes | Yes | Yes | Yes | Yes | Conservative continuation rules first |
| Lists | Yes | Yes | Yes | Yes | ChatFast favors common cases over edge cases |
| Thematic breaks | Yes | Route not frozen | Planned | Planned | Implemented in the Stage 3 block parser MVP |
| Inline code | Yes | Yes | Yes | Yes | Implemented in InlineParser MVP |
| Emphasis / strong | Yes | Yes | Yes | Yes | Simplified delimiter rules in Stage 4 |
| Inline links | Yes | Yes | Yes | Yes | Reference links deferred |
| Reference links | No | No | Planned | Planned | Needs future dependency tracking |
| Autolinks | Yes | Yes | Yes | Yes | Includes angle autolinks + bare URL heuristic |
| Strikethrough | Yes | Yes | No | Planned | ChatFast keeps it because chat content uses it often |
| Hard / soft breaks | Yes | Yes | Yes | Yes | Important for streaming chat display |
| Raw HTML | No | No | Optional later | Optional later | Disabled by default |
| Tables | No | No | No | Planned | Requires wider invalidation rules |
| Task lists | No | No | No | Planned | GFM extension only |
| Footnotes | No | No | No | Planned | Low priority |

## Current Stop Point

The repository is currently stopped at Stage 4. Full incremental invalidation minimization and deeper cache efficiency remain Stage 5 work.
