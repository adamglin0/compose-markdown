package com.adamglin.compose.markdown.core.inline

import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.internal.LinkReferenceDefinition
import com.adamglin.compose.markdown.core.internal.normalizeReferenceLabel
import com.adamglin.compose.markdown.core.model.InlineNode
import com.adamglin.compose.markdown.core.model.TextRange

internal fun parseInline(
    markdown: String,
    dialect: MarkdownDialect = MarkdownDialect.GfmCompat,
    definitions: Map<String, String> = emptyMap(),
): List<InlineNode> {
    val referenceDefinitions = definitions.entries.associate { (label, destination) ->
        val normalized = requireNotNull(normalizeReferenceLabel(label))
        normalized to LinkReferenceDefinition(
            label = normalized,
            destination = destination,
            title = null,
            range = TextRange(0, 0),
        )
    }
    return InlineParser(dialect = dialect, referenceDefinitions = referenceDefinitions)
        .parse(literal = markdown, range = TextRange(0, markdown.length))
        .nodes
}

internal fun inlineShape(
    markdown: String,
    dialect: MarkdownDialect = MarkdownDialect.GfmCompat,
    definitions: Map<String, String> = emptyMap(),
): String = parseInline(markdown, dialect, definitions).shape()

/** Compact structural rendering: `text(..)`, `em(..)`, `link[dest "title"](..)`, `sb`, `hb`, ... */
internal fun List<InlineNode>.shape(): String = joinToString(separator = "") { node ->
    when (node) {
        is InlineNode.Text -> "text(${node.literal})"
        is InlineNode.Emphasis -> "em(${node.children.shape()})"
        is InlineNode.Strong -> "strong(${node.children.shape()})"
        is InlineNode.Strikethrough -> "del(${node.children.shape()})"
        is InlineNode.CodeSpan -> "code(${node.literal})"
        is InlineNode.Link -> "link[${node.destination}${node.title?.let { " \"$it\"" }.orEmpty()}](${node.children.shape()})"
        is InlineNode.Image -> "image[${node.destination}${node.title?.let { " \"$it\"" }.orEmpty()}](${node.alt.shape()})"
        is InlineNode.MathSpan -> "math(${node.latex})"
        is InlineNode.SoftBreak -> "sb"
        is InlineNode.HardBreak -> "hb"
    }
}
