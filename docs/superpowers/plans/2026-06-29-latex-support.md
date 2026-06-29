# LaTeX / Math Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recognize LaTeX math (`$…$`, `\(…\)`, `$$…$$`, `\[…\]`) in the parser, expose a pluggable `MathRenderer` slot in the Compose layer, and wire two real renderers behind a runtime toggle in the demo.

**Architecture:** `markdown-core` gains `BlockNode.MathBlock` / `InlineNode.MathSpan` nodes plus feature-flag-gated parsing in `BlockParser`/`InlineParser` (a new `GfmMath` dialect turns them on). `markdown-compose` adds a `MathRenderer` interface threaded through every `Markdown(…)` overload; block math calls it directly, inline math uses Compose `InlineTextContent`. `sample-chat` implements two `MathRenderer`s (huarangmeng/latex and RaTeX-CMP) and a toggle.

**Tech Stack:** Kotlin Multiplatform 2.3.20, Compose Multiplatform 1.10.2, kotlin.test. Targets: android, jvm, iosX64/iosArm64/iosSimulatorArm64, js(IR), wasmJs.

## Global Constraints

- Kotlin `2.3.20`, Compose Multiplatform `1.10.2`, AGP `8.13.2`, androidCompileSdk `36`, androidMinSdk `23`.
- All new `markdown-core` and `markdown-compose` code lives in `commonMain` (no platform-specific APIs).
- `markdown-compose` MUST NOT depend on any LaTeX rendering library — only the slot interface lives there. Real renderers live only in `sample-chat`.
- Existing dialects `ChatFast` / `CommonMarkCore` / `GfmCompat` MUST keep math **off**; math is enabled only by the new `MarkdownDialect.GfmMath`.
- New fields on `MarkdownBlockFeatures` / `MarkdownInlineFeatures` MUST default to `false` (source compatibility).
- Follow TDD: write the failing test, see it fail, implement minimally, see it pass, commit.
- Library coordinates (verified, Maven Central): `io.github.huarangmeng:latex-base/-parser/-renderer:1.4.7`; `io.github.darriousliu:ratex:0.1.11` + `io.github.darriousliu:ratex-native-darwin-aarch64:0.1.11`.
- Single-test command: `./gradlew :markdown-core:jvmTest --tests "<FQCN>"`. Whole core suite: `./gradlew :markdown-core:jvmTest`. Compose suite: `./gradlew :markdown-compose:jvmTest`. Desktop demo: `./gradlew :sample-chat:run`.

## File Structure

**markdown-core (recognition):**
- Modify `…/core/model/BlockNode.kt` — add `MathBlock` + `MathBlockDelimiter`.
- Modify `…/core/model/InlineNode.kt` — add `MathSpan` + `MathInlineDelimiter`.
- Modify `…/core/dialect/MarkdownDialect.kt` — add `mathBlocks`/`inlineMath` flags, `MarkdownFeature.MathBlock`/`InlineMath`, `GfmMath` preset + dialect.
- Modify `…/core/block/BlockParser.kt` — add `parseMathBlock`.
- Modify `…/core/inline/InlineParser.kt` — add inline `$…$` and `\(…\)` parsing; add `$` to `ESCAPABLE_CHARS`.
- Modify `…/core/engine/IncrementalMarkdownEngine.kt` — add pass-through branches if any exhaustive `when` requires them.
- Create tests: `…/core/block/MathBlockParserTest.kt`, `…/core/inline/InlineMathParserTest.kt`.

**markdown-compose (slot):**
- Create `…/compose/MarkdownMath.kt` — `MathRenderer` interface + inline render model + helpers.
- Modify `…/compose/MarkdownRenderer.kt` — thread `mathRenderer`, render `MathBlock`, route inline through `InlineTextContent`.

**sample-chat (demo):**
- Modify `gradle/libs.versions.toml`, `sample-chat/build.gradle.kts` — add deps.
- Create `…/sample/chat/MathRenderers.kt` — two `MathRenderer` implementations + option enum.
- Modify `…/sample/chat/SampleChatApp.kt` — toggle state + control, pass `mathRenderer`.
- Modify `…/sample/chat/SampleChatDefaults.kt` — `GfmMath` engine + new example definition.
- Create `sample-chat/markdown-examples/math-showcase.md` — example content (auto-bundled by the build).

---

## Task 1: Add MathBlock / MathSpan AST nodes

**Files:**
- Modify: `markdown-core/src/commonMain/kotlin/com/adamglin/compose/markdown/core/model/BlockNode.kt`
- Modify: `markdown-core/src/commonMain/kotlin/com/adamglin/compose/markdown/core/model/InlineNode.kt`
- Modify: `markdown-core/src/commonMain/kotlin/com/adamglin/compose/markdown/core/engine/IncrementalMarkdownEngine.kt` (only if compiler flags exhaustive `when`)

**Interfaces:**
- Produces: `BlockNode.MathBlock(id, range, lineRange, latex: String, isClosed: Boolean, delimiter: MathBlockDelimiter)`, `enum MathBlockDelimiter { Dollar, Bracket }`; `InlineNode.MathSpan(id, range, latex: String, delimiter: MathInlineDelimiter)`, `enum MathInlineDelimiter { Dollar, Paren }`.

- [ ] **Step 1: Add `MathBlock` to `BlockNode`**

Inside the `sealed interface BlockNode { … }` body (e.g. after `FencedCodeBlock`), add:

```kotlin
    @Immutable
    data class MathBlock(
        override val id: BlockId,
        override val range: TextRange,
        override val lineRange: LineRange,
        val latex: String,
        val isClosed: Boolean,
        val delimiter: MathBlockDelimiter,
    ) : BlockNode
```

At the bottom of the file (next to the other top-level enums like `ListStyle`), add:

```kotlin
enum class MathBlockDelimiter {
    Dollar,
    Bracket,
}
```

- [ ] **Step 2: Add `MathSpan` to `InlineNode`**

Inside `sealed interface InlineNode { … }` (e.g. after `CodeSpan`), add:

```kotlin
    @Immutable
    data class MathSpan(
        override val id: InlineId,
        override val range: TextRange,
        val latex: String,
        val delimiter: MathInlineDelimiter,
    ) : InlineNode
```

At the bottom of `InlineNode.kt` add:

```kotlin
enum class MathInlineDelimiter {
    Dollar,
    Paren,
}
```

- [ ] **Step 3: Compile core and resolve exhaustive `when` sites**

Run: `./gradlew :markdown-core:compileKotlinJvm`

