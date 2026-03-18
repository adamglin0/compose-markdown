package dev.markstream.core.dialect

sealed interface MarkdownDialect {
    val id: String
    val preset: MarkdownPreset
    val blockFeatures: MarkdownBlockFeatures
    val inlineFeatures: MarkdownInlineFeatures

    fun supports(feature: MarkdownFeature): Boolean = feature in preset.featureSet

    data object ChatFast : MarkdownDialect {
        override val id: String = "chat-fast"
        override val preset: MarkdownPreset = MarkdownPreset.ChatFast
        override val blockFeatures: MarkdownBlockFeatures = preset.blockFeatures
        override val inlineFeatures: MarkdownInlineFeatures = preset.inlineFeatures
    }

    data object CommonMarkCore : MarkdownDialect {
        override val id: String = "commonmark-core"
        override val preset: MarkdownPreset = MarkdownPreset.CommonMarkCore
        override val blockFeatures: MarkdownBlockFeatures = preset.blockFeatures
        override val inlineFeatures: MarkdownInlineFeatures = preset.inlineFeatures
    }

    data object GfmCompat : MarkdownDialect {
        override val id: String = "gfm-compat"
        override val preset: MarkdownPreset = MarkdownPreset.GfmCompat
        override val blockFeatures: MarkdownBlockFeatures = preset.blockFeatures
        override val inlineFeatures: MarkdownInlineFeatures = preset.inlineFeatures
    }
}

sealed class MarkdownPreset(
    val id: String,
    val blockFeatures: MarkdownBlockFeatures,
    val inlineFeatures: MarkdownInlineFeatures,
) {
    val featureSet: Set<MarkdownFeature> = buildSet {
        add(MarkdownFeature.Paragraph)
        if (blockFeatures.atxHeadings) add(MarkdownFeature.AtxHeading)
        if (blockFeatures.setextHeadings) add(MarkdownFeature.SetextHeading)
        if (blockFeatures.fencedCodeBlocks) add(MarkdownFeature.FencedCodeBlock)
        if (blockFeatures.blockQuotes) add(MarkdownFeature.BlockQuote)
        if (blockFeatures.lists) add(MarkdownFeature.ListBlock)
        if (blockFeatures.taskListItems) add(MarkdownFeature.TaskListItem)
        if (blockFeatures.tables) add(MarkdownFeature.Table)
        if (inlineFeatures.inlineCode) add(MarkdownFeature.InlineCode)
        if (inlineFeatures.emphasis) add(MarkdownFeature.Emphasis)
        if (inlineFeatures.strong) add(MarkdownFeature.Strong)
        if (inlineFeatures.inlineLinks) add(MarkdownFeature.Link)
        if (inlineFeatures.referenceLinks) add(MarkdownFeature.ReferenceLink)
        if (inlineFeatures.softBreaks) add(MarkdownFeature.SoftBreak)
        if (inlineFeatures.hardBreaks) add(MarkdownFeature.HardBreak)
        if (inlineFeatures.strikethrough) add(MarkdownFeature.Strikethrough)
        if (inlineFeatures.images) add(MarkdownFeature.Image)
        if (!blockFeatures.rawHtml) add(MarkdownFeature.RawHtmlDisabled)
    }

    data object ChatFast : MarkdownPreset(
        id = "chat-fast",
        blockFeatures = MarkdownBlockFeatures(
            atxHeadings = true,
            setextHeadings = true,
            fencedCodeBlocks = true,
            blockQuotes = true,
            lists = true,
            taskListItems = true,
            tables = true,
            rawHtml = false,
        ),
        inlineFeatures = MarkdownInlineFeatures(
            inlineCode = true,
            emphasis = true,
            strong = true,
            strikethrough = true,
            inlineLinks = true,
            referenceLinks = false,
            autolinks = true,
            bareAutolinks = true,
            images = true,
            softBreaks = true,
            hardBreaks = true,
        ),
    )

    data object CommonMarkCore : MarkdownPreset(
        id = "commonmark-core",
        blockFeatures = MarkdownBlockFeatures(
            atxHeadings = true,
            setextHeadings = true,
            fencedCodeBlocks = true,
            blockQuotes = true,
            lists = true,
            taskListItems = false,
            tables = false,
            rawHtml = false,
        ),
        inlineFeatures = MarkdownInlineFeatures(
            inlineCode = true,
            emphasis = true,
            strong = true,
            strikethrough = false,
            inlineLinks = true,
            referenceLinks = true,
            autolinks = true,
            bareAutolinks = true,
            images = true,
            softBreaks = true,
            hardBreaks = true,
        ),
    )

    data object GfmCompat : MarkdownPreset(
        id = "gfm-compat",
        blockFeatures = MarkdownBlockFeatures(
            atxHeadings = true,
            setextHeadings = true,
            fencedCodeBlocks = true,
            blockQuotes = true,
            lists = true,
            taskListItems = true,
            tables = true,
            rawHtml = false,
        ),
        inlineFeatures = MarkdownInlineFeatures(
            inlineCode = true,
            emphasis = true,
            strong = true,
            strikethrough = true,
            inlineLinks = true,
            referenceLinks = true,
            autolinks = true,
            bareAutolinks = true,
            images = true,
            softBreaks = true,
            hardBreaks = true,
        ),
    )
}

data class MarkdownBlockFeatures(
    val atxHeadings: Boolean,
    val setextHeadings: Boolean,
    val fencedCodeBlocks: Boolean,
    val blockQuotes: Boolean,
    val lists: Boolean,
    val taskListItems: Boolean,
    val tables: Boolean,
    val rawHtml: Boolean,
)

data class MarkdownInlineFeatures(
    val inlineCode: Boolean,
    val emphasis: Boolean,
    val strong: Boolean,
    val strikethrough: Boolean,
    val inlineLinks: Boolean,
    val referenceLinks: Boolean,
    val autolinks: Boolean,
    val bareAutolinks: Boolean,
    val images: Boolean,
    val softBreaks: Boolean,
    val hardBreaks: Boolean,
)

enum class MarkdownFeature {
    Paragraph,
    AtxHeading,
    SetextHeading,
    FencedCodeBlock,
    BlockQuote,
    ListBlock,
    TaskListItem,
    Table,
    InlineCode,
    Emphasis,
    Strong,
    Link,
    ReferenceLink,
    SoftBreak,
    HardBreak,
    Strikethrough,
    Image,
    RawHtmlDisabled,
}
