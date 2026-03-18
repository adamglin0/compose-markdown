# sample-chat

Desktop-first Compose Multiplatform sample app for the current Stage 7 checkpoint.

Current responsibilities:

- simulate LLM-style chunked output with start, stop, reset, script selection, and chunk-delay controls,
- drive `MarkdownEngine.append()` incrementally while wiring each `ParseDelta` into `MarkdownRendererState`,
- render the current message through `markdown-compose` with block-keyed updates across the Stage 7 preset surface,
- expose delta and snapshot debug text so block reuse, late reference-definition reprocessing, and fence closure behavior stay easy to verify.
