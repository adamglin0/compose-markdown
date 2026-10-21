package com.adamglin.compose.markdown.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.ListType

/**
 * Composables that render each kind of Markdown element, passed to [Markdown] or
 * [androidx.compose.foundation.lazy.LazyListScope.markdown]. Every block slot defaults to the
 * built-in rendering; replace the ones you need:
 *
 * ```
 * MarkdownComponents(
 *     codeBlock = {
 *         Column {
 *             CodeHeader(language, onCopy = { clipboard.setText(code) })
 *             HighlightedCode()
 *         }
 *     },
 * )
 * ```
 *
 * A slot runs with its scope as receiver: the scope carries the element's data and
 * `DefaultContent()`, the built-in rendering, so a slot can decorate the default or replace it.
 * Containers render their child blocks through the library (`Children()`), so nested content keeps
 * its own slots and streaming behavior. A block slot should emit one layout node (wrap several in
 * a `Column` or `Box`), as the default does.
 *
 * Lambdas may be new on every call, as they are in a `LazyListScope` builder: a new instance only
 * re-runs the slots, never recomposes the blocks around them. Instances with the same slots are
 * equal.
 *
 * @param paragraph a paragraph.
 * @param heading a heading; `block.level` is 1-6.
 * @param codeBlock a fenced or indented code block.
 * @param mathBlock a display formula. The default renders it with the `mathRenderer` of [Markdown]
 *   once it is closed and shows its LaTeX source before.
 * @param blockQuote a block quote.
 * @param list a bullet or ordered list, including its markers and items.
 * @param listItem the body of a list item, next to its marker.
 * @param listMarker the bullet or number of a list item without a task checkbox.
 * @param taskCheckbox the read-only checkbox of a task list item; `block.taskState` is non-null.
 * @param table a GFM table.
 * @param thematicBreak a thematic break (`---`).
 * @param image an image, embedded in its line of text at the size it measures; `null` shows the
 *   image's alt text. The library does not load images.
 */
@Immutable
public class MarkdownComponents(
    public val paragraph: @Composable MarkdownBlockScope<BlockNode.Paragraph>.() -> Unit = { DefaultContent() },
    public val heading: @Composable MarkdownBlockScope<BlockNode.Heading>.() -> Unit = { DefaultContent() },
    public val codeBlock: @Composable MarkdownCodeBlockScope.() -> Unit = { DefaultContent() },
    public val mathBlock: @Composable MarkdownBlockScope<BlockNode.MathBlock>.() -> Unit = { DefaultContent() },
    public val blockQuote: @Composable MarkdownContainerScope<BlockNode.BlockQuote>.() -> Unit = { DefaultContent() },
    public val list: @Composable MarkdownBlockScope<BlockNode.ListBlock>.() -> Unit = { DefaultContent() },
    public val listItem: @Composable MarkdownContainerScope<BlockNode.ListItem>.() -> Unit = { DefaultContent() },
    public val listMarker: @Composable MarkdownListMarkerScope.() -> Unit = { DefaultContent() },
    public val taskCheckbox: @Composable MarkdownBlockScope<BlockNode.ListItem>.() -> Unit = { DefaultContent() },
    public val table: @Composable MarkdownBlockScope<BlockNode.TableBlock>.() -> Unit = { DefaultContent() },
    public val thematicBreak: @Composable MarkdownBlockScope<BlockNode.ThematicBreak>.() -> Unit = { DefaultContent() },
    public val image: (@Composable (MarkdownImage) -> Unit)? = null,
) {
    private val slots: List<Any?>
        get() = listOf(
            paragraph, heading, codeBlock, mathBlock, blockQuote, list, listItem, listMarker, taskCheckbox,
            table, thematicBreak, image,
        )

    override fun equals(other: Any?): Boolean = other is MarkdownComponents && slots == other.slots

    override fun hashCode(): Int = slots.hashCode()
}

/** The element a [MarkdownComponents] slot renders, and its built-in rendering. */
@Stable
public interface MarkdownBlockScope<out T : BlockNode> {
    /** The node to render. */
    public val block: T

    /** The built-in rendering of [block]. */
    @Composable
    public fun DefaultContent(modifier: Modifier = Modifier)
}

/** Like [MarkdownBlockScope], for a block whose children are blocks: a quote or a list item. */
@Stable
public interface MarkdownContainerScope<out T : BlockNode> {
    /** The node to render. */
    public val block: T

    /** The built-in rendering of [block]. */
    @Composable
    public fun DefaultContent(modifier: Modifier = Modifier)

    /** The child blocks, stacked with the theme's spacing and rendered with their own slots. */
    @Composable
    public fun Children(modifier: Modifier = Modifier)
}

/** A fenced or indented code block. */
@Stable
public interface MarkdownCodeBlockScope {
    /** The code, without fences or indentation. */
    public val code: String

    /** The first word of the info string, lowercased and with aliases resolved; `null` if absent. */
    public val language: String?

    /** The full fence info string; `null` for indented code and fences without one. */
    public val infoString: String?

    /** `false` while the fence is still streaming; indented code is always closed. */
    public val isClosed: Boolean

    /** The built-in code block: a bordered surface with the info string as label above [HighlightedCode]. */
    @Composable
    public fun DefaultContent(modifier: Modifier = Modifier)

    /**
     * The code text in the theme's code style, horizontally scrollable, with [contentPadding] inside
     * the scroll. Closed fences are colored by the `codeHighlighter` of [Markdown]; the text is
     * plain while highlighting runs.
     */
    @Composable
    public fun HighlightedCode(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues())
}

/** The marker of a list item that is not a task. */
@Stable
public interface MarkdownListMarkerScope {
    /** The list's marker type; an ordered item's number is `startNumber + index`. */
    public val listType: ListType

    /** The item's position in its list. */
    public val index: Int

    /** How many lists contain this one: 0 for a top-level list. */
    public val depth: Int

    /** The built-in marker: a drawn bullet (disc, circle, square by [depth]) or the item number. */
    @Composable
    public fun DefaultContent(modifier: Modifier = Modifier)
}
