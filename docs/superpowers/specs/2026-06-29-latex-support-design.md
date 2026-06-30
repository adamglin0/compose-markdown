# LaTeX / Math Support — Design

- **Date:** 2026-06-29
- **Status:** Approved (design); pending implementation plan
- **Modules touched:** `markdown-core`, `markdown-compose`, `sample-chat`

## 1. Goal

Add LaTeX/math support to compose-markdown in three layers:

1. **Recognition** — the core parser recognizes LaTeX regions and emits dedicated AST nodes.
2. **Render slot** — the Compose layer exposes a pluggable slot so a consumer supplies the actual math renderer; without one, it degrades gracefully to raw source text.
3. **Demo** — `sample-chat` wires two real renderers and lets the user switch between them at runtime:
   - `huarangmeng/latex` → `io.github.huarangmeng:latex-base/-parser/-renderer:1.4.7`
   - `darriousliu/RaTeX-CMP` → `io.github.darriousliu:ratex:0.1.11`

### Confirmed decisions

- **Delimiters recognized:** inline `$...$` **and** `\(...\)`; block `$$...$$` **and** `\[...\]`.
- **Inline rendering:** true inline layout via Compose `InlineTextContent` (math flows within the text line).
- **Demo:** a runtime toggle switching the active renderer (huarangmeng / RaTeX / raw placeholder).
- **Slot/node naming:** slot `MathRenderer`; nodes `BlockNode.MathBlock` / `InlineNode.MathSpan` (payload field named `latex`).
- **Opt-in:** math is gated behind feature flags, **off** in all existing dialects; a **new `MarkdownDialect.GfmMath`** turns it on. The demo uses `GfmMath`.

## 2. Current state (investigation summary)

- No LaTeX/math support exists today: zero `latex/katex/mathjax` references; the parser has no `$`/`$$` handling. `$...$` and `$$...$$` currently fall through as plain paragraph text.
- Architecture is a good fit:
  - `markdown-core` — a custom, **incremental/streaming** Markdown engine. Syntax is gated by a `MarkdownDialect` whose `MarkdownBlockFeatures`/`MarkdownInlineFeatures` are boolean toggles; presets `ChatFast` (default), `CommonMarkCore`, `GfmCompat`. AST is sealed `BlockNode` / `InlineNode`.
  - `markdown-compose` — renders by `when` over the sealed types. Block nodes each emit a Composable; **inline nodes are flattened into a single `AnnotatedString`** and drawn with `BasicText` (which already supports `inlineContent`, but none is passed today).
  - `sample-chat` — KMP demo (android/jvm/js/wasmJs + iOS targets). Streams via `MarkdownEngine(dialect).append()` → `ParseDelta` → `MarkdownRendererState` → `Markdown(state, …)`.
- Versions: Kotlin 2.3.20, Compose Multiplatform 1.10.2, AGP 8.13.2, minSdk 23, compileSdk 36.
- Streaming constraint: a `$$` block may be temporarily unclosed mid-stream (there is an existing `BlockParserOpenFrameTest` precedent for open frames); recognition must degrade gracefully.

### Both libraries (verified)

Both are **Maven Central, MIT, native Compose Multiplatform** renderers (no WebView), covering all of the demo's target platforms.

| | huarangmeng/latex 1.4.7 | RaTeX-CMP 0.1.11 |
|---|---|---|
| Coordinates | `io.github.huarangmeng:latex-base/-parser/-renderer:1.4.7` | `io.github.darriousliu:ratex:0.1.11` |
| API | `Latex(latex, config = LatexConfig(fontSize, theme))` | `RaTeX(latex, fontSize, displayMode, color)` |
| Backing | Pure-Kotlin AST renderer | Rust/WASM engine (`DisplayList`) |
| Extra setup | none | desktop needs `ratex-native-<host>` `runtimeOnly`; web needs async init |
| Built against | Kotlin 2.3 / CMP 1.10 | Kotlin 2.x / CMP |

## 3. Design

### A. `markdown-core` — recognition

**New AST nodes** (matching existing field conventions: `BlockNode` carries `id: BlockId`, `range: TextRange`, `lineRange: LineRange`; `InlineNode` carries `id: InlineId`, `range: TextRange`):

```kotlin
@Immutable
data class MathBlock(            // $$...$$ or \[...\]
    override val id: BlockId,
    override val range: TextRange,
    override val lineRange: LineRange,
    val latex: String,           // content without delimiters
    val isClosed: Boolean,       // false while not yet closed (streaming)
    val delimiter: MathBlockDelimiter,
) : BlockNode

@Immutable
data class MathSpan(             // $...$ or \(...\)
    override val id: InlineId,
    override val range: TextRange,
    val latex: String,
    val delimiter: MathInlineDelimiter,
) : InlineNode

enum class MathBlockDelimiter { Dollar, Bracket }   // $$ , \[ \]
enum class MathInlineDelimiter { Dollar, Paren }    // $  , \( \)
```

**Feature flags**

- Add `mathBlocks: Boolean = false` to `MarkdownBlockFeatures`, `inlineMath: Boolean = false` to `MarkdownInlineFeatures`.
  - **Tradeoff:** these data classes are currently all-explicit (no defaults). We add the two new fields **with `= false` defaults** to preserve source compatibility for external constructors. This is the one intentional style deviation, justified by public-API compatibility.
