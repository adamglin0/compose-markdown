# markdown-compose

Planned home of the Compose Multiplatform rendering layer.

Stage 1 status: Kotlin Multiplatform module initialized as a scaffold checkpoint with snapshot-driven placeholder Compose APIs on top of `markdown-core`.

Current public shape:

- render from `MarkdownSnapshot` via `Markdown(...)` or `MarkdownSnapshotView(...)`,
- drive placeholder state through `rememberMarkdownState()` plus `append()` / `finish()` / `reset()`,
- temporary Material3 styling is only for the Stage 1 placeholder renderer and is not a long-term API commitment.

Planned responsibilities:

- block-level composables,
- RenderIR rendering,
- keyed updates using stable block IDs,
- renderer-specific theming hooks.
