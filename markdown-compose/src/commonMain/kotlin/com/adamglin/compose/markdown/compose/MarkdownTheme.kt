package com.adamglin.compose.markdown.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colors used by the Markdown renderer.
 *
 * The `code*` colors feed the syntax highlighter; everything else styles the rendered blocks.
 */
@Immutable
public data class MarkdownColors(
    val text: Color,
    val textSecondary: Color,
    val link: Color,
    val accent: Color,
    val surface: Color,
    val border: Color,
    val codeKeyword: Color,
    val codeString: Color,
    val codeLiteral: Color,
    val codeComment: Color,
) {
    public companion object {
        public val Light: MarkdownColors = MarkdownColors(
            text = Color(0xFF2C241B),
            textSecondary = Color(0xFF635748),
            link = Color(0xFF1F6D63),
            accent = Color(0xFF1F6D63),
            surface = Color(0xFFEFE5D7),
            border = Color(0xFFD3C5B2),
            codeKeyword = Color(0xFF1F6D63),
            codeString = Color(0xFF6F4BA8),
            codeLiteral = Color(0xFF9F5A2B),
            codeComment = Color(0xFF7C705F),
        )

        public val Dark: MarkdownColors = MarkdownColors(
            text = Color(0xFFF3EEDF),
            textSecondary = Color(0xFFD3CBBC),
            link = Color(0xFF7BC5B8),
            accent = Color(0xFF7BC5B8),
            surface = Color(0xFF272B26),
            border = Color(0xFF444941),
            codeKeyword = Color(0xFF7BC5B8),
            codeString = Color(0xFFC5A5FF),
            codeLiteral = Color(0xFFF1A86E),
            codeComment = Color(0xFFA39C8F),
        )
    }
}

/**
 * Text styles used by the Markdown renderer.
 *
 * Styles normally leave `color` unspecified so [MarkdownColors.text] applies; a color set here wins.
 * [code] styles fenced code blocks (its `lineHeight` is the code line height), [label] the code-block
 * language label.
 */
@Immutable
public data class MarkdownTypography(
    val heading1: TextStyle,
    val heading2: TextStyle,
    val heading3: TextStyle,
    val heading4: TextStyle,
    val heading5: TextStyle,
    val heading6: TextStyle,
    val body: TextStyle,
    val table: TextStyle,
    val code: TextStyle,
    val label: TextStyle,
) {
    internal fun heading(level: Int): TextStyle = when (level) {
        1 -> heading1
        2 -> heading2
        3 -> heading3
        4 -> heading4
        5 -> heading5
        else -> heading6
    }

    public companion object {
        public val Default: MarkdownTypography = MarkdownTypography(
            heading1 = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
            heading2 = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
            heading3 = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
            heading4 = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            heading5 = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
            heading6 = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            table = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
            code = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp),
            label = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
        )
    }
}

private val LocalMarkdownColors = staticCompositionLocalOf { MarkdownColors.Light }
private val LocalMarkdownTypography = staticCompositionLocalOf { MarkdownTypography.Default }
private val LocalMarkdownBlockSpacing = staticCompositionLocalOf { 12.dp }

/**
 * Themes every Markdown renderer in [content].
 *
 * Parameters default to the enclosing theme, so nested calls override only what they pass. Without
 * any provider the renderer uses [MarkdownColors.Light], [MarkdownTypography.Default] and 12.dp
 * block spacing; pick [MarkdownColors.Dark] (or your own colors) from your app's theme.
 */
@Composable
public fun MarkdownTheme(
    colors: MarkdownColors = MarkdownTheme.colors,
    typography: MarkdownTypography = MarkdownTheme.typography,
    blockSpacing: Dp = MarkdownTheme.blockSpacing,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMarkdownColors provides colors,
        LocalMarkdownTypography provides typography,
        LocalMarkdownBlockSpacing provides blockSpacing,
        content = content,
    )
}

public object MarkdownTheme {
    public val colors: MarkdownColors
        @Composable @ReadOnlyComposable get() = LocalMarkdownColors.current

    public val typography: MarkdownTypography
        @Composable @ReadOnlyComposable get() = LocalMarkdownTypography.current

    /** Vertical space between sibling blocks. */
    public val blockSpacing: Dp
        @Composable @ReadOnlyComposable get() = LocalMarkdownBlockSpacing.current
}
