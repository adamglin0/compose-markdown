# Engineering Deep Dive: Shipping a Safer Streaming Markdown Pipeline

_How the preview stack moved from "usually works" to a predictable, debuggable rendering path for long AI responses._

In Q1 we stopped treating the markdown preview as a cosmetic layer and started treating it like a product surface. The immediate trigger was a small incident: a partially streamed reply rendered fine in the happy path, but nested lists, unfinished code fences, and late-arriving reference links occasionally produced a confusing intermediate UI. Nothing was permanently lost, yet the experience felt fragile.

This write-up summarizes what changed, why the team chose an incremental parser over a repaint-heavy approach, and what we learned while hardening the stream lifecycle. It is written as a real engineering note rather than a syntax checklist, but it intentionally exercises the markdown features we care about during acceptance.

What changed
============

The old approach optimized for speed of implementation. Each append updated a text buffer, the renderer refreshed the full document, and we hoped the result stayed stable enough between chunks. That was acceptable for short answers, but it broke down when a response mixed prose, tables, and code.

> The most useful lesson from the incident review was simple: users do not judge correctness only by the final frame.
>
> They judge whether the UI feels trustworthy *while* content is still arriving.

We now bias the system toward predictable intermediate states. A chunk can still be incomplete, but it should not make the surrounding structure look random, flicker excessively, or force the reader to re-scan the whole message.

---

## Incident Snapshot

The customer-visible symptom was subtle:

1. A streamed answer opened with a heading and several paragraphs.
2. The next chunks introduced a fenced code block and a table.
3. The closing fence arrived late, which caused downstream paragraphs to look temporarily wrong.
4. A reviewer marked the issue as a parser bug, but the real problem was a mismatch between stream semantics and UI expectations.

Three constraints shaped the fix:

- We wanted incremental rendering, not a full reset after every append.
- We needed clear behavior for unfinished structures such as ```` ```kotlin ````.
- We could not regress plain text throughput just to make edge cases prettier.

The work also forced us to remove a few ideas that sounded clever on paper but did not survive measurement. In particular, a debounce-heavy repaint strategy looked attractive until we saw how often it hid state transitions that developers actually needed to inspect. That experiment is now ~~the preferred path~~ the option we keep documented as a rejected trade-off.

## Design Principles

We kept the final design boring on purpose. The guiding rule was: if a new teammate cannot explain the behavior after reading the code for ten minutes, the system is too magical.

- Prefer append-only state transitions over "repair everything later" heuristics.
- Keep parser output inspectable so a failing test can explain *which block* changed.
- Make incomplete input safe by default, even if that means a temporarily conservative render.
- Expose enough metadata for tooling, but avoid building a second protocol next to markdown itself.

Implementation work was tracked in two phases:

1. Stabilize the document model and snapshot generation.
2. Improve example coverage so demos and tests reflect realistic content.
3. Add regression cases for late fences, reference definitions, and mixed block types.

For day-to-day development, the team used `MarkdownEngine()` directly in focused unit tests and reserved end-to-end runs for acceptance verification.

### Renderer Notes

The renderer does not need to know every historical append; it only needs a trustworthy current snapshot plus enough structure to avoid guesswork. That sounds obvious, but it changed several implementation details:

| Decision | Why it helped | Trade-off |
| --- | --- | --- |
| Preserve block boundaries | Reduced visual churn during streaming | Slightly more bookkeeping |
| Accept incomplete fences | Kept previews readable mid-stream | Final block type may change later |
| Delay reference resolution until definitions arrive | Matched markdown semantics | Links appear "inactive" for a while |
| Use curated examples in the desktop sample | Improved manual QA coverage | Requires maintaining local fixtures |

One practical takeaway is that acceptance samples should read like real artifacts. A wall of isolated syntax tokens might prove parser support, but it rarely reveals whether the interface feels credible under real usage.

---

## Rollout Checklist

- [x] Add a long-form article sample to the desktop resources.
- [x] Connect the sample to the left-side scenario list.
- [x] Update tests that assume a fixed example count.
- [ ] Add visual diff coverage for theme-specific typography.
- [ ] Measure whether very large tables need virtualization.

The team also kept an informal review checklist for release candidates:

- Verify the opening section remains readable before the first list completes.
- Confirm nested blocks do not "jump" when a later chunk lands.
- Open the inline design note at [stream handling guide](https://example.com/streaming-markdown-guide).
- Re-read the retrospective in [the incident appendix][incident-appendix].
- Sanity-check exported logs that contain raw URLs like <https://status.example.com/incidents/2026-02-stream-preview>.

## Example: Guarding the Final Snapshot

The core idea is not complicated. We append chunks as they arrive, then finalize once the stream closes.

```kotlin
val engine = MarkdownEngine()

chunks.forEach(engine::append)

val snapshot = engine.finish().snapshot
check(snapshot.isFinal)
```

That snippet is intentionally small because the complexity is elsewhere: choosing examples, validating intermediate states, and deciding which incomplete structures should remain visible instead of being hidden until the end.

### What We Would Still Improve

There are still product questions that code alone cannot answer:

- Should a large table collapse automatically on narrow screens?
- Should the preview highlight blocks that were modified by the latest chunk?
- Should unresolved reference links display a loading hint, or is plain text enough?

My current recommendation is to keep the UX quiet. The preview should feel like a calm reading surface, not a debugger disguised as content.

Architecture sketch:

![Streaming pipeline sketch](data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20width='640'%20height='180'%20viewBox='0%200%20640%20180'%3E%3Crect%20width='640'%20height='180'%20fill='%23f5f1e8'/%3E%3Crect%20x='24'%20y='48'%20width='164'%20height='84'%20rx='12'%20fill='%23d9cbb3'/%3E%3Crect%20x='238'%20y='48'%20width='164'%20height='84'%20rx='12'%20fill='%23c9d8c5'/%3E%3Crect%20x='452'%20y='48'%20width='164'%20height='84'%20rx='12'%20fill='%23b9d3df'/%3E%3Cpath%20d='M188%2090h50M402%2090h50'%20stroke='%23614c3d'%20stroke-width='6'%20stroke-linecap='round'/%3E%3Ctext%20x='106'%20y='96'%20font-size='22'%20text-anchor='middle'%20fill='%233a2d24'%20font-family='Georgia,serif'%3EChunks%3C/text%3E%3Ctext%20x='320'%20y='96'%20font-size='22'%20text-anchor='middle'%20fill='%23253a2a'%20font-family='Georgia,serif'%3EEngine%3C/text%3E%3Ctext%20x='534'%20y='96'%20font-size='22'%20text-anchor='middle'%20fill='%231d3b46'%20font-family='Georgia,serif'%3ESnapshot%3C/text%3E%3C/svg%3E "A tiny inline architecture sketch")

The image above is intentionally lightweight and local to the document via a data URL, which makes it suitable for offline sample coverage without introducing another bundled asset.

---

## Closing Notes

If there is one theme across the work, it is this: the preview becomes more trustworthy when the sample content itself is trustworthy. A realistic article exposes edge cases earlier than a contrived demo, especially when it mixes prose, lists, links, tables, and code in one continuous narrative.

Additional reading lives in the internal [rendering rubric][rubric] and the public CommonMark spec at <https://spec.commonmark.org/>. We also kept a short migration memo for reviewers who wanted a smaller overview before reading this deep dive.

[incident-appendix]: https://example.com/incident-appendix
[rubric]: https://example.com/rendering-rubric
