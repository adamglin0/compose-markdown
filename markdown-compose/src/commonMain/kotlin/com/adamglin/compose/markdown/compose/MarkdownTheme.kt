package com.adamglin.compose.markdown.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
internal data class MarkdownColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentSecondary: Color,
    val accentTertiary: Color,
    val border: Color,
    val borderMuted: Color,
)

@Immutable
internal data class MarkdownTypography(
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val labelMedium: TextStyle,
)

private val lightMarkdownColors = MarkdownColors(
    background = Color(0xFFF7F2E8),
    surface = Color(0xFFFCF8F1),
    surfaceMuted = Color(0xFFEFE5D7),
    textPrimary = Color(0xFF2C241B),
    textSecondary = Color(0xFF635748),
    accent = Color(0xFF1F6D63),
    accentSecondary = Color(0xFF9F5A2B),
    accentTertiary = Color(0xFF6F4BA8),
    border = Color(0xFFB6A793),
    borderMuted = Color(0xFFD3C5B2),
)

private val darkMarkdownColors = MarkdownColors(
    background = Color(0xFF161714),
    surface = Color(0xFF1C1F1B),
    surfaceMuted = Color(0xFF272B26),
    textPrimary = Color(0xFFF3EEDF),
    textSecondary = Color(0xFFD3CBBC),
    accent = Color(0xFF7BC5B8),
    accentSecondary = Color(0xFFF1A86E),
    accentTertiary = Color(0xFFC5A5FF),
    border = Color(0xFF6D726A),
    borderMuted = Color(0xFF444941),
)

private val LocalMarkdownColors = staticCompositionLocalOf<MarkdownColors> {
    error("MarkdownColors not provided")
}

private val LocalMarkdownTypography = staticCompositionLocalOf<MarkdownTypography> {
    error("MarkdownTypography not provided")
}

internal object MarkdownTheme {
    val colors: MarkdownColors
        @Composable get() = LocalMarkdownColors.current

    val typography: MarkdownTypography
        @Composable get() = LocalMarkdownTypography.current
}

@Composable
internal fun ProvideMarkdownTheme(
    content: @Composable () -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) darkMarkdownColors else lightMarkdownColors
    androidx.compose.runtime.CompositionLocalProvider(
        LocalMarkdownColors provides colors,
        LocalMarkdownTypography provides rememberMarkdownTypography(colors),
        content = content,
    )
}

@Composable
private fun rememberMarkdownTypography(
    colors: MarkdownColors,
): MarkdownTypography = MarkdownTypography(
    headlineLarge = TextStyle(
        color = colors.textPrimary,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = TextStyle(
        color = colors.textPrimary,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineSmall = TextStyle(
        color = colors.textPrimary,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        color = colors.textPrimary,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        color = colors.textPrimary,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        color = colors.textPrimary,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        color = colors.textPrimary,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        color = colors.textPrimary,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    labelMedium = TextStyle(
        color = colors.textSecondary,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
    ),
)
