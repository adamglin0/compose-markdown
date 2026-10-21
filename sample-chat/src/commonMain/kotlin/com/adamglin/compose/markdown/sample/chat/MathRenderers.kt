package com.adamglin.compose.markdown.sample.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
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
    override fun InlineMath(latex: String, style: TextStyle) {
        Latex(
            latex = latex,
            config = LatexConfig(fontSize = style.fontSize, theme = LatexTheme.auto()),
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
    override fun InlineMath(latex: String, style: TextStyle) {
        RaTeX(
            latex = latex,
            fontSize = style.fontSize,
            displayMode = false,
        )
    }
}
