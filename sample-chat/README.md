# sample-chat

Desktop-first Compose Multiplatform sample app for the current Stage 5 checkpoint.

It is the incremental delta/stats debugging entry for the append-only ChatFast engine rather than a planned-only module.

Current responsibilities:

- drive `MarkdownEngine.append()` with chat-like chunks,
- visualize block-level updates plus delta/stats diagnostics,
- provide a manual debugging surface for incremental parser behavior in the primary chat scenario.
