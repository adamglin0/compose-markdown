package dev.markstream.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import dev.markstream.core.model.BlockNode
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.CodeHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

interface CodeHighlighter {
    val engineId: String

    fun supports(language: String): Boolean

    suspend fun highlight(
        language: String?,
        code: CharSequence,
        previous: HighlightSnapshot? = null,
        mode: HighlightMode = HighlightMode.Final,
    ): HighlightResult
}

enum class HighlightMode {
    Preview,
    Final,
}

data class HighlightResult(
    val tokens: List<HighlightToken>,
    val snapshot: HighlightSnapshot? = null,
)

data class HighlightToken(
    val start: Int,
    val endExclusive: Int,
    val foregroundColor: Int? = null,
    val isBold: Boolean = false,
)

interface HighlightSnapshot

@Stable
internal data class MarkdownCodeHighlightTheme(
    val version: Int,
    val syntaxTheme: SyntaxTheme,
)

@Composable
internal fun rememberMarkdownCodeHighlighter(): CodeHighlighter {
    val theme = rememberMarkdownCodeHighlightTheme()
    return remember(theme.version) {
        HighlightsCodeHighlighter(theme = theme.syntaxTheme)
    }
}

@Composable
internal fun rememberHighlightedCodeBlock(
    block: BlockNode.FencedCodeBlock,
    highlighter: CodeHighlighter?,
): AnnotatedString {
    val plainText = remember(block.literal) {
        AnnotatedString(block.literal)
    }
    if (!block.isClosed) {
        return plainText
    }
    val languageHint = block.languageHint ?: return plainText
    val activeHighlighter = highlighter ?: return plainText
    if (!activeHighlighter.supports(languageHint)) {
        return plainText
    }

    var annotated by remember(block.id, block.literal, activeHighlighter) {
        mutableStateOf(plainText)
    }
    var previousSnapshot by remember(block.id, activeHighlighter) {
        mutableStateOf<HighlightSnapshot?>(null)
    }

    LaunchedEffect(block.id, block.literal, languageHint, activeHighlighter) {
        annotated = plainText
        val result = withContext(Dispatchers.Default) {
            activeHighlighter.highlight(
                language = languageHint,
                code = block.literal,
                previous = previousSnapshot,
                mode = HighlightMode.Final,
            )
        }
        previousSnapshot = result.snapshot
        annotated = buildHighlightedAnnotatedString(
            code = block.literal,
            tokens = result.tokens,
        )
    }

    return annotated
}

@Composable
private fun rememberMarkdownCodeHighlightTheme(): MarkdownCodeHighlightTheme {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.surface.luminance() < 0.5f

    return remember(colorScheme, isDark) {
        MarkdownCodeHighlightTheme(
            version = colorScheme.hashCode(),
            syntaxTheme = SyntaxTheme(
                key = if (isDark) "markstream-dark" else "markstream-light",
                code = colorScheme.onSurface.toRgbInt(),
                keyword = colorScheme.primary.toRgbInt(),
                string = colorScheme.tertiary.toRgbInt(),
                literal = colorScheme.secondary.toRgbInt(),
                comment = colorScheme.onSurfaceVariant.copy(alpha = 0.85f).toRgbInt(),
                metadata = colorScheme.primary.copy(alpha = 0.82f).toRgbInt(),
                multilineComment = colorScheme.onSurfaceVariant.copy(alpha = 0.8f).toRgbInt(),
                punctuation = colorScheme.primary.copy(alpha = 0.9f).toRgbInt(),
                mark = colorScheme.onSurface.toRgbInt(),
            ),
        )
    }
}

internal fun buildHighlightedAnnotatedString(
    code: CharSequence,
    tokens: List<HighlightToken>,
): AnnotatedString = buildAnnotatedString {
    val text = code.toString()
    append(text)
    tokens.forEach { token ->
        val start = token.start.coerceIn(0, text.length)
        val endExclusive = token.endExclusive.coerceIn(start, text.length)
        if (start >= endExclusive) {
            return@forEach
        }
        val style = SpanStyle(
            color = token.foregroundColor?.toComposeColor() ?: Color.Unspecified,
            fontWeight = if (token.isBold) FontWeight.Bold else null,
        )
        addStyle(style = style, start = start, end = endExclusive)
    }
}

private class HighlightsCodeHighlighter(
    private val theme: SyntaxTheme,
) : CodeHighlighter {
    override val engineId: String = "highlights"

    override fun supports(language: String): Boolean = resolveSyntaxLanguage(language) != null

    override suspend fun highlight(
        language: String?,
        code: CharSequence,
        previous: HighlightSnapshot?,
        mode: HighlightMode,
    ): HighlightResult {
        val syntaxLanguage = resolveSyntaxLanguage(language) ?: return HighlightResult(tokens = emptyList())
        val highlights = Highlights.Builder()
            .language(syntaxLanguage)
            .theme(theme)
            .build()
        return runCatching {
            highlights.setCode(code.toString())
            HighlightResult(tokens = highlights.getHighlights().mapNotNull(::toHighlightToken))
        }.getOrElse {
            HighlightResult(tokens = emptyList())
        }
    }
}

internal fun resolveSyntaxLanguage(language: String?): SyntaxLanguage? = when (language?.lowercase()) {
    "c" -> SyntaxLanguage.C
    "cpp" -> SyntaxLanguage.CPP
    "csharp" -> SyntaxLanguage.CSHARP
    "coffeescript" -> SyntaxLanguage.COFFEESCRIPT
    "dart" -> SyntaxLanguage.DART
    "go" -> SyntaxLanguage.GO
    "java" -> SyntaxLanguage.JAVA
    "javascript" -> SyntaxLanguage.JAVASCRIPT
    "kotlin" -> SyntaxLanguage.KOTLIN
    "perl" -> SyntaxLanguage.PERL
    "php" -> SyntaxLanguage.PHP
    "python" -> SyntaxLanguage.PYTHON
    "ruby" -> SyntaxLanguage.RUBY
    "rust" -> SyntaxLanguage.RUST
    "shell" -> SyntaxLanguage.SHELL
    "swift" -> SyntaxLanguage.SWIFT
    "typescript" -> SyntaxLanguage.TYPESCRIPT
    else -> null
}

private fun toHighlightToken(highlight: CodeHighlight): HighlightToken? = when (highlight) {
    is BoldHighlight -> HighlightToken(
        start = highlight.location.start,
        endExclusive = highlight.location.end,
        isBold = true,
    )

    is ColorHighlight -> HighlightToken(
        start = highlight.location.start,
        endExclusive = highlight.location.end,
        foregroundColor = highlight.rgb,
    )
}

private fun Int.toComposeColor(): Color = Color(
    red = ((this shr 16) and 0xFF) / 255f,
    green = ((this shr 8) and 0xFF) / 255f,
    blue = (this and 0xFF) / 255f,
    alpha = 1f,
)

private fun Color.toRgbInt(): Int {
    val red = (this.red * 255).roundToInt().coerceIn(0, 255)
    val green = (this.green * 255).roundToInt().coerceIn(0, 255)
    val blue = (this.blue * 255).roundToInt().coerceIn(0, 255)
    return (red shl 16) or (green shl 8) or blue
}
