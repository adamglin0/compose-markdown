package com.adamglin.compose.markdown.core.block

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.ListType
import com.adamglin.compose.markdown.core.model.TaskState
import com.adamglin.compose.markdown.core.spec.SpecHtmlRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Block structure regressions, checked through the spec HTML renderer unless the AST carries more. */
class BlockStructureRegressionTest {
    @Test
    fun listsFollowedByALineEndingStayTight() {
        assertHtml("<ul>\n<li>a</li>\n<li>b</li>\n</ul>\n", "- a\n- b\n")
        assertHtml("<ol>\n<li>a</li>\n<li>b</li>\n</ol>\n", "1. a\n2. b\n")
    }

    @Test
    fun blankLinesInsideNestedListsOrFencesKeepTheOuterListTight() {
        assertHtml(
            "<ul>\n<li>a\n<ul>\n<li>\n<p>b</p>\n</li>\n<li>\n<p>c</p>\n</li>\n</ul>\n</li>\n</ul>\n",
            "- a\n  - b\n\n  - c\n",
        )
        assertHtml("<ul>\n<li>\n<pre><code>a\n\nb\n</code></pre>\n</li>\n</ul>\n", "- ```\n  a\n\n  b\n  ```\n")
    }

    @Test
    fun lazyContinuationLinesExtendParagraphsInContainers() {
        assertHtml("<ul>\n<li>item\ncontinued</li>\n</ul>\n", "- item\ncontinued\n")
        assertHtml("<blockquote>\n<p>quote\ncontinued</p>\n</blockquote>\n", "> quote\ncontinued\n")
        assertHtml("<blockquote>\n<blockquote>\n<p>a\nb</p>\n</blockquote>\n</blockquote>\n", "> > a\nb\n")
        assertHtml("<blockquote>\n<ul>\n<li>a\nb</li>\n</ul>\n</blockquote>\n", "> - a\nb\n")
    }

    @Test
    fun indentedCodeBlocksAreRecognizedInEveryPreset() {
        listOf(MarkdownDialect.ChatFast, MarkdownDialect.CommonMarkCore, MarkdownDialect.GfmCompat, MarkdownDialect.GfmMath).forEach { dialect ->
            assertHtml("<pre><code>code\nmore\n</code></pre>\n", "    code\n    more\n", dialect)
            assertHtml("<pre><code>code\n</code></pre>\n", "\tcode\n", dialect)
            assertHtml("<pre><code># foo\n</code></pre>\n", "    # foo\n", dialect)
            assertHtml("<pre><code>***\n</code></pre>\n", "    ***\n", dialect)
            assertHtml("<pre><code>```\nx\n```\n</code></pre>\n", "    ```\n    x\n    ```\n", dialect)
        }
    }

    @Test
    fun blankLineEndsABlockQuote() {
        assertHtml("<blockquote>\n<p>a</p>\n</blockquote>\n<blockquote>\n<p>b</p>\n</blockquote>\n", "> a\n\n> b\n")
    }

    @Test
    fun thematicBreakTakesPrecedenceOverListMarkers() {
        assertHtml("<hr />\n", "- - -\n")
        assertHtml("<ul>\n<li>a</li>\n</ul>\n<hr />\n<ul>\n<li>b</li>\n</ul>\n", "* a\n* * *\n* b\n")
    }

    @Test
    fun bracketMathNeverSwallowsFollowingBlocks() {
        assertHtml("<p>[note] see</p>\n<h1>Heading</h1>\n<p>para</p>\n", "\\[note] see\n\n# Heading\n\npara\n", MarkdownDialect.GfmMath)
        assertHtml("<div class=\"math\">a + b</div>\n", "\\[\na + b\n\\]\n", MarkdownDialect.GfmMath)
    }

    @Test
    fun itemsWithDifferentIndentationShareOneList() {
        assertHtml("<ul>\n<li>a</li>\n<li>b</li>\n<li>c</li>\n</ul>\n", "- a\n - b\n  - c\n")
        assertHtml("<ul>\n<li>a\n<ul>\n<li>b</li>\n</ul>\n</li>\n</ul>\n", "- a\n\t- b\n")
    }

    @Test
    fun changingTheBulletOrDelimiterStartsANewList() {
        assertHtml("<ul>\n<li>a</li>\n</ul>\n<ul>\n<li>b</li>\n</ul>\n", "- a\n+ b\n")
        assertHtml("<ol>\n<li>a</li>\n</ol>\n<ol>\n<li>b</li>\n</ol>\n", "1. a\n1) b\n")
    }

