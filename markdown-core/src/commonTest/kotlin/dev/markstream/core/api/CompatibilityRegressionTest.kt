package dev.markstream.core.api

import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.model.BlockNode
import dev.markstream.core.model.InlineNode
import dev.markstream.core.model.TaskState
import kotlin.test.Test
import kotlin.test.assertEquals

class CompatibilityRegressionTest {
    @Test
    fun curatedSpecCasesMatchExpectedShapeInOneShotMode() {
        compatibilityCases.forEach { case ->
            val engine = MarkdownEngine(dialect = case.dialect)
            val snapshot = engine.append(case.markdown).snapshot

            assertEquals(
                case.expectedShape,
                snapshot.document.blocks.debugShape(),
                "${case.name} (${case.origin})",
            )
        }
    }

    @Test
    fun curatedStreamingCasesMatchExpectedShapeWithoutFallback() {
        streamingCases.forEach { case ->
            val engine = MarkdownEngine(dialect = case.dialect)
            var fallbackCount = 0

            case.chunks.forEach { chunk ->
                fallbackCount += engine.append(chunk).stats.fallbackCount
            }

            val snapshot = engine.finish().snapshot

            assertEquals(0, fallbackCount, "${case.name} should stay incremental")
            assertEquals(
                case.expectedShape,
                snapshot.document.blocks.debugShape(),
                "${case.name} (${case.origin})",
            )
        }
    }
}

private data class CompatibilityCase(
    val name: String,
    val origin: String,
    val dialect: MarkdownDialect,
    val markdown: String,
    val expectedShape: String,
)

private data class StreamingCompatibilityCase(
    val name: String,
    val origin: String,
    val dialect: MarkdownDialect,
    val chunks: List<String>,
    val expectedShape: String,
)

private val compatibilityCases = listOf(
    CompatibilityCase(
        name = "CommonMark paragraph",
        origin = "CommonMark 0.31.2 representative paragraph case",
        dialect = MarkdownDialect.CommonMarkCore,
        markdown = "alpha\nbeta",
        expectedShape = "paragraph(text(alpha)softbreaktext(beta))",
    ),
    CompatibilityCase(
        name = "CommonMark thematic break precedence",
        origin = "CommonMark 0.31.2 Example 59",
        dialect = MarkdownDialect.CommonMarkCore,
        markdown = "Foo\n---\nbar",
        expectedShape = "heading(2,setext,text(Foo))\nparagraph(text(bar))",
    ),
    CompatibilityCase(
        name = "CommonMark setext heading with inline content",
        origin = "CommonMark 0.31.2 Example 80",
        dialect = MarkdownDialect.CommonMarkCore,
        markdown = "Foo *bar*\n---------",
        expectedShape = "heading(2,setext,text(Foo )em(text(bar)))",
    ),
    CompatibilityCase(
        name = "CommonMark fenced code block",
        origin = "CommonMark 0.31.2 Example 89 style case",
        dialect = MarkdownDialect.CommonMarkCore,
        markdown = "```kotlin\nval x = 1\n```",
        expectedShape = "fence(kotlin,closed,val x = 1)",
    ),
    CompatibilityCase(
        name = "CommonMark block quote",
        origin = "CommonMark 0.31.2 representative block quote case",
        dialect = MarkdownDialect.CommonMarkCore,
        markdown = "> quoted",
        expectedShape = "quote(paragraph(text(quoted)))",
    ),
    CompatibilityCase(
        name = "CommonMark list",
        origin = "CommonMark 0.31.2 representative list case",
        dialect = MarkdownDialect.CommonMarkCore,
        markdown = "- one\n- two",
        expectedShape = "list(unordered,tight,item(-,paragraph(text(one)));item(-,paragraph(text(two))))",
    ),
    CompatibilityCase(
        name = "CommonMark reference link",
        origin = "CommonMark 0.31.2 Example 198",
        dialect = MarkdownDialect.CommonMarkCore,
        markdown = "[foo]: /url\n\n[foo]",
        expectedShape = "paragraph(link(foo->/url))",
    ),
    CompatibilityCase(
        name = "GFM table",
        origin = "GFM 0.29-gfm Example 198",
        dialect = MarkdownDialect.GfmCompat,
        markdown = "| foo | bar |\n| --- | --- |\n| baz | bim |",
        expectedShape = "table(header=foo|bar;rows=baz|bim)",
    ),
    CompatibilityCase(
        name = "GFM task list",
        origin = "GFM 0.29-gfm Example 279",
        dialect = MarkdownDialect.GfmCompat,
        markdown = "- [ ] foo\n- [x] bar",
        expectedShape = "list(unordered,tight,item(-,task:unchecked,paragraph(text(foo)));item(-,task:checked,paragraph(text(bar))))",
    ),
    CompatibilityCase(
        name = "GFM strikethrough",
        origin = "GFM 0.29-gfm Example 491 derived case",
        dialect = MarkdownDialect.GfmCompat,
        markdown = "~~Hi~~ Hello",
        expectedShape = "paragraph(strike(text(Hi))text( Hello))",
    ),
)

