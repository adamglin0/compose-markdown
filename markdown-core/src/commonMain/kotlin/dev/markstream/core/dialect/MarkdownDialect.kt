package dev.markstream.core.dialect

sealed interface MarkdownDialect {
    val id: String
    val featureSet: Set<MarkdownFeature>

    data object ChatFast : MarkdownDialect {
        override val id: String = "chat-fast"
        override val featureSet: Set<MarkdownFeature> = setOf(
            MarkdownFeature.Paragraph,
            MarkdownFeature.Heading,
            MarkdownFeature.FencedCodeBlock,
            MarkdownFeature.BlockQuote,
            MarkdownFeature.ListBlock,
            MarkdownFeature.InlineCode,
            MarkdownFeature.Emphasis,
            MarkdownFeature.Strong,
            MarkdownFeature.Link,
            MarkdownFeature.SoftBreak,
            MarkdownFeature.HardBreak,
            MarkdownFeature.Strikethrough,
        )
    }

    data object CommonMarkCore : MarkdownDialect {
        override val id: String = "commonmark-core"
        override val featureSet: Set<MarkdownFeature> = ChatFast.featureSet - MarkdownFeature.Strikethrough
    }

    data object GfmCompat : MarkdownDialect {
        override val id: String = "gfm-compat"
        override val featureSet: Set<MarkdownFeature> = ChatFast.featureSet
    }
}

enum class MarkdownFeature {
    Paragraph,
    Heading,
    FencedCodeBlock,
    BlockQuote,
    ListBlock,
    InlineCode,
    Emphasis,
    Strong,
    Link,
    SoftBreak,
    HardBreak,
    Strikethrough,
}
