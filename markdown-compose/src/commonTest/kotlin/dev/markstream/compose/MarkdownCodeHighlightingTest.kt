package dev.markstream.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkdownCodeHighlightingTest {
    @Test
    fun buildHighlightedAnnotatedStringAppliesStyles() {
        val annotated = buildHighlightedAnnotatedString(
            code = "val answer = 42",
            tokens = listOf(
                HighlightToken(start = 0, endExclusive = 3, foregroundColor = 0x3366FF, isBold = true),
                HighlightToken(start = 13, endExclusive = 15, foregroundColor = 0xCC6600),
            ),
        )

        assertEquals("val answer = 42", annotated.text)
        assertEquals(2, annotated.spanStyles.size)
        assertEquals(Color(0xFF3366FF), annotated.spanStyles[0].item.color)
        assertEquals(FontWeight.Bold, annotated.spanStyles[0].item.fontWeight)
        assertEquals(Color(0xFFCC6600), annotated.spanStyles[1].item.color)
    }

    @Test
    fun buildHighlightedAnnotatedStringClampsInvalidRanges() {
        val annotated = buildHighlightedAnnotatedString(
            code = "abc",
            tokens = listOf(
                HighlightToken(start = -5, endExclusive = 2, foregroundColor = 0x123456),
                HighlightToken(start = 3, endExclusive = 9, foregroundColor = 0x654321),
            ),
        )

        assertEquals(1, annotated.spanStyles.size)
        assertEquals(0, annotated.spanStyles[0].start)
        assertEquals(2, annotated.spanStyles[0].end)
    }

    @Test
    fun resolveSyntaxLanguageReturnsNullForUnsupportedLanguage() {
        assertEquals(dev.snipme.highlights.model.SyntaxLanguage.KOTLIN, resolveSyntaxLanguage("kotlin"))
        assertEquals(dev.snipme.highlights.model.SyntaxLanguage.TYPESCRIPT, resolveSyntaxLanguage("typescript"))
        assertNull(resolveSyntaxLanguage("json"))
    }

    @Test
    fun resolveSyntaxLanguageSupportsNormalizedAliases() {
        assertTrue(resolveSyntaxLanguage("shell") != null)
        assertTrue(resolveSyntaxLanguage("python") != null)
    }
}
