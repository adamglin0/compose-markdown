package com.adamglin.compose.markdown.core.inline

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.InlineNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Regression tests for the inline parser audit findings; each test names the finding it covers. */
class InlineParserRegressionTest {
    @Test
    fun emphasisRequiresFlankingDelimiterRuns() { // finding 3
        assertEquals("text(* a *)", inlineShape("* a *"))
        assertEquals("text(2 * 3 * 4)", inlineShape("2 * 3 * 4"))
        assertEquals("text(~~ a ~~)", inlineShape("~~ a ~~"))
    }

    @Test
    fun intrawordUnderscoreIsLiteral() { // finding 4
        assertEquals("text(snake_case_name here)", inlineShape("snake_case_name here"))
        assertEquals("text(foo_bar_)", inlineShape("foo_bar_"))
        assertEquals("text(call foo__init__bar)", inlineShape("call foo__init__bar"))
        // Per CommonMark, a free-standing `__init__` is strong emphasis (left- and right-flanking at word bounds).
        assertEquals("strong(text(init))text( and )strong(text(main))", inlineShape("__init__ and __main__"))
    }

    @Test
    fun unmatchedDelimitersNeverProduceEmptyEmphasis() { // finding 5
        assertEquals("text(**)", inlineShape("**"))
        assertEquals("text(a ** b)", inlineShape("a ** b"))
        assertEquals("text(*)em(text(foo))", inlineShape("**foo*"))
        assertEquals("strong(strong(text(foo)))", inlineShape("____foo____"))
    }

    @Test
    fun emphasisNestsAccordingToTheDelimiterStack() { // finding 6
        assertEquals("em(text(foo )strong(text(bar))text( baz))", inlineShape("*foo **bar** baz*"))
        assertEquals("strong(text(foo, )strong(text(bar))text(, baz))", inlineShape("__foo, __bar__, baz__"))
    }

    @Test
    fun ruleOfThreeAndTripleDelimiters() { // finding 7
        assertEquals("em(strong(text(strong emph)))", inlineShape("***strong emph***"))
        assertEquals("em(strong(text(strong))text( in emph))", inlineShape("***strong** in emph*"))
        assertEquals("em(text(in emph )strong(text(strong)))", inlineShape("*in emph **strong***"))
        assertEquals("text(foo)em(strong(text(bar)))text(baz)", inlineShape("foo***bar***baz"))
        assertEquals("em(text(foo**bar))", inlineShape("*foo**bar*"))
    }

    @Test
    fun strikethroughFollowsGfm() { // finding 8
        assertEquals("text(~~~Hi~~~)", inlineShape("~~~Hi~~~"))
        assertEquals("del(text(Hi))text( and )del(text(there))", inlineShape("~~Hi~~ and ~there~"))
        assertEquals("text(~~Hi~)", inlineShape("~~Hi~"))
        // A tilde closer only tries the nearest tilde opener (cmark-gfm), which must have the same length.
        assertEquals("text(~a ~~b~)", inlineShape("~a ~~b~"))
        assertEquals("del(text(a )del(text(b))text( c))", inlineShape("~~a ~b~ c~~"))
        assertEquals("del(text(a )del(text(b c~ d))text( e))", inlineShape("~a ~~b c~ d~~ e~"))
        assertEquals("text(e)sbtext(***x)", inlineShape("e\n***x"))
    }

    @Test
    fun inlineLinkTitles() { // finding 9
        assertEquals("link[/uri \"title\"](text(link))", inlineShape("[link](/uri \"title\")"))
        assertEquals("link[/uri \"t\"](text(link))", inlineShape("[link](/uri 't')"))
        assertEquals("link[/uri \"t\"](text(link))", inlineShape("[link](/uri (t))"))
        assertEquals("image[/a.png \"cap\"](text(alt))", inlineShape("![alt](/a.png \"cap\")"))
        assertEquals("link[/uri \"say \"hi\"\"](text(a))", inlineShape("[a](/uri \"say \\\"hi\\\"\")"))
        assertEquals("link[/uri \"t\"](text(a))", inlineShape("[a](\n/uri\n\"t\"\n)"))
    }

    @Test
    fun inlineLinkDestinations() { // finding 10
        assertEquals("text([link](/my uri))", inlineShape("[link](/my uri)"))
        assertEquals("text([link](foo)sbtext(bar))", inlineShape("[link](foo\nbar)"))
        assertEquals("link[/my uri](text(link))", inlineShape("[link](</my uri>)"))
        assertEquals("link[](text(link))", inlineShape("[link](<>)"))
        assertEquals("link[](text(link))", inlineShape("[link]()"))
        assertEquals("link[(foo)](text(link))", inlineShape("[link](\\(foo\\))"))
        assertEquals("link[foo(and(bar))](text(link))", inlineShape("[link](foo(and(bar)))"))
    }

