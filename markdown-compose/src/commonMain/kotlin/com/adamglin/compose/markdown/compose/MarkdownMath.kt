package com.adamglin.compose.markdown.compose

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit

/**
 * Pluggable renderer for LaTeX/math regions recognized by the parser.
 *
 * markdown-compose ships only this interface; concrete implementations (e.g. backed by a
 * LaTeX library) live in the consuming app. When no renderer is supplied, math falls back
 * to its raw source text.
 */
interface MathRenderer {
    /** Renders a display/block formula (`$$…$$` or `\[…\]`). */
    @Composable
    fun BlockMath(latex: String, modifier: Modifier)

    /**
     * Builds the [InlineTextContent] used to lay an inline formula (`$…$` or `\(…\)`) inside a
     * line of text. The implementation owns the placeholder sizing (it may use [fontSize] and
     * `LocalDensity`).
     */
    @Composable
    fun inlineMathContent(latex: String, fontSize: TextUnit): InlineTextContent
}
