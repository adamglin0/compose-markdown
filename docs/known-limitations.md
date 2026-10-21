# Known Limitations

## Editing Model

- the engine is append-only; arbitrary insert/delete in the middle of the source is out of scope
- callers must `reset()` or create a new engine after `finish()` before starting a new session

## Spec Coverage

- raw HTML is disabled for every preset
- indented code blocks, footnotes, and HTML export are not implemented
- inline parsing follows the CommonMark 0.31.2 reference algorithm and passes every inline spec section except examples that need raw HTML or block-level constructs the block parser does not produce yet (see `docs/compatibility-report.md`)
- GFM extended `www.` / URL autolinks are not recognized while a `[` or `![` bracket is open (cmark-gfm behaviour), so URLs inside unmatched brackets stay text
- inline nodes nested deeper than 32 levels are kept as literal text to bound tree depth
- delimiter flanking classifies UTF-16 code units, so astral characters such as emoji next to `*`/`_` count as neither whitespace nor punctuation
- reference definitions are single-line only
- tables implement the common pipe-table subset rather than full GFM edge behavior; delimiter cells do accept one or more hyphens (`|-|`, `|--|`, `|---|`)

## Incremental Model And Performance

- mutable-tail reparsing is localized, but immutable snapshot rebuild and delta classification still scale with the current top-level block list
- performance measurements are documented from the JVM benchmark runner only; no equivalent native or JS benchmark suite ships yet
- `ParseStats` is diagnostic surface area, not a hard complexity guarantee

## Rendering

- Compose rendering is block-oriented and intentionally lightweight
- images currently render as alt text only; this repository does not include an image loading pipeline
- task list checkboxes render as read-only UI; toggle interactions are not implemented
- table rendering sizes columns from content and honors left/center/right alignment, but does not implement full GitHub-style interactive or sticky table chrome

## Tooling And Distribution

- CI runs the full check suite on Linux plus library iOS simulator tests on macOS; `v*` tags publish `markdown-core` and `markdown-compose` to Maven Central (see `docs/maven-central-publishing.md`)
- benchmark tasks are for local measurement and smoke verification, not for hard pass/fail release gating
- `markdown-compose` requires Java 21 on desktop JVM because `dev.snipme:highlights` ships Java 21 bytecode; `markdown-core` targets Java 17

For the final support audit, see `docs/compatibility-report.md`. For follow-up priorities, see `docs/next-steps.md`.
