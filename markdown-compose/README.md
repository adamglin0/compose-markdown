# markdown-compose

Planned home of the Compose Multiplatform rendering layer.

Stage 2 status: Kotlin Multiplatform module provides a snapshot-driven placeholder Compose surface on top of `markdown-core`.

Current public shape:

- render from `MarkdownSnapshot` via `Markdown(...)` or `MarkdownSnapshotView(...)`,
- drive placeholder state through `rememberMarkdownState()` plus `append()` / `finish()` / `reset()`,
- temporary Material3 styling is only for the Stage 2 placeholder renderer and is not a long-term API commitment.

Planned responsibilities:

- block-level composables,
- RenderIR rendering,
- keyed updates using stable block IDs,
- renderer-specific theming hooks.
