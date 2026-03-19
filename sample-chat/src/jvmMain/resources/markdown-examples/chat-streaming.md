# Support reply stream

The assistant is drafting a rollout note in small chunks so the layout should feel like a calm preview workbench instead of a debugger.

## What just landed

- Parser updates arrive per chunk.
- Existing blocks keep their identity while text grows.
- Links such as [release checklist](https://example.com/release-checklist) remain clickable.

> Streaming should make the answer feel alive,
> but the reader still needs a stable place to read.

### Reply draft

Hello team,

We finished the desktop-first markdown preview refactor. The sidebar now acts as a sample picker, the toolbar keeps playback controls compact, and the main canvas stays focused on the rendered document.

1. Pick a scenario from the left rail.
2. Adjust the chunk speed if you want to inspect updates.
3. Press start to replay the stream.

Thanks for reviewing.