    @Test
    fun listTypeExposesBulletStartNumberAndDelimiter() {
        val lists = blocks("* a\n\n7) b\n8) c\n", MarkdownDialect.CommonMarkCore).filterIsInstance<BlockNode.ListBlock>()

        assertEquals(listOf(ListType.Bullet(bulletChar = '*'), ListType.Ordered(startNumber = 7, delimiter = ')')), lists.map { it.type })
    }

    @Test
    fun onlyNonEmptyItemsStartingAtOneInterruptAParagraph() {
        assertHtml("<p>text\n2. two</p>\n", "text\n2. two\n")
        assertHtml("<p>text\n*</p>\n", "text\n* \n")
        assertHtml("<p>text</p>\n<ol>\n<li>one</li>\n</ol>\n", "text\n1. one\n")
        assertHtml("<p>foo</p>\n<div class=\"math\">x</div>\n", "foo\n$$\nx\n$$\n", MarkdownDialect.GfmMath)
    }

    @Test
    fun emptyItemsAndTabSeparatedMarkers() {
        assertHtml("<ul>\n<li></li>\n<li>b</li>\n</ul>\n", "-\n- b\n")
        assertHtml("<ul>\n<li>foo</li>\n</ul>\n", "-\n  foo\n")
        assertHtml("<ul>\n<li>a</li>\n</ul>\n", "-\ta\n")
    }

    @Test
    fun aBlankLineAfterAnEmptyFirstLineEndsTheItemWhateverItsIndentation() {
        assertHtml("<ul>\n<li></li>\n</ul>\n<p>foo</p>\n", "-\n  \n  foo\n")
        assertHtml("<ul>\n<li></li>\n</ul>\n<pre><code>  wide\n</code></pre>\n", "+\n\t\t\n      wide\n")
    }

    @Test
    fun anUnderlineBelowOnlyLinkReferenceDefinitionsIsParagraphText() {
        assertHtml("<p>---</p>\n", "[a]: /u\n---\n")
        assertHtml("<p>-</p>\n", "[a]: /u\n-\n", MarkdownDialect.GfmCompat)
    }

    @Test
    fun taskMarkersAreRecognizedAloneAndBeforeContent() {
        val items = blocks("- [ ]\n- [x] done\n- [X]\tdone\n- [y] not a task\n- [ ]text\n")
            .filterIsInstance<BlockNode.ListBlock>()
            .single()
            .items

        assertEquals(listOf(TaskState.Unchecked, TaskState.Checked, TaskState.Checked, null, null), items.map { it.taskState })
        assertEquals(emptyList(), items[0].children)
        assertEquals("<p>done</p>\n", SpecHtmlRenderer.render(items[1].children))
        assertEquals("<p>[y] not a task</p>\n", SpecHtmlRenderer.render(items[3].children))
    }

    @Test
    fun textAfterSingleLineMathIsNeverDropped() {
        // Not a math block: the line stays a paragraph and inline parsing decides what `$$x$$` is.
        val paragraph = assertIs<BlockNode.Paragraph>(blocks("\$\$x\$\$ and text\n", MarkdownDialect.GfmMath).single())
        assertTrue(SpecHtmlRenderer.render(listOf(paragraph)).endsWith(" and text</p>\n"))
        assertHtml("<div class=\"math\">a $$ b</div>\n", "$$\na $$ b\n", MarkdownDialect.GfmMath)
        assertHtml("<div class=\"math\">x^2</div>\n", "\$\$x^2\$\$\n", MarkdownDialect.GfmMath)
    }

    @Test
    fun atxHeadingEdgeCases() {
        assertHtml("<h1></h1>\n", "#\n")
        assertHtml("<h1>foo</h1>\n", "#\tfoo\n")
        assertHtml("<h1>foo#</h1>\n", "# foo#\n")
        assertHtml("<h3>foo ###</h3>\n", "### foo \\###\n")
    }

    @Test
    fun setextUnderlinesOfAnyLengthButNotIndentedFourSpaces() {
        assertHtml("<h1>Foo</h1>\n", "Foo\n=\n")
        assertHtml("<h2>Foo</h2>\n", "Foo\n--\n")
        assertHtml("<p>Foo\n---</p>\n", "Foo\n    ---\n")
    }

