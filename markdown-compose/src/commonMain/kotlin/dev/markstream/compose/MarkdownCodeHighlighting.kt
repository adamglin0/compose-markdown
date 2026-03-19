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
internal data class MarkdownCodeHighlightPalette(
    val version: Int,
    val code: Int,
    val keyword: Int,
    val string: Int,
    val literal: Int,
    val comment: Int,
    val metadata: Int,
    val multilineComment: Int,
    val punctuation: Int,
    val mark: Int,
)

@Composable
internal fun rememberMarkdownCodeHighlighter(): CodeHighlighter {
    val palette = rememberMarkdownCodeHighlightPalette()
    return rememberPlatformMarkdownCodeHighlighter(palette)
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
private fun rememberMarkdownCodeHighlightPalette(): MarkdownCodeHighlightPalette {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.surface.luminance() < 0.5f

    return remember(colorScheme, isDark) {
        MarkdownCodeHighlightPalette(
            version = colorScheme.hashCode(),
            code = colorScheme.onSurface.toRgbInt(),
            keyword = colorScheme.primary.toRgbInt(),
            string = colorScheme.tertiary.toRgbInt(),
            literal = colorScheme.secondary.toRgbInt(),
            comment = colorScheme.onSurfaceVariant.copy(alpha = 0.85f).toRgbInt(),
            metadata = colorScheme.primary.copy(alpha = 0.82f).toRgbInt(),
            multilineComment = colorScheme.onSurfaceVariant.copy(alpha = 0.8f).toRgbInt(),
            punctuation = colorScheme.primary.copy(alpha = 0.9f).toRgbInt(),
            mark = colorScheme.onSurface.toRgbInt(),
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

internal object PlainCodeHighlighter : CodeHighlighter {
    override val engineId: String = "plain"

    override fun supports(language: String): Boolean = false

    override suspend fun highlight(
        language: String?,
        code: CharSequence,
        previous: HighlightSnapshot?,
        mode: HighlightMode,
    ): HighlightResult = HighlightResult(tokens = emptyList())
}

@Composable
internal expect fun rememberPlatformMarkdownCodeHighlighter(
    palette: MarkdownCodeHighlightPalette,
): CodeHighlighter

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
