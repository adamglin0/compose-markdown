package com.adamglin.compose.markdown.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class MarkdownColors(
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
data class MarkdownTypography(
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

@Immutable
data class MarkdownStyle(
    val colors: MarkdownColors? = null,
    val typography: MarkdownTypography? = null,
) {
    companion object {
        val Default: MarkdownStyle = MarkdownStyle()
    }
}

internal data class ResolvedMarkdownTheme(
    val colors: MarkdownColors,
    val typography: MarkdownTypography,
)

object MarkdownThemeDefaults {
    val LightColors: MarkdownColors = MarkdownColors(
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

    val DarkColors: MarkdownColors = MarkdownColors(
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

    fun colors(isDarkTheme: Boolean): MarkdownColors = if (isDarkTheme) DarkColors else LightColors

    fun typography(colors: MarkdownColors): MarkdownTypography = MarkdownTypography(
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
}

internal fun resolveMarkdownTheme(
    colors: MarkdownColors?,
    typography: MarkdownTypography?,
    inheritedColors: MarkdownColors?,
    inheritedTypography: MarkdownTypography?,
    isDarkTheme: Boolean,
): ResolvedMarkdownTheme {
    val baseColors = inheritedColors ?: MarkdownThemeDefaults.colors(isDarkTheme = isDarkTheme)
    val resolvedColors = colors ?: baseColors
    val resolvedTypography = when {
        typography != null -> typography
        colors != null -> MarkdownThemeDefaults.typography(colors = resolvedColors)
        inheritedTypography != null -> inheritedTypography
        else -> MarkdownThemeDefaults.typography(colors = resolvedColors)
    }
    return ResolvedMarkdownTheme(
        colors = resolvedColors,
        typography = resolvedTypography,
    )
}

private val LocalMarkdownColors = staticCompositionLocalOf<MarkdownColors?> {
    null
}

private val LocalMarkdownTypography = staticCompositionLocalOf<MarkdownTypography?> {
    null
}

object MarkdownTheme {
    val colors: MarkdownColors
        @Composable get() = LocalMarkdownColors.current ?: MarkdownThemeDefaults.colors(isDarkTheme = isSystemInDarkTheme())

    val typography: MarkdownTypography
        @Composable get() {
            val colors = colors
            return LocalMarkdownTypography.current ?: MarkdownThemeDefaults.typography(colors = colors)
        }
}

@Composable
fun ProvideMarkdownTheme(
    colors: MarkdownColors? = null,
    typography: MarkdownTypography? = null,
    content: @Composable () -> Unit,
) {
    val resolvedTheme = resolveMarkdownTheme(
        colors = colors,
        typography = typography,
        inheritedColors = LocalMarkdownColors.current,
        inheritedTypography = LocalMarkdownTypography.current,
        isDarkTheme = isSystemInDarkTheme(),
    )
    androidx.compose.runtime.CompositionLocalProvider(
        LocalMarkdownColors provides resolvedTheme.colors,
        LocalMarkdownTypography provides resolvedTheme.typography,
        content = content,
    )
}
