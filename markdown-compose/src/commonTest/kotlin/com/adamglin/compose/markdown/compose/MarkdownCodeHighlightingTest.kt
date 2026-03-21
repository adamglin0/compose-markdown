package com.adamglin.compose.markdown.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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

    @Test
    fun markdownThemeDefaultsUseProvidedColorsForTypography() {
        val colors = MarkdownColors(
            background = Color(0xFF000001),
            surface = Color(0xFF000002),
            surfaceMuted = Color(0xFF000003),
            textPrimary = Color(0xFF123456),
            textSecondary = Color(0xFF654321),
            accent = Color(0xFF000004),
            accentSecondary = Color(0xFF000005),
            accentTertiary = Color(0xFF000006),
            border = Color(0xFF000007),
            borderMuted = Color(0xFF000008),
        )

        val typography = MarkdownThemeDefaults.typography(colors)

        assertEquals(colors.textPrimary, typography.bodyLarge.color)
        assertEquals(colors.textPrimary, typography.headlineLarge.color)
        assertEquals(colors.textSecondary, typography.labelMedium.color)
    }

    @Test
    fun markdownThemeDefaultsExposeStableLightAndDarkColors() {
        assertEquals(MarkdownThemeDefaults.LightColors, MarkdownThemeDefaults.colors(isDarkTheme = false))
        assertEquals(MarkdownThemeDefaults.DarkColors, MarkdownThemeDefaults.colors(isDarkTheme = true))
    }

    @Test
    fun markdownStyleResolveBuildsTypographyFromOverriddenColors() {
        val colors = MarkdownThemeDefaults.DarkColors.copy(
            textPrimary = Color(0xFF102030),
            textSecondary = Color(0xFF405060),
        )

        val resolved = resolveMarkdownTheme(
            colors = colors,
            typography = null,
            inheritedColors = MarkdownThemeDefaults.LightColors,
            inheritedTypography = MarkdownThemeDefaults.typography(MarkdownThemeDefaults.LightColors),
            isDarkTheme = false,
        )

        assertEquals(colors, resolved.colors)
        assertEquals(colors.textPrimary, resolved.typography.bodyLarge.color)
        assertEquals(colors.textSecondary, resolved.typography.labelMedium.color)
    }

    @Test
    fun markdownStyleResolvePrefersExplicitTypographyOverrides() {
        val typography = MarkdownThemeDefaults.typography(MarkdownThemeDefaults.LightColors).copy(
            bodyLarge = TextStyle(color = Color(0xFFABCDEF)),
        )

        val resolved = resolveMarkdownTheme(
            colors = null,
            typography = typography,
            inheritedColors = MarkdownThemeDefaults.LightColors,
            inheritedTypography = null,
            isDarkTheme = false,
        )

        assertEquals(MarkdownThemeDefaults.LightColors, resolved.colors)
        assertEquals(typography, resolved.typography)
    }

    @Test
    fun resolveMarkdownThemeInheritsOuterThemeWhenNoOverridesAreProvided() {
        val inheritedColors = MarkdownThemeDefaults.DarkColors
        val inheritedTypography = MarkdownThemeDefaults.typography(inheritedColors).copy(
            bodyMedium = TextStyle(color = Color(0xFF556677)),
        )

        val resolved = resolveMarkdownTheme(
            colors = null,
            typography = null,
            inheritedColors = inheritedColors,
            inheritedTypography = inheritedTypography,
            isDarkTheme = false,
        )

        assertEquals(inheritedColors, resolved.colors)
        assertEquals(inheritedTypography, resolved.typography)
    }
}
