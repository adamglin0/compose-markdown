package com.adamglin.compose.markdown.sample.chat

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.adamglin.compose.markdown.compose.MathRenderer
import com.hrm.latex.renderer.Latex
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme
import io.ratex.compose.RaTeX

public enum class MathRendererOption(public val label: String) {
    Huarangmeng("huarangmeng"),
    RaTeX("RaTeX"),
    Placeholder("Raw"),
}

public class HuarangmengMathRenderer : MathRenderer {
    @Composable
    override fun BlockMath(latex: String, modifier: Modifier) {
        Latex(
            latex = latex,
            modifier = modifier,
            config = LatexConfig(theme = LatexTheme.auto()),
        )
    }

    @Composable
    override fun inlineMathContent(latex: String, fontSize: TextUnit): InlineTextContent =
        InlineTextContent(
            placeholder = Placeholder(
                width = (latex.length.coerceAtLeast(1) * 0.6f).em,
                height = 1.4.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            Latex(
                latex = latex,
                config = LatexConfig(fontSize = fontSize, theme = LatexTheme.auto()),
            )
        }
}

public class RaTeXMathRenderer : MathRenderer {
    @Composable
    override fun BlockMath(latex: String, modifier: Modifier) {
        RaTeX(
            latex = latex,
            modifier = modifier,
            displayMode = true,
        )
    }

    @Composable
    override fun inlineMathContent(latex: String, fontSize: TextUnit): InlineTextContent =
        InlineTextContent(
            placeholder = Placeholder(
                width = (latex.length.coerceAtLeast(1) * 0.6f).em,
                height = 1.4.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            RaTeX(
                latex = latex,
                fontSize = fontSize,
                displayMode = false,
            )
        }
}
