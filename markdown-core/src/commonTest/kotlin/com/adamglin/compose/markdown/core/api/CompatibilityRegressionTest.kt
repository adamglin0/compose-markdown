package com.adamglin.compose.markdown.core.api

import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.InlineNode
import com.adamglin.compose.markdown.core.model.TaskState
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

private val timelineTableMarkdown = """
    | 阶段 | 时间 | 关键事件 |
    |------|------|----------|
    | 上升期 | 2009-2017 | 王健林给5亿启动 → 普思资本峰值10亿美元，身价63亿 |
    | 转折期 | 2017-2019 | 熊猫直播烧光资金，承担20亿连带损失，资产被查封限高 |
    | 蛰伏期 | 2020-2023 | 熊猫破产终局，靠母亲资助化解债务；成立寰聚商业转型文旅 |
    | 收缩期 | 2024-2025 | 寰聚被并购、关联公司注销、麦戟负资产法拍，王健林家族财富从2600亿→100亿 |
""".trimIndent()

private const val timelineTableExpectedShape = "table(header=阶段|时间|关键事件;rows=上升期|2009-2017|王健林给5亿启动 → 普思资本峰值10亿美元，身价63亿;转折期|2017-2019|熊猫直播烧光资金，承担20亿连带损失，资产被查封限高;蛰伏期|2020-2023|熊猫破产终局，靠母亲资助化解债务；成立寰聚商业转型文旅;收缩期|2024-2025|寰聚被并购、关联公司注销、麦戟负资产法拍，王健林家族财富从2600亿→100亿)"

private const val timelineTableIntro = "王思聪财富轨迹（时间线）："

private val timelineTableAfterParagraphMarkdown = """
${timelineTableIntro}
${timelineTableMarkdown}
""".trimIndent()

private const val timelineTableAfterParagraphExpectedShape = "paragraph(text(王思聪财富轨迹（时间线）：))\n${timelineTableExpectedShape}"

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
        name = "GFM table starts immediately after paragraph",
        origin = "User reported table after CJK intro line regression",
        dialect = MarkdownDialect.GfmCompat,
        markdown = timelineTableAfterParagraphMarkdown,
        expectedShape = timelineTableAfterParagraphExpectedShape,
    ),
    CompatibilityCase(
        name = "GFM table requires matching delimiter columns",
        origin = "GFM table delimiter column-count guard",
        dialect = MarkdownDialect.GfmCompat,
        markdown = "intro\na | b\n--- | --- | ---\nstill paragraph",
        expectedShape = "paragraph(text(intro)softbreaktext(a | b)softbreaktext(--- | --- | ---)softbreaktext(still paragraph))",
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
        name = "Streaming table accepts rows after stable prefix",
        origin = "Local regression for appending rows after a complete table snapshot",
        dialect = MarkdownDialect.GfmCompat,
        chunks = listOf(
            "Name | Score\n--- | ---\nAda | 10\n",
            "Bob | 20",
        ),
        expectedShape = "table(header=Name|Score;rows=Ada|10;Bob|20)",
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
