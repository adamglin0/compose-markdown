package com.adamglin.compose.markdown.core.dialect

import androidx.compose.runtime.Immutable

sealed interface MarkdownDialect {
    val id: String
    val preset: MarkdownPreset
    val blockFeatures: MarkdownBlockFeatures
    val inlineFeatures: MarkdownInlineFeatures

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

    data object GfmMath : MarkdownDialect {
        override val id: String = "gfm-math"
        override val preset: MarkdownPreset = MarkdownPreset.GfmMath
        override val blockFeatures: MarkdownBlockFeatures = preset.blockFeatures
        override val inlineFeatures: MarkdownInlineFeatures = preset.inlineFeatures
    }
}

sealed class MarkdownPreset(
    val id: String,
    val blockFeatures: MarkdownBlockFeatures,
    val inlineFeatures: MarkdownInlineFeatures,
) {
    data object ChatFast : MarkdownPreset(
        id = "chat-fast",
        blockFeatures = MarkdownBlockFeatures(
            taskListItems = true,
            tables = true,
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
            taskListItems = false,
            tables = false,
        ),
        inlineFeatures = MarkdownInlineFeatures(
            inlineCode = true,
            emphasis = true,
            strong = true,
            strikethrough = false,
            inlineLinks = true,
            referenceLinks = true,
            autolinks = true,
            bareAutolinks = false,
            images = true,
            softBreaks = true,
            hardBreaks = true,
        ),
    )

    data object GfmCompat : MarkdownPreset(
        id = "gfm-compat",
        blockFeatures = MarkdownBlockFeatures(
            taskListItems = true,
            tables = true,
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

    data object GfmMath : MarkdownPreset(
        id = "gfm-math",
        blockFeatures = MarkdownBlockFeatures(
            taskListItems = true,
            tables = true,
            mathBlocks = true,
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
            inlineMath = true,
        ),
    )
}

/**
 * Block constructs that differ between presets. Paragraphs, headings, code blocks, block quotes, lists and
 * thematic breaks are part of every preset.
 */
@Immutable
data class MarkdownBlockFeatures(
    val taskListItems: Boolean,
    val tables: Boolean,
    val mathBlocks: Boolean = false,
)

@Immutable
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
    val inlineMath: Boolean = false,
)
