package com.adamglin.compose.markdown.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.CodeHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxTheme

@Composable
internal actual fun rememberPlatformMarkdownCodeHighlighter(
    palette: MarkdownCodeHighlightPalette,
): CodeHighlighter = remember(palette.version) {
    HighlightsCodeHighlighter(theme = palette.toSyntaxTheme())
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

private fun MarkdownCodeHighlightPalette.toSyntaxTheme(): SyntaxTheme = SyntaxTheme(
    key = "compose-markdown-${version}",
    code = code,
    keyword = keyword,
    string = string,
    literal = literal,
    comment = comment,
    metadata = metadata,
    multilineComment = multilineComment,
    punctuation = punctuation,
    mark = mark,
)

private fun resolveSyntaxLanguage(language: String?): SyntaxLanguage? = when (language?.lowercase()) {
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
