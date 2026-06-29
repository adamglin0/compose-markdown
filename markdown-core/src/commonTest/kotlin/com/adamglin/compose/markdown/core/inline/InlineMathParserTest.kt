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
