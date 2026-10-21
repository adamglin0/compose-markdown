package com.adamglin.compose.markdown.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.ListType
import com.adamglin.compose.markdown.core.model.InlineNode
import dev.snipme.highlights.model.SyntaxLanguage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MarkdownHelpersTest {
    @Test
    fun streamingStateKeepsUnchangedBlockInstances() {
        val state = MarkdownState()
        state.append("first paragraph\n\nsecond")
        val first = state.document.blocks[0]
        val second = state.document.blocks[1]

        state.append(" grows")

        assertSame(first, state.document.blocks[0])
        assertNotSame(second, state.document.blocks[1])
        assertEquals("second grows", (state.document.blocks[1] as BlockNode.Paragraph).children.plainText())

        val grown = state.document.blocks[1]
        state.finish()
        assertSame(first, state.document.blocks[0])
        assertSame(grown, state.document.blocks[1])
    }

    @Test
    fun lateDefinitionReplacesOnlyTheBlocksUsingIt() {
        val state = MarkdownState(MarkdownDialect.CommonMarkCore)
        state.append("see [r]\n\nother\n\n")
        val user = state.document.blocks[0]
        val other = state.document.blocks[1]

        state.append("[r]: /u\n")

        assertNotSame(user, state.document.blocks[0])
        assertSame(other, state.document.blocks[1])
        val link = (state.document.blocks[0] as BlockNode.Paragraph).children.filterIsInstance<InlineNode.Link>().single()
        assertEquals("/u", link.destination)
    }

    @Test
    fun resetStartsAFreshDocument() {
        val state = MarkdownState()
        state.append("hello")
        state.finish()
        state.reset()
        assertTrue(state.document.blocks.isEmpty())
        state.append("again")
        assertEquals("again", (state.document.blocks.single() as BlockNode.Paragraph).children.plainText())
    }

    @Test
    fun orderedListsCountFromTheirStartNumber() {
        assertEquals(listOf("3.", "4.", "5."), orderedMarkers(firstList("3. a\n3. b\n3. c")))
        assertEquals(listOf("1)", "2)"), orderedMarkers(firstList("1) a\n1) b")))
    }

    @Test
    fun bulletsVaryByDepth() {
        assertEquals(
            listOf(Bullet.Disc, Bullet.Circle, Bullet.Square, Bullet.Disc),
            (0..3).map(::bulletForDepth),
        )
    }

    @Test
    fun linksCarryUrlAndOnlyACustomListenerWhenGiven() {
        val nodes = (parseMarkdown("go [docs](https://example.com)", MarkdownDialect.ChatFast).blocks.single() as BlockNode.Paragraph).children
        val styles = MarkdownStyles(MarkdownColors.Light, MarkdownTypography.Default).inline
        val model = nodes.toInlineRenderModel(styles, linkListener = null, embedMath = false, embedImages = false)

        assertEquals("go docs", model.text.text)
        val link = assertIs<LinkAnnotation.Url>(model.text.getLinkAnnotations(3, 7).single().item)
        assertEquals("https://example.com", link.url)
        assertNull(link.linkInteractionListener)
    }

    @Test
    fun imagesBecomeEmbedsOnlyWithImageContent() {
        val nodes: List<InlineNode> =
            (parseMarkdown("![alt *text*](https://e.com/a.png \"t\")", MarkdownDialect.ChatFast).blocks.single() as BlockNode.Paragraph).children
        val styles = MarkdownStyles(MarkdownColors.Light, MarkdownTypography.Default).inline

        assertEquals("alt text", nodes.toInlineRenderModel(styles, null, embedMath = false, embedImages = false).text.text)
        val embed = nodes.toInlineRenderModel(styles, null, embedMath = false, embedImages = true).embeds.single()
        assertEquals(MarkdownImage("https://e.com/a.png", "alt text", "t"), assertIs<InlineEmbed.Image>(embed).image)
    }

    @Test
    fun highlightedStringAppliesAndClampsTokens() {
        val annotated = buildHighlightedAnnotatedString(
            code = "val answer = 42",
            tokens = listOf(
                HighlightToken(start = -3, endExclusive = 3, color = Color.Red),
                HighlightToken(start = 13, endExclusive = 99, color = Color.Blue),
            ),
        )
        assertEquals(2, annotated.spanStyles.size)
        assertEquals(0, annotated.spanStyles[0].start)
        assertEquals(Color.Red, annotated.spanStyles[0].item.color)
        assertEquals(15, annotated.spanStyles[1].end)
    }

    @Test
    fun languageAliasesResolve() {
        assertEquals(SyntaxLanguage.KOTLIN, resolveSyntaxLanguage("kt"))
        assertEquals(SyntaxLanguage.JAVASCRIPT, resolveSyntaxLanguage("JS"))
        assertEquals(SyntaxLanguage.TYPESCRIPT, resolveSyntaxLanguage("tsx"))
        assertEquals(SyntaxLanguage.PYTHON, resolveSyntaxLanguage("py"))
        assertEquals(SyntaxLanguage.SHELL, resolveSyntaxLanguage("bash"))
        assertEquals(SyntaxLanguage.CPP, resolveSyntaxLanguage("c++"))
        assertEquals(SyntaxLanguage.CSHARP, resolveSyntaxLanguage("cs"))
        assertNull(resolveSyntaxLanguage("json"))
    }

    @Test
    fun defaultHighlighterUsesThemeColors() {
        val colors = MarkdownColors.Light
        val tokens = DefaultCodeHighlighter.highlight("val x = \"s\"", "kotlin", colors)!!
        assertTrue(tokens.any { it.color == colors.codeKeyword })
        assertTrue(tokens.any { it.color == colors.codeString })
        assertNull(DefaultCodeHighlighter.highlight("{}", "json", colors))
    }

    @Test
    fun columnsKeepPreferredWidthWhenTheyFit() {
        assertContentEquals(intArrayOf(80, 720), resolveTableColumnWidths(intArrayOf(80, 240), intArrayOf(40, 60), 800))
    }

    @Test
    fun overflowShrinksWidestColumnsButNotBelowTheirLongestWord() {
        assertContentEquals(intArrayOf(100, 100), resolveTableColumnWidths(intArrayOf(100, 300), intArrayOf(50, 50), 200))
        assertContentEquals(intArrayOf(150, 50), resolveTableColumnWidths(intArrayOf(300, 300), intArrayOf(150, 50), 200))
        assertEquals(200, resolveTableColumnWidths(intArrayOf(10, 500, 30), intArrayOf(10, 20, 30), 200).sum())
    }

    @Test
    fun tablesWiderThanTheirMinimumOverflowForScrolling() {
        val widths = resolveTableColumnWidths(intArrayOf(600, 1000), intArrayOf(600, 80), 400)
        assertContentEquals(intArrayOf(600, 200), widths)
    }
}

private fun orderedMarkers(list: BlockNode.ListBlock): List<String> =
    list.items.indices.map { orderedMarker(list.type as ListType.Ordered, it) }

private fun firstList(markdown: String): BlockNode.ListBlock =
    parseMarkdown(markdown, MarkdownDialect.ChatFast).blocks.filterIsInstance<BlockNode.ListBlock>().first()