For every "when expression must be exhaustive" error this surfaces inside `markdown-core` (most likely in `IncrementalMarkdownEngine.kt`'s inline/block hydration), add a leaf/pass-through branch:
- A `when (block)` over `BlockNode` → `is BlockNode.MathBlock -> <same handling as FencedCodeBlock>` (a `MathBlock` has no inline children, so mirror the no-inline-children path used by `FencedCodeBlock`/`ThematicBreak`).
- A `when (node)` over `InlineNode` → `is InlineNode.MathSpan -> <leaf, same as CodeSpan>` (no child recursion).

Expected: after adding the branches, the command succeeds.

- [ ] **Step 4: Commit**

```bash
git add markdown-core/src/commonMain/kotlin/com/adamglin/compose/markdown/core/model/BlockNode.kt \
        markdown-core/src/commonMain/kotlin/com/adamglin/compose/markdown/core/model/InlineNode.kt \
        markdown-core/src/commonMain/kotlin/com/adamglin/compose/markdown/core/engine/IncrementalMarkdownEngine.kt
git commit -m "feat(core): add MathBlock and MathSpan AST nodes"
```

---

## Task 2: Add math feature flags and the GfmMath dialect

**Files:**
- Modify: `markdown-core/src/commonMain/kotlin/com/adamglin/compose/markdown/core/dialect/MarkdownDialect.kt`
- Test: `markdown-core/src/commonTest/kotlin/com/adamglin/compose/markdown/core/api/DialectExtensionTest.kt`

**Interfaces:**
- Consumes: `MarkdownPreset.GfmCompat` feature values.
- Produces: `MarkdownBlockFeatures.mathBlocks: Boolean`, `MarkdownInlineFeatures.inlineMath: Boolean`, `MarkdownFeature.MathBlock`, `MarkdownFeature.InlineMath`, `MarkdownPreset.GfmMath`, `MarkdownDialect.GfmMath`.

- [ ] **Step 1: Write the failing test**

Append to `DialectExtensionTest.kt` (add imports `MarkdownFeature`, `MarkdownDialect` if absent):

```kotlin
    @Test
    fun gfmMathEnablesMathFeaturesAndOthersDoNot() {
        assertTrue(MarkdownDialect.GfmMath.blockFeatures.mathBlocks)
        assertTrue(MarkdownDialect.GfmMath.inlineFeatures.inlineMath)
        assertTrue(MarkdownDialect.GfmMath.supports(MarkdownFeature.MathBlock))
        assertTrue(MarkdownDialect.GfmMath.supports(MarkdownFeature.InlineMath))

        assertFalse(MarkdownDialect.GfmCompat.blockFeatures.mathBlocks)
        assertFalse(MarkdownDialect.GfmCompat.inlineFeatures.inlineMath)
        assertFalse(MarkdownDialect.ChatFast.supports(MarkdownFeature.MathBlock))
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :markdown-core:jvmTest --tests "com.adamglin.compose.markdown.core.api.DialectExtensionTest"`
Expected: compile failure (`mathBlocks` / `GfmMath` unresolved).

- [ ] **Step 3: Add the flags, feature enum entries, preset, and dialect**

In `MarkdownDialect.kt`:

(a) Add fields (with `= false` defaults) to the data classes:

```kotlin
@Immutable
data class MarkdownBlockFeatures(
    val atxHeadings: Boolean,
    val setextHeadings: Boolean,
    val fencedCodeBlocks: Boolean,
    val blockQuotes: Boolean,
    val lists: Boolean,
    val taskListItems: Boolean,
    val tables: Boolean,
    val rawHtml: Boolean,
    val mathBlocks: Boolean = false,
)

@Immutable
data class MarkdownInlineFeatures(
    val inlineCode: Boolean,
    val emphasis: Boolean,
    val strong: Boolean,
    val strikethrough: Boolean,
    val inlineLinks: Boolean,
    val referenceLinks: Boolean,
    val autolinks: Boolean,
    val bareAutolinks: Boolean,
    val images: Boolean,
    val softBreaks: Boolean,
    val hardBreaks: Boolean,
    val inlineMath: Boolean = false,
)
```

(b) Add enum entries to `MarkdownFeature` (before the closing brace):

```kotlin
    MathBlock,
    InlineMath,
```

(c) In `MarkdownPreset.featureSet` (`buildSet { … }`), add after the existing block/inline lines:

```kotlin
        if (blockFeatures.mathBlocks) add(MarkdownFeature.MathBlock)
        if (inlineFeatures.inlineMath) add(MarkdownFeature.InlineMath)
```

(d) Add a new preset (after `GfmCompat` in `sealed class MarkdownPreset`):

```kotlin
    data object GfmMath : MarkdownPreset(
        id = "gfm-math",
        blockFeatures = MarkdownBlockFeatures(
            atxHeadings = true,
            setextHeadings = true,
            fencedCodeBlocks = true,
            blockQuotes = true,
            lists = true,
            taskListItems = true,
            tables = true,
            rawHtml = false,
            mathBlocks = true,
        ),
        inlineFeatures = MarkdownInlineFeatures(
            inlineCode = true,
            emphasis = true,
            strong = true,
            strikethrough = true,
            inlineLinks = true,
            referenceLinks = true,
            autolinks = true,
            bareAutolinks = true,
            images = true,
            softBreaks = true,
            hardBreaks = true,
            inlineMath = true,
        ),
    )
```

(e) Add a new dialect (after `GfmCompat` in `sealed interface MarkdownDialect`):

```kotlin
    data object GfmMath : MarkdownDialect {
        override val id: String = "gfm-math"
        override val preset: MarkdownPreset = MarkdownPreset.GfmMath
        override val blockFeatures: MarkdownBlockFeatures = preset.blockFeatures
        override val inlineFeatures: MarkdownInlineFeatures = preset.inlineFeatures
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :markdown-core:jvmTest --tests "com.adamglin.compose.markdown.core.api.DialectExtensionTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add markdown-core/src/commonMain/kotlin/com/adamglin/compose/markdown/core/dialect/MarkdownDialect.kt \
        markdown-core/src/commonTest/kotlin/com/adamglin/compose/markdown/core/api/DialectExtensionTest.kt
git commit -m "feat(core): add math feature flags and GfmMath dialect"
```

---

## Task 3: Parse block math (`$$…$$`, `\[…\]`)

**Files:**
- Modify: `markdown-core/src/commonMain/kotlin/com/adamglin/compose/markdown/core/block/BlockParser.kt`
- Test: `markdown-core/src/commonTest/kotlin/com/adamglin/compose/markdown/core/block/MathBlockParserTest.kt` (create)

**Interfaces:**
- Consumes: `BlockNode.MathBlock`, `MathBlockDelimiter` (Task 1); `dialect.blockFeatures.mathBlocks` (Task 2); existing `ParsedBlock`, `withFrame`, `isFinal`, `rememberOpenFrames`, `allocateBlockId`, `List<ParserLine>.combinedRange()/combinedLineRange()`, `ParserLine.content/range/lineRange`.

- [ ] **Step 1: Write the failing test**

Create `MathBlockParserTest.kt`:

```kotlin
package com.adamglin.compose.markdown.core.block

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.MathBlockDelimiter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MathBlockParserTest {
    private fun firstBlock(markdown: String): BlockNode {
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmMath)
        return engine.append(markdown).snapshot.document.blocks.first()
    }

    @Test
    fun parsesMultiLineDollarBlock() {
        val block = assertIs<BlockNode.MathBlock>(firstBlock("$$\n\\frac{a}{b}\n$$\n"))
        assertEquals("\\frac{a}{b}", block.latex)
        assertEquals(MathBlockDelimiter.Dollar, block.delimiter)
        assertTrue(block.isClosed)
    }

    @Test
    fun parsesSingleLineDollarBlock() {
        val block = assertIs<BlockNode.MathBlock>(firstBlock("$$x^2 + y^2$$\n"))
        assertEquals("x^2 + y^2", block.latex)
        assertTrue(block.isClosed)
    }

    @Test
    fun parsesBracketBlock() {
        val block = assertIs<BlockNode.MathBlock>(firstBlock("\\[\n\\sum_{i=0}^n i\n\\]\n"))
        assertEquals("\\sum_{i=0}^n i", block.latex)
        assertEquals(MathBlockDelimiter.Bracket, block.delimiter)
        assertTrue(block.isClosed)
    }

    @Test
    fun unclosedDollarBlockStaysOpenWhileStreaming() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmMath)
        val block = assertIs<BlockNode.MathBlock>(
            engine.append("$$\n\\frac{a}{b}\n").snapshot.document.blocks.first(),
        )
        assertFalse(block.isClosed)
    }

    @Test
    fun disabledDialectDoesNotParseMath() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)
        val block = engine.append("$$\nx\n$$\n").snapshot.document.blocks.first()
        assertFalse(block is BlockNode.MathBlock)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :markdown-core:jvmTest --tests "com.adamglin.compose.markdown.core.block.MathBlockParserTest"`
Expected: FAIL — math blocks parse as paragraphs (`assertIs<BlockNode.MathBlock>` throws).

- [ ] **Step 3: Add `parseMathBlock` and dispatch it**

In `BlockParser.kt`, add the import (with the other model imports):

```kotlin
import com.adamglin.compose.markdown.core.model.MathBlockDelimiter
```

In `parseBlocks`, immediately after the `parseFencedCodeBlock` block (before `parseBlockQuote`), insert:

```kotlin
        val mathBlock = parseMathBlock(lines = lines, startIndex = index)
        if (mathBlock != null) {
            blocks += mathBlock.block
            index = mathBlock.nextIndex
            continue
        }
```

Add these private members (next to `parseFencedCodeBlock`):

```kotlin
    private data class MathBlockOpener(
        val delimiter: MathBlockDelimiter,
        val openMarker: String,
        val closeMarker: String,
    )

    private fun matchMathBlockOpener(content: String): MathBlockOpener? {
        val trimmed = content.trimStart()
        return when {
            trimmed.startsWith("$$") -> MathBlockOpener(MathBlockDelimiter.Dollar, "$$", "$$")
            trimmed.startsWith("\\[") -> MathBlockOpener(MathBlockDelimiter.Bracket, "\\[", "\\]")
            else -> null
        }
    }

    private fun parseMathBlock(lines: List<ParserLine>, startIndex: Int): ParsedBlock? {
        if (!dialect.blockFeatures.mathBlocks) {
            return null
        }
        val opener = matchMathBlockOpener(lines[startIndex].content) ?: return null
        val afterOpen = lines[startIndex].content.trimStart().removePrefix(opener.openMarker)

        // Single line: "$$ ... $$"
        val inlineClose = afterOpen.indexOf(opener.closeMarker)
        if (inlineClose >= 0) {
            val consumed = lines.subList(startIndex, startIndex + 1)
            return ParsedBlock(
                block = BlockNode.MathBlock(
                    id = allocateBlockId("math-block", consumed.first().range.start, opener.openMarker),
                    range = consumed.combinedRange(),
                    lineRange = consumed.combinedLineRange(),
                    latex = afterOpen.substring(0, inlineClose).trim(),
                    isClosed = true,
                    delimiter = opener.delimiter,
                ),
                nextIndex = startIndex + 1,
            )
        }

        var index = startIndex + 1
        var isClosed = false
        val bodyLines = mutableListOf<String>()
        if (afterOpen.isNotBlank()) {
            bodyLines += afterOpen
        }

        withFrame(marker = "math-block", startOffset = lines[startIndex].range.start) {
            while (index < lines.size) {
                val lineContent = lines[index].content
                val embeddedClose = lineContent.indexOf(opener.closeMarker)
                if (embeddedClose >= 0) {
                    val before = lineContent.substring(0, embeddedClose)
                    if (before.isNotBlank()) {
                        bodyLines += before
                    }
                    index += 1
                    isClosed = true
                    break
                }
                bodyLines += lineContent
                index += 1
            }
            if (!isClosed && !isFinal) {
                rememberOpenFrames()
            }
        }

        val consumed = lines.subList(startIndex, index)
        return ParsedBlock(
            block = BlockNode.MathBlock(
                id = allocateBlockId("math-block", consumed.first().range.start, opener.openMarker),
                range = consumed.combinedRange(),
                lineRange = consumed.combinedLineRange(),
                latex = bodyLines.joinToString(separator = "\n").trim(),
                isClosed = isClosed,
                delimiter = opener.delimiter,
            ),
            nextIndex = index,
        )
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :markdown-core:jvmTest --tests "com.adamglin.compose.markdown.core.block.MathBlockParserTest"`
Expected: PASS.

- [ ] **Step 5: Run the whole core suite (no regressions)**

Run: `./gradlew :markdown-core:jvmTest`
Expected: PASS (existing dialects unchanged → no snapshot/regression breakage).

- [ ] **Step 6: Commit**

```bash
git add markdown-core/src/commonMain/kotlin/com/adamglin/compose/markdown/core/block/BlockParser.kt \
        markdown-core/src/commonTest/kotlin/com/adamglin/compose/markdown/core/block/MathBlockParserTest.kt
git commit -m "feat(core): parse block math ($$ and \\[ \\])"
```

---

## Task 4: Parse inline math (`$…$`, `\(…\)`)

**Files:**
- Modify: `markdown-core/src/commonMain/kotlin/com/adamglin/compose/markdown/core/inline/InlineParser.kt`
- Test: `markdown-core/src/commonTest/kotlin/com/adamglin/compose/markdown/core/inline/InlineMathParserTest.kt` (create)

**Interfaces:**
- Consumes: `InlineNode.MathSpan`, `MathInlineDelimiter` (Task 1); `dialect.inlineFeatures.inlineMath` (Task 2); existing `ParsedInlineNode`, `inlineId(kind, range, salt)`, `toRange(start, end)`, the `parseSegment` loop and the `\\` branch.

- [ ] **Step 1: Write the failing test**

Create `InlineMathParserTest.kt`:

```kotlin
package com.adamglin.compose.markdown.core.inline

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.InlineNode
import com.adamglin.compose.markdown.core.model.MathInlineDelimiter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InlineMathParserTest {
    private fun paragraph(markdown: String): BlockNode.Paragraph {
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmMath)
        return assertIs(engine.append(markdown).snapshot.document.blocks.first())
    }

    @Test
    fun parsesDollarInlineMath() {
        val math = paragraph("energy is \$E = mc^2\$ today")
            .children.filterIsInstance<InlineNode.MathSpan>().single()
        assertEquals("E = mc^2", math.latex)
        assertEquals(MathInlineDelimiter.Dollar, math.delimiter)
    }

    @Test
    fun parsesParenInlineMath() {
        val math = paragraph("value \\(a+b\\) end")
            .children.filterIsInstance<InlineNode.MathSpan>().single()
        assertEquals("a+b", math.latex)
        assertEquals(MathInlineDelimiter.Paren, math.delimiter)
    }

    @Test
    fun currencyIsNotTreatedAsMath() {
        val children = paragraph("it costs \$5 and \$6 total").children
        assertTrue(children.none { it is InlineNode.MathSpan })
    }

    @Test
    fun escapedDollarIsLiteral() {
        val children = paragraph("price \\\$5").children
        assertTrue(children.none { it is InlineNode.MathSpan })
    }

    @Test
    fun disabledDialectDoesNotParseInlineMath() {
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)
        val paragraph = assertIs<BlockNode.Paragraph>(
            engine.append("a \$E=mc^2\$ b").snapshot.document.blocks.first(),
        )
        assertTrue(paragraph.children.none { it is InlineNode.MathSpan })
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :markdown-core:jvmTest --tests "com.adamglin.compose.markdown.core.inline.InlineMathParserTest"`
Expected: FAIL — no `MathSpan` nodes produced.

- [ ] **Step 3: Add `$` to escapable chars, add the dispatch branches, and the parse helpers**

In `InlineParser.kt`:

(a) Add import (with other model imports):

```kotlin
import com.adamglin.compose.markdown.core.model.MathInlineDelimiter
```

(b) In the companion `ESCAPABLE_CHARS` set, add `'$'`:

```kotlin
            val ESCAPABLE_CHARS: Set<Char> = setOf(
                '\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!', '~', '>', '<', '$',
            )
```

(c) In the `parseSegment` loop, inside `if (char == '\\') { … }` — immediately after the `parseBackslashHardBreak` block and **before** the `isEscapable` block — add:

```kotlin
                    if (dialect.inlineFeatures.inlineMath && index + 1 < endExclusive && text[index + 1] == '(') {
                        val parsed = parseInlineMathParen(index = index, endExclusive = endExclusive)
                        if (parsed != null) {
                            flushText(index)
                            nodes += parsed.node
                            index = parsed.nextIndex
                            textStart = index
                            continue
                        }
                    }
```

(d) In the same loop, after the backtick / code-span branch, add a `$` branch:

```kotlin
                if (char == '$' && dialect.inlineFeatures.inlineMath) {
                    val parsed = parseInlineMathDollar(index = index, endExclusive = endExclusive)
                    if (parsed != null) {
                        flushText(index)
                        nodes += parsed.node
                        index = parsed.nextIndex
                        textStart = index
                        continue
                    }
                }
```

(e) Add the two helper methods inside the `InlineScanner` inner class (next to `parseCodeSpan`):

```kotlin
        private fun parseInlineMathDollar(index: Int, endExclusive: Int): ParsedInlineNode? {
            val contentStart = index + 1
            if (contentStart >= endExclusive) {
                return null
            }
            val firstChar = text[contentStart]
            if (firstChar.isWhitespace() || firstChar == '$') {
                return null
            }
            var cursor = contentStart
            while (cursor < endExclusive) {
                val current = text[cursor]
                if (current == '\\') {
                    cursor += 2
                    continue
                }
                if (current == '$' && !text[cursor - 1].isWhitespace()) {
                    val latex = text.substring(contentStart, cursor)
                    if (latex.isEmpty()) {
                        return null
                    }
                    val range = toRange(index, cursor + 1)
                    return ParsedInlineNode(
                        node = InlineNode.MathSpan(
                            id = inlineId(kind = "math", range = range, salt = latex.hashCode().toLong()),
                            range = range,
                            latex = latex,
                            delimiter = MathInlineDelimiter.Dollar,
                        ),
                        nextIndex = cursor + 1,
                    )
                }
                cursor += 1
            }
            return null
        }

        private fun parseInlineMathParen(index: Int, endExclusive: Int): ParsedInlineNode? {
            val contentStart = index + 2
            var cursor = contentStart
            while (cursor + 1 < endExclusive) {
                if (text[cursor] == '\\' && text[cursor + 1] == ')') {
                    val latex = text.substring(contentStart, cursor).trim()
                    if (latex.isEmpty()) {
                        return null
                    }
                    val range = toRange(index, cursor + 2)
                    return ParsedInlineNode(
                        node = InlineNode.MathSpan(
                            id = inlineId(kind = "math", range = range, salt = latex.hashCode().toLong()),
                            range = range,
                            latex = latex,
                            delimiter = MathInlineDelimiter.Paren,
                        ),
                        nextIndex = cursor + 2,
                    )
                }
                cursor += 1
            }
            return null
        }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :markdown-core:jvmTest --tests "com.adamglin.compose.markdown.core.inline.InlineMathParserTest"`
Expected: PASS.

- [ ] **Step 5: Run the whole core suite**

Run: `./gradlew :markdown-core:jvmTest`
Expected: PASS. (Note: adding `'$'` to `ESCAPABLE_CHARS` makes `\$` render as a literal `$`; if an existing test asserts the old `\$`→`\$` behavior, update it to expect `$`.)

- [ ] **Step 6: Commit**

```bash
git add markdown-core/src/commonMain/kotlin/com/adamglin/compose/markdown/core/inline/InlineParser.kt \
        markdown-core/src/commonTest/kotlin/com/adamglin/compose/markdown/core/inline/InlineMathParserTest.kt
git commit -m "feat(core): parse inline math (\$…\$ and \\(…\\))"
```

---

## Task 5: MathRenderer slot + block-math rendering

**Files:**
- Create: `markdown-compose/src/commonMain/kotlin/com/adamglin/compose/markdown/compose/MarkdownMath.kt`
- Modify: `markdown-compose/src/commonMain/kotlin/com/adamglin/compose/markdown/compose/MarkdownRenderer.kt`
- Test: `markdown-compose/src/commonTest/kotlin/com/adamglin/compose/markdown/compose/MathBlockRenderingTest.kt` (create)

**Interfaces:**
- Produces: `interface MathRenderer { @Composable fun BlockMath(latex: String, modifier: Modifier); @Composable fun inlineMathContent(latex: String, fontSize: TextUnit): InlineTextContent }`. New `mathRenderer: MathRenderer? = null` parameter on every public `Markdown(...)` overload.
- Consumes: `BlockNode.MathBlock` (Task 1).

- [ ] **Step 1: Create the slot interface**

Create `MarkdownMath.kt`:

```kotlin
package com.adamglin.compose.markdown.compose

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit

/**
 * Pluggable renderer for LaTeX/math regions recognized by the parser.
 *
 * markdown-compose ships only this interface; concrete implementations (e.g. backed by a
 * LaTeX library) live in the consuming app. When no renderer is supplied, math falls back
 * to its raw source text.
 */
interface MathRenderer {
    /** Renders a display/block formula (`$$…$$` or `\[…\]`). */
    @Composable
    fun BlockMath(latex: String, modifier: Modifier)

    /**
     * Builds the [InlineTextContent] used to lay an inline formula (`$…$` or `\(…\)`) inside a
     * line of text. The implementation owns the placeholder sizing (it may use [fontSize] and
     * `LocalDensity`).
     */
    @Composable
    fun inlineMathContent(latex: String, fontSize: TextUnit): InlineTextContent
}
```

- [ ] **Step 2: Write the failing test**

Create `MathBlockRenderingTest.kt`. This is a Compose UI test verifying the slot is invoked for a `MathBlock`:

```kotlin
package com.adamglin.compose.markdown.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.em
import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import kotlin.test.Test

class MathBlockRenderingTest {
    private class FakeMathRenderer : MathRenderer {
        @androidx.compose.runtime.Composable
        override fun BlockMath(latex: String, modifier: Modifier) {
            BasicText(text = "BLOCK:$latex", modifier = modifier)
        }

        @androidx.compose.runtime.Composable
        override fun inlineMathContent(latex: String, fontSize: TextUnit): InlineTextContent =
            InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                BasicText("INLINE:$latex")
            }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun blockMathUsesTheSlot() = runComposeUiTest {
        val snapshot = MarkdownEngine(dialect = MarkdownDialect.GfmMath)
            .append("$$\n\\frac{a}{b}\n$$\n").snapshot
        setContent {
            Markdown(snapshot = snapshot, mathRenderer = FakeMathRenderer())
        }
        onNodeWithText("BLOCK:\\frac{a}{b}").assertIsDisplayed()
    }
}
```

If `markdown-compose/build.gradle.kts` lacks a UI-test dependency, add to `commonTest.dependencies`: `implementation(compose.uiTest)` and `@file:OptIn(ExperimentalWasmDsl::class)` is already present; ensure `compose` extension is available (the `org.jetbrains.compose` plugin provides `compose.uiTest`). If wiring UI tests proves heavy, replace this with a render-to-blocks assertion using `MarkdownRendererState` instead.

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :markdown-compose:jvmTest --tests "com.adamglin.compose.markdown.compose.MathBlockRenderingTest"`
Expected: FAIL — `mathRenderer` parameter does not exist / `MathBlock` not rendered.

- [ ] **Step 4: Thread `mathRenderer` and render the block**

In `MarkdownRenderer.kt`:

(a) Add imports:

```kotlin
import androidx.compose.foundation.layout.fillMaxWidth
```
(`fillMaxWidth` is already imported; skip if present.)

(b) Add `mathRenderer: MathRenderer? = null` to every public `Markdown(...)` overload signature (the 6 public ones) and forward it in each delegating call. Also add `mathRenderer: MathRenderer?` to the private `Markdown(blocks, …)` and pass it into `MarkdownBlock(...)`.

Example for the snapshot+style overload (apply the same pattern to all):

```kotlin
@Composable
fun Markdown(
    snapshot: MarkdownSnapshot,
    style: MarkdownStyle,
    modifier: Modifier = Modifier,
    state: MarkdownRendererState = rememberMarkdownRendererState(snapshot = snapshot),
    codeHighlighter: CodeHighlighter? = null,
    mathRenderer: MathRenderer? = null,
    onLinkClick: (String) -> Unit = {},
) {
    LaunchedEffect(snapshot) { state.render(snapshot) }
    Markdown(
        blocks = state.blocks,
        modifier = modifier,
        style = style,
        codeHighlighter = codeHighlighter,
        mathRenderer = mathRenderer,
        onLinkClick = onLinkClick,
    )
}
```

(c) Update the private renderer and `MarkdownBlock` to take and forward `mathRenderer`:

```kotlin
@Composable
private fun Markdown(
    blocks: List<RenderedMarkdownBlock>,
    modifier: Modifier,
    style: MarkdownStyle,
    codeHighlighter: CodeHighlighter?,
    mathRenderer: MathRenderer?,
    onLinkClick: (String) -> Unit,
) {
    // …unchanged up to the forEach…
                        MarkdownBlock(
                            block = renderedBlock.block,
                            styles = styles,
                            codeHighlighter = codeHighlighter ?: defaultCodeHighlighter!!,
                            mathRenderer = mathRenderer,
                            onLinkClick = { currentOnLinkClick.value(it) },
                        )
}
```

Add `mathRenderer: MathRenderer?` to `MarkdownBlock`'s parameters and forward it to every recursive `MarkdownBlock(...)` call (inside `Document`, `QuoteBlock`, `ListBlock`, `ListItemBlock`, `TableBlock`) — those helper composables (`QuoteBlock`, `ListBlock`, `ListItemBlock`, `TableBlock`) each gain a `mathRenderer: MathRenderer?` parameter passed through.

(d) Add the `MathBlock` branch to `MarkdownBlock`'s `when (block)`:

```kotlin
        is BlockNode.MathBlock -> MathBlockView(
            block = block,
            styles = styles,
            mathRenderer = mathRenderer,
            modifier = modifier,
        )
```

(e) Add the view composable (near `CodeBlock`):

```kotlin
@Composable
private fun MathBlockView(
    block: BlockNode.MathBlock,
    styles: MarkdownBlockStyles,
    mathRenderer: MathRenderer?,
    modifier: Modifier = Modifier,
) {
    if (mathRenderer != null) {
        mathRenderer.BlockMath(latex = block.latex, modifier = modifier.fillMaxWidth())
    } else {
        MarkdownText(
            text = buildString {
                append(block.latex)
                if (!block.isClosed) {
                    append("  streaming...")
                }
            },
            style = styles.codeBlockTextStyle,
            modifier = modifier,
        )
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :markdown-compose:jvmTest --tests "com.adamglin.compose.markdown.compose.MathBlockRenderingTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add markdown-compose/src/commonMain/kotlin/com/adamglin/compose/markdown/compose/MarkdownMath.kt \
        markdown-compose/src/commonMain/kotlin/com/adamglin/compose/markdown/compose/MarkdownRenderer.kt \
        markdown-compose/src/commonTest/kotlin/com/adamglin/compose/markdown/compose/MathBlockRenderingTest.kt \
        markdown-compose/build.gradle.kts
git commit -m "feat(compose): add MathRenderer slot and block-math rendering"
```

---

## Task 6: Inline math rendering via InlineTextContent

**Files:**
- Modify: `markdown-compose/src/commonMain/kotlin/com/adamglin/compose/markdown/compose/MarkdownRenderer.kt`

**Interfaces:**
- Consumes: `MathRenderer.inlineMathContent` (Task 5), `InlineNode.MathSpan` (Task 1).
- Produces: an inline pipeline that emits `appendInlineContent` placeholders for math and supplies them to `BasicText`.

- [ ] **Step 1: Write the failing test**

Append to `MathBlockRenderingTest.kt` (reuse `FakeMathRenderer`):

```kotlin
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun inlineMathUsesTheSlot() = runComposeUiTest {
        val snapshot = MarkdownEngine(dialect = MarkdownDialect.GfmMath)
            .append("before \$x+1\$ after").snapshot
        setContent {
            Markdown(snapshot = snapshot, mathRenderer = FakeMathRenderer())
        }
        onNodeWithText("INLINE:x+1").assertIsDisplayed()
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :markdown-compose:jvmTest --tests "com.adamglin.compose.markdown.compose.MathBlockRenderingTest.inlineMathUsesTheSlot"`
Expected: FAIL — inline math currently has no rendering path (MathSpan not handled; `BasicText` gets no `inlineContent`).

- [ ] **Step 3: Build an inline render model and feed BasicText**

In `MarkdownRenderer.kt`:

(a) Add imports:

```kotlin
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.key
```

(b) Add a render model + a builder that returns both the annotated string and the math span refs. Replace the body of `toAnnotatedString` usage with a model. Add:

```kotlin
@Stable
internal data class MathSpanRef(val key: String, val latex: String)

@Stable
internal data class InlineRenderModel(
    val text: AnnotatedString,
    val mathSpans: List<MathSpanRef>,
)

internal fun List<InlineNode>.toInlineRenderModel(
    styles: MarkdownInlineStyles,
    onLinkClick: (String) -> Unit,
): InlineRenderModel {
    val mathSpans = mutableListOf<MathSpanRef>()
    val text = buildAnnotatedString {
        appendInlineNodes(
            nodes = this@toInlineRenderModel,
            styles = styles,
            onLinkClick = onLinkClick,
            mathSpans = mathSpans,
        )
    }
    return InlineRenderModel(text = text, mathSpans = mathSpans)
}
```

(c) Change `appendInlineNodes` to take `mathSpans: MutableList<MathSpanRef>` and handle `MathSpan`; forward the list through the recursive calls:

```kotlin
private fun AnnotatedString.Builder.appendInlineNodes(
    nodes: List<InlineNode>,
    styles: MarkdownInlineStyles,
    onLinkClick: (String) -> Unit,
    mathSpans: MutableList<MathSpanRef>,
) {
    nodes.forEach { node ->
        when (node) {
            // …existing branches, passing `mathSpans = mathSpans` into every recursive
            //    appendInlineNodes(node.children, styles, onLinkClick, mathSpans) call…

            is InlineNode.MathSpan -> {
                val key = "math:${mathSpans.size}"
                mathSpans += MathSpanRef(key = key, latex = node.latex)
                appendInlineContent(key, node.latex)
            }
        }
    }
}
```

Keep the existing `toAnnotatedString` extension (used by `TableRow`'s plain joined text) but have it delegate:

```kotlin
internal fun List<InlineNode>.toAnnotatedString(
    styles: MarkdownInlineStyles = MarkdownInlineStyles(
        emphasis = SpanStyle(fontStyle = FontStyle.Italic),
        strong = SpanStyle(fontWeight = FontWeight.Bold),
        strike = SpanStyle(textDecoration = TextDecoration.LineThrough),
        code = SpanStyle(fontFamily = FontFamily.Monospace),
        link = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline)),
    ),
    onLinkClick: (String) -> Unit = {},
): AnnotatedString = toInlineRenderModel(styles, onLinkClick).text
```

(d) Add a composable that renders an `InlineRenderModel`, building the `inlineContent` map:

```kotlin
@Composable
private fun MarkdownInlineText(
    model: InlineRenderModel,
    style: TextStyle,
    mathRenderer: MathRenderer?,
    modifier: Modifier = Modifier,
) {
    val inlineContent = if (model.mathSpans.isEmpty() || mathRenderer == null) {
        emptyMap()
    } else {
        buildMap {
            model.mathSpans.forEach { ref ->
                key(ref.key) {
                    put(ref.key, mathRenderer.inlineMathContent(ref.latex, style.fontSize))
                }
            }
        }
    }
    BasicText(
        text = model.text,
        style = style,
        inlineContent = inlineContent,
        modifier = modifier.fillMaxWidth(),
    )
}
```

(Fallback note: when `mathRenderer == null`, the `appendInlineContent` placeholder has no map entry, so Compose renders its alternate text — the raw latex — automatically.)

(e) Route the inline-bearing block branches through `MarkdownInlineText`. For `Heading`, `Paragraph`, and `TableCell` in `MarkdownBlock`, replace the `MarkdownText(text = block.children.toAnnotatedString(...))` call with:

```kotlin
        is BlockNode.Paragraph -> MarkdownInlineText(
            model = block.children.toInlineRenderModel(styles.inline, onLinkClick),
            style = MarkdownTheme.typography.bodyLarge,
            mathRenderer = mathRenderer,
            modifier = modifier,
        )
```

For `Heading` keep its computed `style = when (block.level) …` and pass `mathRenderer = mathRenderer`. For `TableCell`, pass `style = MarkdownTheme.typography.bodyMedium`. `TableCell` is reached from `TableRowBlock`, which must also receive and forward `mathRenderer` (add the parameter to `TableRowBlock` and to the `TableBlock` calls). `TableRow`'s joined-preview branch stays on plain `toAnnotatedString` (no inline math in the collapsed preview).

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :markdown-compose:jvmTest --tests "com.adamglin.compose.markdown.compose.MathBlockRenderingTest"`
Expected: PASS (both block and inline tests).

- [ ] **Step 5: Run the whole compose suite**

Run: `./gradlew :markdown-compose:jvmTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add markdown-compose/src/commonMain/kotlin/com/adamglin/compose/markdown/compose/MarkdownRenderer.kt \
        markdown-compose/src/commonTest/kotlin/com/adamglin/compose/markdown/compose/MathBlockRenderingTest.kt
git commit -m "feat(compose): render inline math via InlineTextContent"
```

---

## Task 7: Add LaTeX library dependencies to the demo

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `sample-chat/build.gradle.kts`
- Modify: `settings.gradle.kts` (only if `mavenCentral()` is missing)

**Interfaces:**
- Produces: `libs.huarangmeng.latex.base/parser/renderer`, `libs.ratex`, `libs.ratex.native.darwin.aarch64` accessors.

- [ ] **Step 1: Verify `mavenCentral()` is present**

Run: `grep -n "mavenCentral" settings.gradle.kts build.gradle.kts`
Expected: at least one match under `dependencyResolutionManagement { repositories { … } }`. If absent, add `mavenCentral()` to that `repositories` block.

- [ ] **Step 2: Add versions and libraries to the catalog**

In `gradle/libs.versions.toml` add under `[versions]`:

```toml
huarangmeng-latex = "1.4.7"
ratex = "0.1.11"
```

Add under `[libraries]`:

```toml
huarangmeng-latex-base = { module = "io.github.huarangmeng:latex-base", version.ref = "huarangmeng-latex" }
huarangmeng-latex-parser = { module = "io.github.huarangmeng:latex-parser", version.ref = "huarangmeng-latex" }
huarangmeng-latex-renderer = { module = "io.github.huarangmeng:latex-renderer", version.ref = "huarangmeng-latex" }
ratex = { module = "io.github.darriousliu:ratex", version.ref = "ratex" }
ratex-native-darwin-aarch64 = { module = "io.github.darriousliu:ratex-native-darwin-aarch64", version.ref = "ratex" }
```

- [ ] **Step 3: Add dependencies to sample-chat**

In `sample-chat/build.gradle.kts`, in `val commonMain by getting { dependencies { … } }` add:

```kotlin
                implementation(libs.huarangmeng.latex.base)
                implementation(libs.huarangmeng.latex.parser)
                implementation(libs.huarangmeng.latex.renderer)
                implementation(libs.ratex)
```

In `val jvmMain by getting { dependencies { … } }` add (desktop native runtime for Apple Silicon host):

```kotlin
                runtimeOnly(libs.ratex.native.darwin.aarch64)
```

- [ ] **Step 4: Verify it resolves**

Run: `./gradlew :sample-chat:dependencies --configuration jvmRuntimeClasspath -q | grep -E "huarangmeng|ratex"`
Expected: the huarangmeng and ratex artifacts appear (downloads succeed).

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml sample-chat/build.gradle.kts settings.gradle.kts
git commit -m "build(sample): add huarangmeng/latex and RaTeX-CMP dependencies"
```

---

## Task 8: Implement the two MathRenderers

**Files:**
- Create: `sample-chat/src/commonMain/kotlin/com/adamglin/compose/markdown/sample/chat/MathRenderers.kt`

**Interfaces:**
- Consumes: `MathRenderer` (Task 5).
- Produces: `enum class MathRendererOption { Huarangmeng, RaTeX, Placeholder }`, `HuarangmengMathRenderer`, `RaTeXMathRenderer`.

> **API verification note:** The composable names/params below come from each library's published README. Before coding, confirm the exact imports/signatures from the resolved artifacts (`Latex` in `com.hrm.latex.renderer`, `LatexConfig`/`LatexTheme` in `com.hrm.latex.renderer.model`; `RaTeX` in `io.ratex.compose`, plus `rememberBlockingRaTeXDisplayList`). Adjust import paths and the `DisplayList` size accessor (Step 3) to match the actual API.

- [ ] **Step 1: Create the renderer file with the option enum and the placeholder/huarangmeng renderers**

Create `MathRenderers.kt`:

```kotlin
package com.adamglin.compose.markdown.sample.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.adamglin.compose.markdown.compose.MathRenderer
import com.hrm.latex.renderer.Latex
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme

enum class MathRendererOption(val label: String) {
    Huarangmeng("huarangmeng"),
    RaTeX("RaTeX"),
    Placeholder("Raw"),
}

class HuarangmengMathRenderer : MathRenderer {
    @Composable
    override fun BlockMath(latex: String, modifier: Modifier) {
        Box(modifier = modifier) {
            Latex(latex = latex, config = LatexConfig(theme = LatexTheme.auto()))
        }
    }

    @Composable
    override fun inlineMathContent(latex: String, fontSize: TextUnit): InlineTextContent =
        InlineTextContent(
            placeholder = Placeholder(
                width = if (fontSize.isSp) (latex.length.coerceAtLeast(1) * 0.6f).em else 4.em,
                height = 1.4.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            Latex(
                latex = latex,
                config = LatexConfig(fontSize = fontSize, theme = LatexTheme.auto()),
            )
        }
}
```

(`TextUnit.isSp` guards the em estimate. The em-based width is a first approximation; Task 10 verifies/tightens it.)

- [ ] **Step 2: Add the RaTeX renderer to the same file**

Append:

```kotlin
import io.ratex.compose.RaTeX

class RaTeXMathRenderer : MathRenderer {
    @Composable
    override fun BlockMath(latex: String, modifier: Modifier) {
        RaTeX(latex = latex, modifier = modifier, displayMode = true)
    }

    @Composable
    override fun inlineMathContent(latex: String, fontSize: TextUnit): InlineTextContent =
        InlineTextContent(
            placeholder = Placeholder(
                width = (latex.length.coerceAtLeast(1) * 0.6f).em,
                height = 1.4.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            RaTeX(latex = latex, fontSize = fontSize, displayMode = false)
        }
}
```

- [ ] **Step 3: (Optional refinement) size the inline placeholder from RaTeX's DisplayList**

If RaTeX exposes a synchronous `DisplayList` with measurable size, replace the em estimate using `rememberBlockingRaTeXDisplayList(latex, displayMode = false)` and `LocalDensity` to convert px→sp. Keep the em fallback if the size is unavailable. (Defer if the API check in the verification note shows no synchronous sizing.)

- [ ] **Step 4: Compile the demo**

Run: `./gradlew :sample-chat:compileKotlinJvm`
Expected: success. If an import is unresolved, fix it per the API verification note.

- [ ] **Step 5: Commit**

```bash
git add sample-chat/src/commonMain/kotlin/com/adamglin/compose/markdown/sample/chat/MathRenderers.kt
git commit -m "feat(sample): implement huarangmeng and RaTeX MathRenderers"
```

---

## Task 9: Demo toggle, GfmMath engine, and math example

**Files:**
- Modify: `sample-chat/src/commonMain/kotlin/com/adamglin/compose/markdown/sample/chat/SampleChatApp.kt`
- Modify: `sample-chat/src/commonMain/kotlin/com/adamglin/compose/markdown/sample/chat/SampleChatDefaults.kt`
- Create: `sample-chat/markdown-examples/math-showcase.md`
- Modify: `sample-chat/src/commonMain/kotlin/com/adamglin/compose/markdown/sample/chat/BlockSnapshotDebugText.kt` (only if it has an exhaustive `when (block)`)

**Interfaces:**
- Consumes: `MathRendererOption`, `HuarangmengMathRenderer`, `RaTeXMathRenderer` (Task 8); `Markdown(state, …, mathRenderer = …)` (Task 5); `MarkdownDialect.GfmMath` (Task 2).

- [ ] **Step 1: Switch the demo engine to GfmMath**

In `SampleChatDefaults.kt`, change `createMarkdownEngine`:

```kotlin
    fun createMarkdownEngine(): MarkdownEngine = MarkdownEngine(
        dialect = MarkdownDialect.GfmMath,
    )
```

- [ ] **Step 2: Add the math example file**

Create `sample-chat/markdown-examples/math-showcase.md`:

```markdown
# Math Showcase

Inline dollar math like $E = mc^2$ flows within the sentence, and so does
paren math \(a^2 + b^2 = c^2\).

Currency is left alone: it costs $5 and $6 total.

Display math with dollars:

$$
\frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
$$

And a bracket block:

\[
\sum_{i=1}^{n} i = \frac{n(n+1)}{2}
\]
```

- [ ] **Step 3: Register the example**

In `SampleChatDefaults.kt`, add to the `exampleDefinitions` list (e.g. first, so it's easy to find):

```kotlin
        SampleScriptDefinition(
            id = "math-showcase",
            title = "Math Showcase",
            summary = "Inline and block LaTeX with dollar and bracket delimiters, plus currency that must stay text.",
            resourcePath = "markdown-examples/math-showcase.md",
        ),
```

- [ ] **Step 4: Add toggle state and pass the renderer**

In `SampleChatApp.kt`, near the other `remember` state (after `var statusText …`), add:

```kotlin
        var mathRendererOption by remember { mutableStateOf(MathRendererOption.Huarangmeng) }
        val huarangmengRenderer = remember { HuarangmengMathRenderer() }
        val ratexRenderer = remember { RaTeXMathRenderer() }
        val mathRenderer = when (mathRendererOption) {
            MathRendererOption.Huarangmeng -> huarangmengRenderer
            MathRendererOption.RaTeX -> ratexRenderer
            MathRendererOption.Placeholder -> null
        }
```

Thread `mathRendererOption` + an `onMathRendererChange: (MathRendererOption) -> Unit` into the `Toolbar` composable (add parameters), and pass `mathRenderer` down to wherever `PreviewCanvas` calls `Markdown`. Update that call:

```kotlin
Markdown(
    state = rendererState,
    modifier = Modifier.fillMaxWidth(),
    mathRenderer = mathRenderer,
    onLinkClick = onLinkClick,
)
```

(`PreviewCanvas` gains a `mathRenderer: MathRenderer?` parameter; pass it from the call site.)

- [ ] **Step 5: Add the toggle control in the Toolbar**

In `Toolbar`, add parameters `mathRendererOption: MathRendererOption` and `onMathRendererChange: (MathRendererOption) -> Unit`, and add a row of buttons below the existing controls:

```kotlin
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText(
                    text = "Math renderer:",
                    style = SampleChatTheme.typography.bodyMedium,
                    color = SampleChatTheme.colors.textSecondary,
                )
                MathRendererOption.entries.forEach { option ->
                    AppButton(
                        text = option.label,
                        onClick = { onMathRendererChange(option) },
                        filled = option == mathRendererOption,
                    )
                }
            }
```

(`MathRendererOption.entries` requires Kotlin 1.9+ enum entries — available on 2.3.20. If `AppButton` has no `filled`-as-selection styling, pass `filled = option == mathRendererOption` as already shown.)

- [ ] **Step 6: Compile the demo and fix any exhaustive `when`**

Run: `./gradlew :sample-chat:compileKotlinJvm`
For any "when must be exhaustive" over `BlockNode` (e.g. in `BlockSnapshotDebugText.kt`), add:

```kotlin
        is BlockNode.MathBlock -> /* mirror the FencedCodeBlock debug branch, printing block.latex */
```

Expected: success.

- [ ] **Step 7: Commit**

```bash
git add sample-chat/src/commonMain/kotlin/com/adamglin/compose/markdown/sample/chat/SampleChatApp.kt \
        sample-chat/src/commonMain/kotlin/com/adamglin/compose/markdown/sample/chat/SampleChatDefaults.kt \
        sample-chat/src/commonMain/kotlin/com/adamglin/compose/markdown/sample/chat/BlockSnapshotDebugText.kt \
        sample-chat/markdown-examples/math-showcase.md
git commit -m "feat(sample): runtime math-renderer toggle, GfmMath engine, math example"
```

---

## Task 10: Manual verification on desktop

**Files:** none (verification only).

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew :markdown-core:jvmTest :markdown-compose:jvmTest`
Expected: all PASS.

- [ ] **Step 2: Launch the desktop demo**

Run: `./gradlew :sample-chat:run`
Expected: the window opens.

- [ ] **Step 3: Verify behavior**

- Select the "Math Showcase" example.
- Confirm inline `$E = mc^2$` and `\(a^2 + b^2 = c^2\)` render as formulas within the text line.
- Confirm "it costs $5 and $6" renders as plain text (no formula).
- Confirm both display blocks (`$$…$$` and `\[…\]`) render as centered formulas.
- Switch the "Math renderer" toggle between huarangmeng / RaTeX / Raw and confirm the rendering changes (Raw shows the literal LaTeX source).
- If inline placeholder sizing looks clipped or over-spaced, tighten the `em` factors in `MathRenderers.kt` (Task 8) and re-run.

- [ ] **Step 4: Commit any sizing tweaks**

```bash
git add sample-chat/src/commonMain/kotlin/com/adamglin/compose/markdown/sample/chat/MathRenderers.kt
git commit -m "fix(sample): tune inline math placeholder sizing"
```

---

## Self-Review

**Spec coverage:**
- Recognition `$`/`$$`/`\(`/`\[` → Tasks 3 (block `$$`,`\[`) + 4 (inline `$`,`\(`). ✓
- New nodes → Task 1. Feature flags + `GfmMath` → Task 2. ✓
- Render slot (block + inline true-inline via `InlineTextContent`) → Tasks 5 + 6. ✓
- markdown-compose has no LaTeX dependency → slot interface only (Task 5); libs added only to sample-chat (Task 7). ✓
- Demo runtime toggle of huarangmeng / RaTeX / placeholder → Tasks 8 + 9. ✓
- Streaming open-frame for unclosed `$$` → Task 3 Step 1/3 (`unclosedDollarBlockStaysOpen…`). ✓
- Currency / `\$` disambiguation → Task 4 tests. ✓
- Verification on desktop → Task 10. ✓

**Placeholder scan:** No "TBD/TODO". The only deferred items are explicit, justified runtime checks: external-library import/signature confirmation (Task 8 note) and inline-size tuning (Task 10) — these require the resolved artifacts/running app and cannot be pre-computed.

**Type consistency:** `MathRenderer.BlockMath(latex, modifier)` / `inlineMathContent(latex, fontSize)` used identically in Tasks 5, 6, 8. `BlockNode.MathBlock(latex, isClosed, delimiter)` and `InlineNode.MathSpan(latex, delimiter)` consistent across Tasks 1, 3, 4, 5, 6. `MathRendererOption` enum consistent across Tasks 8, 9. `MarkdownDialect.GfmMath` consistent across Tasks 2, 3, 4, 9.
