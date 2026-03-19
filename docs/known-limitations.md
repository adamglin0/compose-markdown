# Known Limitations

## Editing Model

- the engine is append-only; arbitrary insert/delete in the middle of the source is out of scope
- callers must `reset()` or create a new engine after `finish()` before starting a new session

## Spec Coverage

- raw HTML is disabled for every preset
- indented code blocks, footnotes, and HTML export are not implemented
- delimiter handling focuses on common chat and README cases, not the full CommonMark edge-case matrix
- reference definitions are single-line only
- tables implement the common pipe-table subset rather than full GFM edge behavior

## Incremental Model And Performance

- mutable-tail reparsing is localized, but immutable snapshot rebuild and delta classification still scale with the current top-level block list
- performance measurements are documented from the JVM benchmark runner only; no equivalent native or JS benchmark suite ships yet
- `ParseStats` is diagnostic surface area, not a hard complexity guarantee

## Rendering

- Compose rendering is block-oriented and intentionally lightweight
- images currently render as alt text only; this repository does not include an image loading pipeline
- task list markers are readable but non-interactive
- table rendering favors simple readability over GitHub-faithful table layout

## Tooling And Distribution

- CI is intentionally minimal and does not publish artifacts
- benchmark tasks are for local measurement and smoke verification, not for hard pass/fail release gating
- release planning exists, but publication metadata and signing are intentionally deferred until there is a real publish target

For the final support audit, see `docs/compatibility-report.md`. For follow-up priorities, see `docs/next-steps.md`.
