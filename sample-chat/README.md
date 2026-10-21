# sample-chat

Desktop-first Compose sample app for exercising the append-only streaming workflow.

## Run

```bash
./gradlew :sample-chat:run
```

Current responsibilities:

- simulate LLM-style chunked output with start, stop, reset, script selection, and delay controls
- stream chunks into a `MarkdownState` and render its document with `LazyListScope.markdown`, one lazy item per top-level block
- switch between LaTeX renderers (`MathRenderer`) and open links through the platform `UriHandler`
- show status text with the replay progress and the last clicked link
