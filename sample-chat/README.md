# sample-chat

Desktop-first Compose sample app for exercising the append-only streaming workflow.

## Run

```bash
./gradlew :sample-chat:run                       # desktop
./gradlew :sample-chat-android:installDebug     # Android
```

The shared UI lives in this Kotlin Multiplatform module; the Android app is the separate `:sample-chat-android` module, because AGP 9 keeps application modules out of Kotlin Multiplatform.

Current responsibilities:

- simulate LLM-style chunked output with start, stop, reset, script selection, and delay controls
- stream chunks into a `MarkdownState` and render its document with `LazyListScope.markdown`, one lazy item per top-level block
- switch between LaTeX renderers (`MathRenderer`) and open links through the platform `UriHandler`
- show status text with the replay progress and the last clicked link
