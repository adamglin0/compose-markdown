package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.MarkdownDocument
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class HoldUnsettledLinesTest {
    private val corpus: List<String> = listOf(
        "| a | b |\n|---|:-:|\n| 1 | 2 |\n| 3 | 4 |\n\nafter\n",
        "intro line\n| a | b |\n|---|---|\n| 1 | 2 |\n",
        "a | b\n--- | ---\nc | d",
        "| not | a table |\nplain text\n| x |\n",
        "| a | b |\n|---|\n| c |\n",
        "- item\n\n  | a | b |\n  |---|---|\n  | 1 | 2 |\n- next\n",
        "> | a |\n> |---|\n> | 1 |\nlazy\n\nend\n",
        "```\n| code |\n|---|\n```\n| a |\n|---|\n",
        "| a |\n|---|\n| 1 |\n# heading\n| b |\n",
        "text with | pipe\n\n|x|\n",
        "Here are options:\n- one\n- two\n\n1. first\n2. second\n",
        "Title\n---\n\nOther\n===\n\n***\n",
        "## Heading\n> quote\n> - [ ] task\n> - [x] done\n",
        "```kotlin\nval x = 1\n}\n```\n\n$$\nx^2\n$$\n",
        "- a\n  -\n  - b\n\n1) x\n10) y\n",
        "see [a] and [b]\n\n[a]: /u\n| x |\n|---|\n[b]: /v \"t\"\n- [a]\n",
        "[a]: /first\n-\n[a]\n\n| [a] |\n|---|\n| [a] |\n",
    )

    @Test
    fun everySnapshotIsAOneShotParseUpToItsVisibleEnd() {
        val random = Random(7)
        for (dialect in allDialects) {
            for (text in corpus) {
                for (chunks in listOf(charChunks(text), randomChunks(text, random))) {
                    holdViolation(dialect, chunks, random)?.let { fail("$dialect ${text.escaped()}: $it") }
                }
            }
        }
    }

    @Test
    fun aStreamedTableNeverShowsAsAParagraphOrAHalfRow() {
        val text = "| a | b |\n| --- | --- |\n| 1 | 2 |\n| 3 | 4 |\n"
        val engine = MarkdownEngine(holdUnsettledLines = true)
        val prefix = StringBuilder()
        for (char in text) {
            prefix.append(char)
            val blocks = engine.append(char.toString()).snapshot.document.blocks
            val completeLines = prefix.count { it == '\n' }
            when {
                completeLines < 2 -> assertEquals(emptyList(), blocks, "before the delimiter row ends: ${prefix.toString().escaped()}")
                else -> {
                    val table = blocks.single() as? BlockNode.TableBlock ?: fail("${prefix.toString().escaped()}: ${blocks.dump()}")
                    assertEquals(completeLines - 2, table.rows.size, "rows at ${prefix.toString().escaped()}")
                }
            }
        }
    }

    @Test
    fun aLoneListMarkerDoesNotTurnTheParagraphAboveIntoAHeading() {
        val engine = MarkdownEngine(holdUnsettledLines = true)
        for (chunk in listOf("Here are options:\n", "-", " ")) {
            val blocks = engine.append(chunk).snapshot.document.blocks
            assertTrue(blocks.single() is BlockNode.Paragraph, "after ${chunk.escaped()}: ${blocks.dump()}")
        }
        val blocks = engine.append("o").snapshot.document.blocks
        assertTrue(blocks.last() is BlockNode.ListBlock, blocks.dump())
    }

    @Test
    fun linesWithContentStillStreamCharacterByCharacter() {
        val engine = MarkdownEngine(holdUnsettledLines = true)
        engine.append("```\n| code")
        assertTrue(engine.snapshot().document.blocks.single() is BlockNode.FencedCodeBlock)
        assertTrue("| code" in engine.snapshot().document.blocks.dump())
        engine.reset()
        engine.append("plain | text")
        assertTrue("plain | text" in engine.snapshot().document.blocks.dump())
        engine.reset()
        engine.append("- a")
        assertEquals(3, engine.snapshot().document.sourceLength)
    }

    @Test
    fun releasedLinesStayShownWhileTheyGrowAndLaterLinesAreHeldAgain() {
        val engine = MarkdownEngine(holdUnsettledLines = true)
        engine.append("| a |\n|---|\n| 1")
        assertEquals(12, engine.snapshot().document.sourceLength)
        val released = engine.releaseHeldLines().snapshot
        assertEquals(15, released.document.sourceLength)
        assertEquals(1, (released.document.blocks.single() as BlockNode.TableBlock).rows.size)
        val grown = engine.append(" |").snapshot
        assertEquals(17, grown.document.sourceLength)
        assertSame(grown, engine.releaseHeldLines().snapshot, "nothing is held, so releasing changes nothing")
        assertEquals(18, engine.append("\n| 2").snapshot.document.sourceLength)
    }

    @Test
    fun heldTextThatGrowsKeepsTheSameDocumentAndBuildsNothing() {
        val engine = MarkdownEngine(holdUnsettledLines = true)
        val shown = engine.append("| a |\n|---|\n| 1").snapshot.document
        for (chunk in listOf("23", " | ", "45", "6")) {
            val delta = engine.append(chunk)
            assertSame(shown, delta.snapshot.document, "after ${chunk.escaped()}")
            assertEquals(emptyList(), delta.changedBlocks)
            assertEquals(0, delta.stats.builtBlocks)
            assertEquals(0, delta.stats.inlineParsedBlockCount)
        }
        // A held header candidate keeps the document too while the line after it grows.
        engine.reset()
        val beforeHeader = engine.append("text\n\n| a |\n").snapshot.document
        assertSame(beforeHeader, engine.append("|-").snapshot.document)
        assertSame(beforeHeader, engine.append("--").snapshot.document)
        assertTrue(engine.append("|\n").snapshot.document.blocks.last() is BlockNode.TableBlock)
    }

    @Test
    fun releasingAHeldHeaderStillHoldsTheRowBeingWrittenBelowIt() {
        val engine = MarkdownEngine(holdUnsettledLines = true)
        engine.append("| a | b |\n|--")
        assertEquals(0, engine.snapshot().document.sourceLength)
        val released = engine.releaseHeldLines().snapshot
        assertEquals(10, released.document.sourceLength)
        assertTrue(released.hasHeldLines)
        assertTrue(engine.append("-|---|\n").snapshot.document.blocks.single() is BlockNode.TableBlock)
    }

    @Test
    fun finishShowsHeldLines() {
        val engine = MarkdownEngine(holdUnsettledLines = true)
        engine.append("| a |\n| b")
        assertEquals(emptyList(), engine.snapshot().document.blocks)
        val finished = engine.finish().snapshot
        assertEquals(false, finished.hasHeldLines)
        assertEquals(MarkdownEngine().also { it.append("| a |\n| b") }.finish().snapshot.document.blocks.dump(), finished.document.blocks.dump())
    }

    /**
     * Each snapshot equals a one-shot parse of the source up to its visible end, which is a line start or the
     * end of the source, never moves back and never falls behind the stable prefix; releasing at random steps
     * keeps that true, and finish() equals a one-shot parse of the whole text.
     */
    private fun holdViolation(dialect: MarkdownDialect, chunks: List<String>, random: Random): String? {
        val engine = MarkdownEngine(dialect = dialect, holdUnsettledLines = true)
        val prefix = StringBuilder()
        var visibleEnd = 0
        for ((step, chunk) in chunks.withIndex()) {
            prefix.append(chunk)
            val snapshot = if (random.nextInt(8) == 0) {
                val held = engine.append(chunk).snapshot
                engine.releaseHeldLines().snapshot.also {
                    if (held.hasHeldLines && it.document.sourceLength <= held.document.sourceLength) {
                        return "step $step: release left the visible end at ${it.document.sourceLength}"
                    }
                }
            } else {
                engine.append(chunk).snapshot
            }
            if (snapshot.document.sourceLength != prefix.length && snapshot.document.sourceLength != 0 && prefix[snapshot.document.sourceLength - 1] != '\n') {
                return "step $step: visible end ${snapshot.document.sourceLength} is not a line start"
            }
            if (snapshot.document.sourceLength < visibleEnd) {
                return "step $step: visible end moved back from $visibleEnd to ${snapshot.document.sourceLength}"
            }
            visibleEnd = snapshot.document.sourceLength
            val expected = oneShot(dialect, prefix.substring(0, snapshot.document.sourceLength))
            val actual = snapshot.document.dump()
            if (snapshot.hasHeldLines != (snapshot.document.sourceLength < prefix.length)) {
                return "step $step: hasHeldLines is ${snapshot.hasHeldLines} with ${snapshot.document.sourceLength} of ${prefix.length} shown"
            }
            if (actual != expected) {
                return "step $step (+${chunk.escaped()}): expected ${expected.escaped()}, actual ${actual.escaped()}"
            }
        }
        val finished = engine.finish().snapshot.document.blocks.dump()
        val expected = MarkdownEngine(dialect = dialect).also { it.append(prefix.toString()) }.finish().snapshot.document.blocks.dump()
        return if (finished == expected) null else "finish: expected ${expected.escaped()}, actual ${finished.escaped()}"
    }

    private fun oneShot(dialect: MarkdownDialect, text: String): String =
        MarkdownEngine(dialect = dialect).append(text).snapshot.document.dump()

    private fun MarkdownDocument.dump(): String = "$sourceLength/$lineCount ${blocks.dump()}"
}
