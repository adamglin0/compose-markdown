# Known Limitations

## Editing Model

- the engine is append-only; arbitrary insert/delete in the middle of the source is out of scope
- callers must `reset()` or create a new engine after `finish()` before starting a new session

## Spec Coverage

- raw HTML is not parsed in any preset: HTML blocks and inline tags stay literal text
- footnotes and HTML export are not implemented
- block and inline parsing follow the CommonMark 0.31.2 reference algorithms and pass every spec section except examples that need raw HTML; GFM tables pass in full (see `docs/compatibility-report.md`)
- GFM extended `www.` / URL autolinks are not recognized while a `[` or `![` bracket is open (cmark-gfm behaviour), so URLs inside unmatched brackets stay text
- inline nodes nested deeper than 32 levels are kept as literal text to bound tree depth
- block quotes and list items nested deeper than 64 container levels (a list item counts twice) are kept as paragraph text for the same reason
- delimiter flanking classifies UTF-16 code units, so astral characters such as emoji next to `*`/`_` count as neither whitespace nor punctuation
- code block literals drop the final line ending, so a fenced block holding one empty line has the same `literal` as an empty one
- inline node ranges inside block quotes and list items are approximate: container markers between lines are not part of a paragraph's literal
- display math blocks (`GfmMath`) open only on a line that is exactly `$$` / `\[`, or on a single line such as `$$x$$`; other lines starting with `$$` or `\[` stay paragraph text

## Incremental Model And Performance

- mutable-tail reparsing is localized, but immutable snapshot rebuild and delta classification still scale with the current top-level block list
- performance measurements are documented from the JVM benchmark runner only; no equivalent native or JS benchmark suite ships yet
- `ParseStats` is diagnostic surface area, not a hard complexity guarantee

## Rendering

- Compose rendering is block-oriented and intentionally lightweight
- images render as alt text unless the app passes an `imageContent` composable; the library ships no image loading
- inline math and images are measured on first composition, so their line reflows once when they first appear
- link titles are not shown
- task list checkboxes render as read-only UI; toggle interactions are not implemented
- table rendering sizes columns from content, honors left/center/right alignment and scrolls horizontally when too wide, but has no sticky headers or GitHub-style chrome
- syntax highlighting covers the languages of `dev.snipme:highlights` (C, C++, C#, CoffeeScript, Dart, Go, Java, JavaScript, Kotlin, Perl, PHP, Python, Ruby, Rust, Shell, Swift, TypeScript and their common aliases); other languages render as plain monospace text
- text is selectable only inside an app-provided `SelectionContainer`; selection across lazy items follows `LazyColumn` limits

## Tooling And Distribution

- CI runs the full check suite on Linux plus library iOS simulator tests on macOS; `v*` tags publish `markdown-core` and `markdown-compose` to Maven Central (see `docs/maven-central-publishing.md`)
- benchmark tasks are for local measurement and smoke verification, not for hard pass/fail release gating
- `markdown-compose` requires Java 21 on desktop JVM because `dev.snipme:highlights` ships Java 21 bytecode; `markdown-core` targets Java 17

For the final support audit, see `docs/compatibility-report.md`. For follow-up priorities, see `docs/next-steps.md`.