    @Test
    fun fencedCodeRemovesOpenerIndentationAndIgnoresDeeplyIndentedClosers() {
        assertHtml("<pre><code>a\nb\n c\n</code></pre>\n", "  ```\n  a\n b\n   c\n  ```\n")
        assertHtml("<pre><code>a\n    ```\n</code></pre>\n", "```\na\n    ```\n")
        assertHtml("<p>``` a`b\nfoo</p>\n", "``` a`b\nfoo\n")
    }

    @Test
    fun linkReferenceDefinitionsFollowTheSpec() {
        assertHtml("<p><a href=\"bar\" title=\"t\">foo</a></p>\n", "[foo]: <bar> \"t\"\n\n[foo]\n")
        assertHtml("<p>[foo]: /url &quot;title&quot; ok</p>\n<p>[foo]</p>\n", "[foo]: /url \"title\" ok\n\n[foo]\n")
        assertHtml("<p><a href=\"/url\">a]b</a></p>\n", "[a\\]b]: /url\n\n[a\\]b]\n")
        assertHtml("<p><a href=\"/url\" title=\"title\">foo</a></p>\n", "[foo]: /url\n\"title\"\n\n[foo]\n")
        assertHtml("<p><a href=\"/url\">foo</a></p>\n", "[foo]:\n/url\n\n[foo]\n")
        assertHtml("<p><a href=\"/url\">Foo  Bar</a></p>\n", "[foo bar]: /url\n\n[Foo  Bar]\n")
    }

    @Test
    fun tableCellsHonorEscapedPipesAndColumnCount() {
        assertHtml(
            "<table>\n<thead>\n<tr>\n<th>a</th>\n<th>b</th>\n</tr>\n</thead>\n<tbody>\n" +
                "<tr>\n<td>x|y</td>\n<td><code>c|d</code></td>\n</tr>\n</tbody>\n</table>\n",
            "| a | b |\n|---|---|\n| x\\|y | `c\\|d` | dropped |\n",
            MarkdownDialect.GfmCompat,
        )
    }

    @Test
    fun tableRowsWithoutPipesContinueUntilABlankLineOrBlockStart() {
        assertHtml(
            "<table>\n<thead>\n<tr>\n<th>a</th>\n<th>b</th>\n</tr>\n</thead>\n<tbody>\n" +
                "<tr>\n<td>bar</td>\n<td></td>\n</tr>\n</tbody>\n</table>\n<blockquote>\n<p>q</p>\n</blockquote>\n",
            "|a|b|\n|---|---|\nbar\n> q\n",
            MarkdownDialect.GfmCompat,
        )
    }

    @Test
    fun aLineWithoutCellsEndsTheTable() {
        assertHtml(
            "<table>\n<thead>\n<tr>\n<th>a</th>\n</tr>\n</thead>\n<tbody>\n<tr>\n<td>b</td>\n</tr>\n</tbody>\n</table>\n<p>|</p>\n",
            "|a|\n|-|\n|b|\n|\n",
            MarkdownDialect.GfmCompat,
        )
    }

    @Test
    fun tabsAdvanceToFourColumnStops() {
        assertHtml("<blockquote>\n<p>foo</p>\n</blockquote>\n", ">\tfoo\n")
        assertHtml("<blockquote>\n<pre><code>  foo\n</code></pre>\n</blockquote>\n", ">\t\tfoo\n")
    }

    @Test
    fun paragraphLinesDropLeadingWhitespaceAndWideMarkersStartIndentedCode() {
        assertHtml("<p>aaa\nbbb</p>\n", "   aaa\n bbb\n")
        assertHtml("<ul>\n<li>\n<pre><code>a\n</code></pre>\n</li>\n</ul>\n", "-     a\n")
    }

    @Test
    fun onlySpacesAndTabsMakeABlankLine() {
        assertHtml("<p>a\n\u00A0\nb</p>\n", "a\n\u00A0\nb\n")
    }

    @Test
    fun htmlStaysLiteralText() {
        assertHtml("<p>&lt;div&gt;\nhi\n&lt;/div&gt;</p>\n", "<div>\nhi\n</div>\n", MarkdownDialect.GfmCompat)
    }

    private fun assertHtml(expected: String, markdown: String, dialect: MarkdownDialect = MarkdownDialect.CommonMarkCore) {
        assertEquals(expected, SpecHtmlRenderer.render(blocks(markdown, dialect)), "markdown: $markdown")
    }

    private fun blocks(markdown: String, dialect: MarkdownDialect = MarkdownDialect.GfmCompat): List<BlockNode> {
        val engine = MarkdownEngine(dialect = dialect)
        engine.append(markdown)
        return assertIs<List<BlockNode>>(engine.finish().snapshot.document.blocks)
    }
}
