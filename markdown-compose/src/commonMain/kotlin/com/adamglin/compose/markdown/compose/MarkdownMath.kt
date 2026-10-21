package com.adamglin.compose.markdown.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/**
 * Pluggable renderer for LaTeX regions recognized by the parser.
 *
 * markdown-compose ships only this interface; implementations (backed by a LaTeX library) live in
 * the consuming app. Without a renderer, math shows its source text. Math blocks that are still
 * streaming (unclosed) always show source text and are never passed to the renderer.
 */
@Stable
interface MathRenderer {
    /** Renders a display formula (`$$…$$` or `\[…\]`). */
    @Composable
    fun BlockMath(latex: String, modifier: Modifier)

    /**
     * Renders an inline formula (`$…$` or `\(…\)`) at its natural size. The surrounding text
     * measures the content and reserves exactly that much space in the line; [style] is the
     * surrounding text style (font size and color).
     */
    @Composable
    fun InlineMath(latex: String, style: TextStyle)
}
