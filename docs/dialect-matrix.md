# Dialect Matrix

The repository follows a three-step dialect strategy instead of trying to ship full compatibility at once.

The route below describes the planned dialect surface, but the current repository stop point is still Stage 3: block parser MVP. ChatFast v0 inline syntax is part of the route, not part of the currently shipped implementation.

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

## Current Stage 3 Implementation Boundary

Stage 3 only delivers the non-incremental block parser MVP for `ChatFast`.

### Implemented Now

- paragraph
- ATX headings
- fenced code blocks
- block quotes
- ordered and unordered lists
- list items
- thematic breaks
- blank-line paragraph termination

### Explicitly Not Implemented Yet

- inline code
- emphasis
- strong
- inline links
- autolinks
- strikethrough
- hard breaks
- soft breaks
- full inline AST construction; blocks still expose placeholder text children in the current MVP

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

| Feature | Current Repo (Stage 3) | ChatFast v0 Target | CommonMarkCore | GfmCompat | Notes |
| --- | --- | --- | --- | --- | --- |
| Paragraphs | Yes | Yes | Yes | Yes | Baseline block support |
| ATX headings | Yes | Yes | Yes | Yes | Included in block parser MVP |
| Setext headings | No | No | Planned | Planned | Deferred due to backward dependency on prior line |
| Fenced code blocks | Yes | Yes | Yes | Yes | High priority for chat output |
| Indented code blocks | No | No | Planned | Planned | Lower priority in chat scenarios |
| Block quotes | Yes | Yes | Yes | Yes | Conservative continuation rules first |
| Lists | Yes | Yes | Yes | Yes | ChatFast favors common cases over edge cases |
| Thematic breaks | Yes | Route not frozen | Planned | Planned | Implemented in the Stage 3 block parser MVP |
| Inline code | No | Yes | Yes | Yes | Planned for Stage 4 inline parsing |
| Emphasis / strong | No | Yes | Yes | Yes | Planned for Stage 4 inline parsing |
| Inline links | No | Yes | Yes | Yes | Reference links deferred |
| Reference links | No | No | Planned | Planned | Needs future dependency tracking |
| Autolinks | No | Yes | Yes | Yes | Bare URL heuristic comes with inline parsing |
| Strikethrough | No | Yes | No | Planned | ChatFast keeps it because chat content uses it often |
| Hard / soft breaks | No | Yes | Yes | Yes | Important for streaming chat display |
| Raw HTML | No | No | Optional later | Optional later | Disabled by default |
| Tables | No | No | No | Planned | Requires wider invalidation rules |
| Task lists | No | No | No | Planned | GFM extension only |
| Footnotes | No | No | No | Planned | Low priority |

## Current Stop Point

The repository is currently stopped at Stage 3. New syntax should not be marked as implemented until the corresponding parser layer ships and this matrix is updated alongside the related docs and ADRs.