    @Test
    fun destinationsOnlyUnescapeAsciiPunctuation() { // finding 11
        assertEquals("link[foo\\bar](text(link))", inlineShape("[link](foo\\bar)"))
        assertEquals("link[foo)bar](text(link))", inlineShape("[link](foo\\)bar)"))
    }

    @Test
    fun linksDoNotNest() { // finding 12
        assertEquals("text([foo )link[/uri](text(bar))text(](/uri))", inlineShape("[foo [bar](/uri)](/uri)"))
        assertEquals(
            "image[uri3](text([)link[uri1](text(foo))text(](uri2)))",
            inlineShape("![[[foo](uri1)](uri2)](uri3)"),
        )
    }

    @Test
    fun precedenceOfCodeSpansAutolinksLinksAndEmphasis() { // finding 13
        assertEquals("text([foo)code(](/uri))", inlineShape("[foo`](/uri)`"))
        assertEquals("text(*)link[/uri](text(foo*))", inlineShape("*[foo*](/uri)"))
        assertEquals("text(**a)link[http://foo.bar/?q=**](text(http://foo.bar/?q=**))", inlineShape("**a<http://foo.bar/?q=**>"))
    }

    @Test
    fun escapedBangDoesNotStartImage() { // finding 14
        assertEquals("text(!)link[/url](text(foo))", inlineShape("\\![foo](/url)"))
    }

    @Test
    fun referenceLabelsHonourEscapesAndLengthLimit() { // finding 15
        assertEquals("link[/u](text(foo))", inlineShape("[foo][ba\\]r]", definitions = mapOf("ba\\]r" to "/u")))
        assertEquals("link[/u](text(Baz))", inlineShape("[Baz][FOO  bar]", definitions = mapOf("foo bar" to "/u")))
        assertEquals("link[/u](text(ẞ))", inlineShape("[ẞ]", definitions = mapOf("SS" to "/u")))
        val longLabel = "a".repeat(1000)
        assertEquals("text([foo][$longLabel])", inlineShape("[foo][$longLabel]", definitions = mapOf(longLabel to "/u")))
    }

    @Test
    fun unmatchedBacktickRunIsLiteral() { // finding 16
        assertEquals("text(```foo``)", inlineShape("```foo``"))
        assertEquals("text(`foo)code(bar)", inlineShape("`foo``bar``"))
        assertEquals("code(foo ` bar)", inlineShape("`` foo ` bar ``"))
    }

    @Test
    fun characterReferencesAreDecoded() { // finding 17
        assertEquals("text(& \u00A0 © # \" ꯍ \uFFFD)", inlineShape("&amp; &nbsp; &copy; &#35; &#X22; &#xabcd; &#0;"))
        assertEquals("text(&nosuchentity; &#87654321;)", inlineShape("&nosuchentity; &#87654321;"))
        assertEquals("link[/föo \"t&\"](text(a))", inlineShape("[a](/f&ouml;o \"t&amp;\")"))
        assertEquals("code(&amp;)", inlineShape("`&amp;`"))
    }

    @Test
    fun allAsciiPunctuationIsEscapable() { // finding 18
        val punctuation = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
        val escaped = punctuation.map { "\\$it" }.joinToString(separator = "")
        assertEquals("text($punctuation)", inlineShape(escaped))
        assertEquals("text(\\a\\φ)", inlineShape("\\a\\φ"))
    }

    @Test
    fun angleAutolinksAcceptAnySchemeAndEmails() { // finding 19
        assertEquals("link[irc://foo.bar:2233/baz](text(irc://foo.bar:2233/baz))", inlineShape("<irc://foo.bar:2233/baz>"))
        assertEquals("link[a+b+c:d](text(a+b+c:d))", inlineShape("<a+b+c:d>"))
        assertEquals("link[mailto:foo@bar.example.com](text(foo@bar.example.com))", inlineShape("<foo@bar.example.com>"))
        assertEquals("text(<http://foo.bar/baz bim>)", inlineShape("<http://foo.bar/baz bim>"))
        assertEquals("text(<m:abc>)", inlineShape("<m:abc>"))
    }

