# sample-chat

Desktop-first Compose Multiplatform sample app for the Stage 6 streaming renderer checkpoint.

Current responsibilities:

- simulate LLM-style chunked output with start, stop, reset, script selection, and chunk-delay controls,
- drive `MarkdownEngine.append()` incrementally while wiring each `ParseDelta` into `MarkdownRendererState`,
- render the current message through `markdown-compose` with block-keyed updates,
- expose delta and snapshot debug text so block reuse and fence closure behavior stay easy to verify.
