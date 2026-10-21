package com.adamglin.compose.markdown.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Colors the source of closed fenced code blocks.
 *
 * Called off the main thread; must be pure. Return `null` when [language] is not supported, and
 * the block renders as plain text. Results are cached by code, language, colors and highlighter.
 */
public fun interface CodeHighlighter {
    public fun highlight(code: String, language: String, colors: MarkdownColors): List<HighlightToken>?
}

/** Colors `code[start, endExclusive)`. */
public data class HighlightToken(
    val start: Int,
    val endExclusive: Int,
    val color: Color,
)

/** [CodeHighlighter] backed by `dev.snipme:highlights`, using [MarkdownColors]' code colors. */
public object DefaultCodeHighlighter : CodeHighlighter {
    override fun highlight(code: String, language: String, colors: MarkdownColors): List<HighlightToken>? {
        val syntaxLanguage = resolveSyntaxLanguage(language) ?: return null
        val highlights = Highlights.Builder()
            .code(code)
            .language(syntaxLanguage)
            .theme(colors.toSyntaxTheme())
            .build()
            .getHighlights()
        return highlights.mapNotNull { highlight ->
            (highlight as? ColorHighlight)?.let {
                HighlightToken(
                    start = it.location.start,
                    endExclusive = it.location.end,
                    color = Color(0xFF000000.toInt() or it.rgb),
                )
            }
        }
    }
}

private fun MarkdownColors.toSyntaxTheme(): SyntaxTheme = SyntaxTheme(
    key = "compose-markdown",
    code = text.toRgb(),
    keyword = codeKeyword.toRgb(),
    string = codeString.toRgb(),
    literal = codeLiteral.toRgb(),
    comment = codeComment.toRgb(),
    metadata = codeKeyword.toRgb(),
    multilineComment = codeComment.toRgb(),
    punctuation = text.toRgb(),
    mark = text.toRgb(),
)

private fun Color.toRgb(): Int = toArgb() and 0xFFFFFF

/** Maps fence info-string languages (including common aliases) to the languages Highlights supports. */
internal fun resolveSyntaxLanguage(language: String): SyntaxLanguage? = when (language.lowercase()) {
    "c", "h" -> SyntaxLanguage.C
    "cpp", "c++", "cc", "cxx", "hpp", "hh", "hxx" -> SyntaxLanguage.CPP
    "csharp", "c#", "cs" -> SyntaxLanguage.CSHARP
    "coffeescript", "coffee" -> SyntaxLanguage.COFFEESCRIPT
    "dart" -> SyntaxLanguage.DART
    "go", "golang" -> SyntaxLanguage.GO
    "java" -> SyntaxLanguage.JAVA
    "javascript", "js", "jsx", "mjs", "cjs" -> SyntaxLanguage.JAVASCRIPT
    "kotlin", "kt", "kts" -> SyntaxLanguage.KOTLIN
    "perl", "pl" -> SyntaxLanguage.PERL
    "php" -> SyntaxLanguage.PHP
    "python", "py", "python3" -> SyntaxLanguage.PYTHON
    "ruby", "rb" -> SyntaxLanguage.RUBY
    "rust", "rs" -> SyntaxLanguage.RUST
    "shell", "sh", "bash", "zsh", "console", "shellscript" -> SyntaxLanguage.SHELL
    "swift" -> SyntaxLanguage.SWIFT
    "typescript", "ts", "tsx" -> SyntaxLanguage.TYPESCRIPT
    else -> null
}

internal fun buildHighlightedAnnotatedString(code: String, tokens: List<HighlightToken>): AnnotatedString =
    buildAnnotatedString {
        append(code)
        tokens.forEach { token ->
            val start = token.start.coerceIn(0, code.length)
            val end = token.endExclusive.coerceIn(start, code.length)
            if (start < end) addStyle(SpanStyle(color = token.color), start, end)
        }
    }

private data class HighlightKey(
    val highlighter: CodeHighlighter,
    val code: String,
    val language: String,
    val colors: MarkdownColors,
)

/**
 * Highlight results shared by every renderer. Only touched from the composition thread: lookups
 * happen in composition, inserts after the background highlight resumes on the effect dispatcher.
 */
private object HighlightCache {
    private const val MAX_ENTRIES = 128
    private val entries = LinkedHashMap<HighlightKey, AnnotatedString>()

    operator fun get(key: HighlightKey): AnnotatedString? {
        val value = entries.remove(key) ?: return null
        entries[key] = value
        return value
    }

    operator fun set(key: HighlightKey, value: AnnotatedString) {
        entries.remove(key)
        entries[key] = value
        if (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
    }
}

/**
 * Returns the highlighted [code], or plain text while highlighting runs, when [highlighter] is
 * null, or when the language is unknown. Cached results are returned synchronously, so blocks that
 * re-enter composition (e.g. when scrolled back into a lazy list) never flash plain text.
 */
@Composable
internal fun rememberHighlightedCode(
    code: String,
    language: String?,
    highlighter: CodeHighlighter?,
    colors: MarkdownColors,
): AnnotatedString {
    val plain = remember(code) { AnnotatedString(code) }
    if (highlighter == null || language == null) return plain
    val key = remember(highlighter, code, language, colors) { HighlightKey(highlighter, code, language, colors) }
    var highlighted by remember(key) { mutableStateOf(HighlightCache[key]) }
    if (highlighted == null) {
        LaunchedEffect(key) {
            val tokens = withContext(Dispatchers.Default) {
                runCatching { highlighter.highlight(code, language, colors) }.getOrNull()
            }
            val result = if (tokens == null) plain else buildHighlightedAnnotatedString(code, tokens)
            HighlightCache[key] = result
            highlighted = result
        }
    }
    return highlighted ?: plain
}
