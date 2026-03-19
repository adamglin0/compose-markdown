# Known Limitations

## Editing Model

- append-only updates are supported; arbitrary insert/delete in the middle of the source is out of scope;
- callers must create a new engine or call `reset()` after `finish()` before starting a new session.

## Markdown Coverage

- raw HTML is disabled for all presets;
- delimiter handling aims at common chat / README cases, not full CommonMark compliance;
- pipe tables support common syntax only and do not try to match GitHub layout quirks exactly;
- reference definitions are single-line only;
- image nodes are parsed but renderer behavior remains intentionally lightweight.

## Rendering

- Compose rendering is block-oriented, so there is no HTML export or rich editor mode;
- there is no dedicated image loading pipeline in this repository checkpoint;
- sample rendering is for verification and exploration, not final product styling.

## Performance

- the engine localizes reparsing, but immutable snapshot rebuild and delta classification still scale with the current top-level block list;
- benchmark numbers are JVM-only for Stage 8;
- performance notes focus on parsing and allocation behavior, not Compose frame metrics.

## Tooling

- CI is intentionally minimal and does not publish artifacts;
- benchmark tasks are meant for local measurement and smoke verification, not for hard pass/fail regression gates yet.
