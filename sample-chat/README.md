# sample-chat

Desktop-first Compose sample app for exercising the append-only streaming workflow.

## Run

```bash
./gradlew :sample-chat:run
```

Current responsibilities:

- simulate LLM-style chunked output with start, stop, reset, script selection, and delay controls
- drive `MarkdownEngine.append()` incrementally and apply each `ParseDelta` into `MarkdownRendererState`
- render the current message through `markdown-compose`
- expose debug text so block reuse, late reference-definition reprocessing, and fence closure remain easy to inspect