private val streamingCases = listOf(
    StreamingCompatibilityCase(
        name = "Streaming plain text",
        origin = "Local streaming split over CommonMark paragraph case",
        dialect = MarkdownDialect.ChatFast,
        chunks = listOf("plain", " text"),
        expectedShape = "paragraph(text(plain text))",
    ),
    StreamingCompatibilityCase(
        name = "Streaming block quote",
        origin = "Local streaming split over CommonMark block quote case",
        dialect = MarkdownDialect.ChatFast,
        chunks = listOf("> quo", "te"),
        expectedShape = "quote(paragraph(text(quote)))",
    ),
    StreamingCompatibilityCase(
        name = "Streaming list",
        origin = "Local streaming split over CommonMark list case",
        dialect = MarkdownDialect.ChatFast,
        chunks = listOf("- one\n", "- two"),
        expectedShape = "list(unordered,tight,item(-,paragraph(text(one)));item(-,paragraph(text(two))))",
    ),
    StreamingCompatibilityCase(
        name = "Streaming fenced code block",
        origin = "Local streaming split over CommonMark fenced code case",
        dialect = MarkdownDialect.ChatFast,
        chunks = listOf("```kotlin\nva", "l x = 1\n```"),
        expectedShape = "fence(kotlin,closed,val x = 1)",
    ),
    StreamingCompatibilityCase(
        name = "Streaming table",
        origin = "Local streaming split over GFM table case",
        dialect = MarkdownDialect.GfmCompat,
        chunks = listOf("| foo | bar |\n", "| --- | --- |\n| baz | bim |"),
        expectedShape = "table(header=foo|bar;rows=baz|bim)",
    ),
    StreamingCompatibilityCase(
        name = "Streaming reference link",
        origin = "Local streaming split over CommonMark reference-link case",
        dialect = MarkdownDialect.CommonMarkCore,
        chunks = listOf("[foo]\n\n", "[foo]: /url"),
        expectedShape = "paragraph(link(foo->/url))",
    ),
)

private fun List<BlockNode>.debugShape(): String = joinToString(separator = "\n") { block ->
    when (block) {
        is BlockNode.BlockQuote -> "quote(${block.children.debugShape()})"
        is BlockNode.Document -> "document(${block.children.debugShape()})"
        is BlockNode.FencedCodeBlock -> "fence(${block.infoString ?: "-"},${if (block.isClosed) "closed" else "open"},${block.literal.replace("\n", "\\n")})"
        is BlockNode.Heading -> "heading(${block.level},${block.style.name.lowercase()},${block.children.debugInline()})"
        is BlockNode.ListBlock -> "list(${block.style.name.lowercase()},${if (block.isLoose) "loose" else "tight"},${block.items.debugItems()})"
        is BlockNode.ListItem -> "item(${block.marker},${block.children.debugShape()})"
        is BlockNode.Paragraph -> "paragraph(${block.children.debugInline()})"
        is BlockNode.RawTextBlock -> "raw(${block.literal.replace("\n", "\\n")})"
        is BlockNode.TableBlock -> "table(header=${block.header.cells.debugCells()};rows=${block.rows.joinToString(separator = ";") { it.cells.debugCells() }})"
        is BlockNode.TableCell -> "cell(${block.children.debugInline()})"
        is BlockNode.TableRow -> "row(${block.cells.debugCells()})"
        is BlockNode.ThematicBreak -> "break(${block.marker})"
        is BlockNode.UnsupportedBlock -> "unsupported(${block.literal.replace("\n", "\\n")})"
    }
}

private fun List<BlockNode.ListItem>.debugItems(): String = joinToString(separator = ";") { item ->
    val taskPrefix = when (item.taskState) {
        null -> ""
        TaskState.Checked -> "task:checked,"
        TaskState.Unchecked -> "task:unchecked,"
    }
    "item(${item.marker},${taskPrefix}${item.children.debugShape()})"
}

private fun List<BlockNode.TableCell>.debugCells(): String = joinToString(separator = "|") { cell ->
    cell.children.inlineLiteral()
}

private fun List<InlineNode>.debugInline(): String = joinToString(separator = "") { node ->
    when (node) {
        is InlineNode.CodeSpan -> "code(${node.literal})"
        is InlineNode.Emphasis -> "em(${node.children.debugInline()})"
        is InlineNode.HardBreak -> "hardbreak"
        is InlineNode.Image -> "image(${node.alt.debugInline()}->${node.destination})"
        is InlineNode.Link -> "link(${node.children.inlineLiteral()}->${node.destination})"
        is InlineNode.SoftBreak -> "softbreak"
        is InlineNode.Strikethrough -> "strike(${node.children.debugInline()})"
        is InlineNode.Strong -> "strong(${node.children.debugInline()})"
        is InlineNode.Text -> "text(${node.literal.replace("\n", "\\n")})"
        is InlineNode.UnsupportedInline -> "unsupported(${node.literal})"
    }
}

private fun List<InlineNode>.inlineLiteral(): String = joinToString(separator = "") { node ->
    when (node) {
        is InlineNode.CodeSpan -> node.literal
        is InlineNode.Emphasis -> node.children.inlineLiteral()
        is InlineNode.HardBreak -> "\\n"
        is InlineNode.Image -> node.alt.inlineLiteral()
        is InlineNode.Link -> node.children.inlineLiteral()
        is InlineNode.SoftBreak -> "\\n"
        is InlineNode.Strikethrough -> node.children.inlineLiteral()
        is InlineNode.Strong -> node.children.inlineLiteral()
        is InlineNode.Text -> node.literal
        is InlineNode.UnsupportedInline -> node.literal
    }
}