- Add `MarkdownFeature.MathBlock`, `MarkdownFeature.InlineMath`; wire them into `MarkdownPreset.featureSet` (`if (blockFeatures.mathBlocks) add(...)`, `if (inlineFeatures.inlineMath) add(...)`).
- Existing presets `ChatFast` / `CommonMarkCore` / `GfmCompat` keep math **off** (existing snapshots/tests unchanged).
- Add a new `MarkdownPreset.GfmMath` and `MarkdownDialect.GfmMath` (= GFM features + `mathBlocks = true`, `inlineMath = true`).

**Parsing** (new branches gated by the flags, slotted into existing dispatch points):

- *Block* (`BlockParser`): a line beginning with `$$` (or `\[`) opens a math block; consume until the closing `$$` (or `\]`). Single-line `$$x$$` and multi-line both supported. If unclosed when input ends, emit `MathBlock(isClosed = false)` as an open frame (mirrors `FencedCodeBlock` streaming).
- *Inline* (`InlineParser` / `InlineScanner.parseSegment`): on an unescaped `$`, apply **pandoc-style** disambiguation — opening `$` not followed by whitespace, closing `$` not preceded by whitespace, non-empty content, `\$` is a literal dollar — to avoid treating currency ("$5 and $6") as math. `\(...\)` is unambiguous. If no valid closing delimiter is found (e.g. mid-stream), the run stays plain text; because each paragraph re-hydrates on every rebuild, it becomes a `MathSpan` once the closing delimiter streams in.

**Streaming/incremental:** block math participates in block framing (open frame when unclosed); inline math is re-hydrated per rebuild, so partial inline math renders as text until closed. New tests mirror the existing streaming/open-frame tests.

### B. `markdown-compose` — render slot

**Slot interface** (mirrors the existing `CodeHighlighter` extensibility style; this module adds **no** dependency on any LaTeX library):

```kotlin
interface MathRenderer {
    @Composable
    fun BlockMath(latex: String, modifier: Modifier)

    // Inline content requires a placeholder size up front, so the slot returns
    // an InlineTextContent directly — sizing knowledge stays in the implementation.
    fun inlineMath(latex: String, fontSize: TextUnit): InlineTextContent
}
```

- Add `mathRenderer: MathRenderer? = null` to all public `Markdown(...)` overloads, threaded down to `MarkdownBlock` and the inline pipeline.
- **Block:** add `is BlockNode.MathBlock ->` to `MarkdownBlock`. With a renderer, call `mathRenderer.BlockMath(block.latex, modifier)`; otherwise fall back to monospace raw source (show "streaming…" when `!isClosed`, consistent with code blocks).
- **Inline (core change):**
  - `appendInlineNodes` handles `InlineNode.MathSpan` by emitting `appendInlineContent(key, latex)` with a unique key, and recording `(key → InlineTextContent)` built via `mathRenderer.inlineMath(latex, fontSize)`.
  - `toAnnotatedString` changes to produce both the `AnnotatedString` and an `inlineContent: Map<String, InlineTextContent>` (e.g. a small result holder), built from the active `mathRenderer` and current text style.
  - `MarkdownText` forwards the map to `BasicText(text, …, inlineContent = …)`.
  - All `toAnnotatedString` call sites (Heading, Paragraph, TableCell, TableRow, …, ≈6) are updated to thread the inline-content map.
- **No renderer:** inline math also falls back to raw styled source, so the library works without any LaTeX dependency.

### C. `sample-chat` — two libraries + runtime toggle

- Dependencies: huarangmeng three artifacts in `commonMain`; `io.github.darriousliu:ratex:0.1.11` in `commonMain` plus `ratex-native-darwin-aarch64` (and the other host artifacts) as `runtimeOnly` in `jvmMain`.
- Two `MathRenderer` implementations:
  - `HuarangmengMathRenderer` → `Latex(latex, LatexConfig(fontSize, theme))`; inline wrapped in `InlineTextContent`.
  - `RaTeXMathRenderer` → `RaTeX(latex, fontSize, displayMode)`; inline size from `rememberBlockingRaTeXDisplayList` (non-web).
- UI: a selector (huarangmeng / RaTeX / raw placeholder) that swaps the `mathRenderer` passed to `Markdown`.
- Add a math sample script containing inline (`$`, `\(\)`) and block (`$$`, `\[\]`) examples; the demo engine uses `MarkdownDialect.GfmMath`.

### D. Testing & verification

- **core:** parser tests for block/inline math, all four delimiters, `\$` escaping, currency disambiguation, and streaming open-frame behavior — written TDD-first.
- **compose:** node→slot wiring and fallback behavior (no pixel-level tests of third-party UI).
- **manual verification:** run the JVM desktop demo on macOS and confirm both renderers display recognized math and the toggle works.

## 4. Risks / tradeoffs

1. **Inline placeholder sizing** is the main difficulty. RaTeX exposes a `DisplayList` size synchronously; if huarangmeng lacks a measurement API, fall back to a one-shot off-screen subcompose measurement. Web RaTeX needs async init — primary verification is on desktop; the web path is documented as a known limitation.
2. Adding defaulted fields to public feature data classes (see A) — accepted minor style deviation for API compatibility.
3. New `GfmMath` dialect instead of enabling math in `GfmCompat` — keeps existing snapshots stable.
4. RaTeX desktop native dependency is per-host; the demo includes the host artifact (`darwin-aarch64`) and notes the others.

## 5. Out of scope

- Full-document LaTeX (only math-mode expressions).
- Rendering math inside code spans/blocks (code stays code).
- Pixel-accurate cross-platform layout parity between the two libraries.