    @Test
    fun gfmExtendedAutolinks() { // finding 20
        assertEquals("link[http://www.commonmark.org](text(www.commonmark.org))", inlineShape("www.commonmark.org"))
        assertEquals("link[mailto:foo@bar.baz](text(foo@bar.baz))", inlineShape("foo@bar.baz"))
        assertEquals(
            "text(see )link[https://en.wikipedia.org/wiki/Foo_(bar)](text(https://en.wikipedia.org/wiki/Foo_(bar)))",
            inlineShape("see https://en.wikipedia.org/wiki/Foo_(bar)"),
        )
        assertEquals(
            "text(()link[https://a.dev/x](text(https://a.dev/x))text())",
            inlineShape("(https://a.dev/x)"),
        )
        assertEquals("text(https://)", inlineShape("https://"))
        assertEquals("text(https:// and www.)", inlineShape("https:// and www."))
        assertEquals(
            "link[http://www.google.com/search?q=commonmark](text(www.google.com/search?q=commonmark))text(&hl;)",
            inlineShape("www.google.com/search?q=commonmark&hl;"),
        )
        assertEquals("link[http://www.commonmark.org](text(www.commonmark.org))text(.)", inlineShape("www.commonmark.org."))
        assertEquals("text(xhttp://a.dev)", inlineShape("xhttp://a.dev"))
        assertEquals("text(www.a_b.c_d)", inlineShape("www.a_b.c_d"))
        assertEquals("em(link[https://a.dev](text(https://a.dev)))", inlineShape("*https://a.dev*"))
        assertEquals("em(link[mailto:foo@bar.com](text(foo@bar.com)))", inlineShape("_foo@bar.com_"))
        assertEquals("link[https://a.dev/x](text(www.a.dev))", inlineShape("[www.a.dev](https://a.dev/x)"))
        assertEquals("text(https://a.dev)", inlineShape("https://a.dev", dialect = MarkdownDialect.CommonMarkCore))
    }

    @Test
    fun lineBreaksStripSurroundingSpaces() { // finding 21
        assertEquals("text(foo)sbtext(baz)", inlineShape("foo \n baz"))
        assertEquals("text(foo)hbtext(baz)", inlineShape("foo  \n   baz"))
        assertEquals("text(foo)hbtext(baz)", inlineShape("foo\\\n  baz"))
        assertEquals("text(foo)", inlineShape("foo   "))
        assertEquals("text(foo\\)", inlineShape("foo\\"))
    }

    @Test
    fun inlineMathBindsLikeCodeSpans() { // finding 22
        val math = MarkdownDialect.GfmMath
        assertEquals("em(math(a*b))", inlineShape("*\$a*b\$*", dialect = math))
        assertEquals("text([)math(a](b)text())", inlineShape("[\$a](b\$)", dialect = math))
        assertEquals("text(\$5 and \$10)", inlineShape("\$5 and \$10", dialect = math))
        assertEquals("text(costs \$20, not \$30.)", inlineShape("costs \$20, not \$30.", dialect = math))
        assertEquals("text(\$5)", inlineShape("\\\$5", dialect = math))
        assertEquals("text(\$x\$5)", inlineShape("\$x\$5", dialect = math))
        assertEquals("math(a\\\$b)", inlineShape("\$a\\\$b\$", dialect = math))
        assertEquals("math(\\alpha)text( and )math(x)", inlineShape("\\(\\alpha\\) and \\( x \\)", dialect = math))
        assertEquals("text(()", inlineShape("\\(", dialect = math))
    }

    @Test
    fun incompleteStreamingDelimitersStayLiteral() { // finding 24
        val engine = MarkdownEngine()
        val partial = assertIs<BlockNode.Paragraph>(engine.append("Hello **b").snapshot.document.blocks.single())
        assertEquals("text(Hello **b)", partial.children.shape())
        val complete = assertIs<BlockNode.Paragraph>(engine.append("old**").snapshot.document.blocks.single())
        assertEquals("text(Hello )strong(text(bold))", complete.children.shape())
    }

    @Test
    fun everyStreamingPrefixMatchesOneShotParse() { // finding 24
        val document = "Hello **bold** and *em* with `code`, [a link](https://e.dev \"t\") ~~del~~ " +
            "and www.example.com or foo@bar.dev.\nNext  \nline \\(x\\) \$y\$ &amp; ![img](i.png)"
        val streaming = MarkdownEngine(dialect = MarkdownDialect.GfmMath)
        document.forEachIndexed { index, char ->
            val streamed = streaming.append(char.toString()).snapshot.document.blocks.inlineShapes()
            val oneShot = MarkdownEngine(dialect = MarkdownDialect.GfmMath)
                .append(document.substring(0, index + 1))
                .snapshot.document.blocks.inlineShapes()
            assertEquals(oneShot, streamed, "prefix length ${index + 1}")
        }
    }

    private fun List<BlockNode>.inlineShapes(): List<String> = map { block ->
        when (block) {
            is BlockNode.Paragraph -> block.children.shape()
            else -> block.toString()
        }
    }

    @Test
    fun deeplyNestedConstructsAreCappedAsLiteralText() {
        val depth = 500
        val nodes = parseInline("*".repeat(depth * 2) + "a" + "*".repeat(depth * 2))
        var level = 0
        var current: List<InlineNode> = nodes
        while (true) {
            val container = current.singleOrNull() as? InlineNode.Strong ?: break
            level += 1
            current = container.children
        }
        assertEquals(32, level)
        val innermost = assertIs<InlineNode.Text>(current.single())
        val remainingDelimiters = "*".repeat((depth - 32) * 2)
        assertEquals(remainingDelimiters + "a" + remainingDelimiters, innermost.literal)
    }

    @Test
    fun emptyAndWhitespaceOnlyInput() {
        assertEquals(emptyList<InlineNode>(), parseInline(""))
        assertEquals("", inlineShape("   "))
    }
}
