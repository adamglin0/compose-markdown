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
        val block = assertIs<BlockNode.MathBlock>(firstBlock("\$\$x^2 + y^2\$\$\n"))
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
