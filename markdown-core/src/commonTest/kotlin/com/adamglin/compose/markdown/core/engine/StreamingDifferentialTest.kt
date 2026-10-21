package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

/**
 * Streaming must be invisible: after every append the snapshot equals a one-shot parse of the same prefix,
 * whatever the chunking, in every dialect (see [streamingViolation] for the invariants checked per step).
 */
class StreamingDifferentialTest {
    private val corpus: List<Pair<String, String>> = listOf(
        "list" to "- one\n- two\n- three\n\nafter\n",
        "ordered" to "1. a\n2. b\n10. c\n\npara\n",
        "nested-list" to "- a\n  - b\n    - c\n  - d\n- e\n",
        "loose-list-continuation" to "- a\n\n  continued para\n- b\n",
        "list-item-code" to "- item\n\n  ```\n  code\n  ```\n- next\n",
        "task-list" to "- [ ] todo\n- [x] done\n",
        "quote" to "> q1\n> q2\nlazy\n\n> new\n",
        "nested-quote" to "> a\n>> b\n> > c\n\nend\n",
        "quote-list" to "> - a\n> - b\n>\n> para\n",
        "fence-closed" to "```kotlin\nval x = 1\n\n\nval y = 2\n```\nafter\n",
        "fence-unclosed" to "text\n\n```py\nprint(1)\n",
        "fence-tilde" to "~~~\na\n```\nb\n~~~\n",
        "fence-long" to "````\n```\ninner\n```\n````\n",
        "table" to "| a | b |\n|---|:-:|\n| 1 | 2 |\n| 3 | 4 |\n\nafter\n",
        "table-no-trailing" to "a | b\n--- | ---\nc | d",
        "table-after-para" to "intro line\n| a | b |\n|---|---|\n| 1 | 2 |\n",
        "atx" to "# H1\n## H2 ##\n###### H6\n####### not\n",
        "setext" to "Title\n=====\n\nSub\n---\n\npara\n",
        "setext-multiline" to "line one\nline two\n---\n",
        "hr-vs-setext" to "para\n\n---\n\n***\n",
        "refdef-after" to "see [foo] and [bar][] and [x][foo]\n\n[foo]: /url \"t\"\n[bar]: /b\n",
        "refdef-after-in-list" to "- [foo]\n- [baz]\n\n[foo]: /f\n[baz]: /z\n",
        "refdef-redefine" to "[a]\n\n[a]: /first\n[a]: /second\n",
        "refdef-before" to "[foo]: /url\n\n[foo] here\n",
        "refdef-multiline" to "[foo]\n\n[foo]:\n/url\n\"title\"\n\nafter\n",
        "math-block" to "text\n\n$$\nx^2 + y^2\n$$\n\nafter\n",
        "math-bracket" to "\\[\na+b\n\\]\n",
        "math-unclosed" to "$$\na = b\n",
        "math-inline" to "inline \$a+b\$ and \\(c\\) end\n",
        "emphasis" to "a *emph* and **strong** and ***both*** and _u_ __uu__ ~~del~~\n",
        "emphasis-span-lines" to "start *emph\ncontinues* end **b\nold**\n",
        "code-span" to "use `a ``b`` c` and ``x`y``\n",
        "links" to "[t](http://x.com \"title\") ![img](i.png) <http://auto.link> www.bare.com https://bare.org\n",
        "crlf" to "# H\r\n\r\npara line\r\nnext\r\n\r\n- a\r\n- b\r\n",
        "cr-only" to "a\rb\r\rc\r",
        "emoji" to "hi \uD83D\uDE00 there \uD83D\uDC4D\uD83C\uDFFD ok *\uD83D\uDE80*\n",
        "hard-breaks" to "line one  \nline two\\\nline three\n",
        "trailing-spaces-end" to "end  ",
        "blank-heavy" to "\n\n\npara\n\n\n\npara2\n\n",
        "indented" to "    code?\n    more\n\npara\n",
        "html-like" to "<div>\nhi\n</div>\n\npara\n",
        "inline-across-lines" to "a *b\nc* d `e\nf` g [h\ni](/j\n\"k\") l  \nm\\\nn **o\np\nq** r ~~s\nt~~ \$u\nv\$\n\nw\n",
        "inline-unclosed-openers" to "*a\nb\n_c\nd\n`e\nf\n[g\nh\n<i\nj\n\nk\n",
        "inline-closed-late" to "x *a\nb\nc\nd* y\n[e\nf\ng](/h) z `i\nj\nk` w\n",
        "list-late-definition" to "- [x]\n- b\n- c *d*\n\n  more [x]\n- e\n\n[x]: /u\n",
        "list-definition-inside" to "- [y]\n- [y]: /in-list\n- z [y]\n\n[y]: /outside\n",
        "quote-long-lazy" to "> a *b\nc* d\ne\n> f\n\ng\n",
        "mixed" to "# Title\n\nIntro *text* with [ref].\n\n- a\n  - b\n> quote\n\n```js\nlet x\n```\n\n| h | i |\n|---|---|\n| 1 | 2 |\n\n\$\$\nm\n\$\$\n\n[ref]: /r\n",
    )

    @Test
    fun everyPrefixMatchesOneShotParseForCorpus() {
        val failures = mutableListOf<String>()
        allDialects.forEach { dialect ->
            corpus.forEach { (name, text) ->
                val strategies = buildList {
                    add("char" to charChunks(text))
                    add("tokens" to randomChunks(text, Random(name.hashCode()), minSize = 4, maxSize = 10))
                    (1..3).forEach { seed -> add("random$seed" to randomChunks(text, Random(seed))) }
                    add("with-empty" to text.chunked(3).flatMap { listOf("", it) })
                }
                strategies.forEach { (strategy, chunks) ->
                    streamingViolation(dialect, chunks)?.let { failures += "${dialect.id} $name $strategy: $it" }
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail("${failures.size} streaming mismatches:\n" + failures.take(10).joinToString("\n"))
        }
    }

    /**
     * Snapshots share storage (stable blocks, cached children, committed inline nodes, code text), and the
     * block parser's forks write past the shared parts: no later append may change a published snapshot.
     */
    @Test
    fun publishedSnapshotsNeverChange() {
        allDialects.forEach { dialect ->
            corpus.forEach { (name, text) ->
                val engine = MarkdownEngine(dialect = dialect)
                val published = randomChunks(text, Random(name.hashCode()), minSize = 1, maxSize = 6).map { chunk ->
                    val snapshot = engine.append(chunk).snapshot
                    snapshot to snapshot.document.blocks.dump()
                }
                engine.finish()
                published.forEachIndexed { step, (snapshot, dump) ->
                    if (snapshot.document.blocks.dump() != dump) {
                        fail("${dialect.id} $name: snapshot of step $step changed after later appends")
                    }
                }
            }
        }
    }

    /** Inputs where new text changes how an earlier block, or one closed by a partial line, is read. */
    @Test
    fun retroactiveReinterpretationMatchesOneShotParse() {
        val cases = listOf(
            listOf("Hello\n", "***", "x\n"),
            listOf("a | b\n", "---", " | ---\nc | d"),
            listOf("- item\n\n  ```\n  code\n  ```\n", "-", " next\n"),
            charChunks("- ```\n  ```\n- "),
            charChunks("g\n|d\n---"),
            listOf("a", " |", " ", " b\n- ", "| -", "\n1 ", "| 2"),
            listOf("Title\n", "="),
            listOf("Title\n", "=====\n"),
            listOf("| a |\n|---|\n", "| 1 |\n"),
            listOf("Temp is\n", "-", "1 degrees\n"),
            listOf("Hello\n", "``", "`x` code\n"),
            listOf("Hello\n", "#", "hashtag\n"),
            listOf("para\n\n", "    code\n"),
            listOf("- a\n\n", "  b\n"),
            listOf("- a\n\n", "- b\n"),
            listOf("> a\n\n", "> b\n"),
            listOf("```\ncode\n``", "`\nafter\n"),
            listOf("# h\n", "   ", "\n    code\n"),
        )
        val failures = allDialects.flatMap { dialect ->
            cases.mapNotNull { chunks ->
                streamingViolation(dialect, chunks)?.let { "${dialect.id} ${chunks.map { it.escaped() }}: $it" }
            }
        }
        if (failures.isNotEmpty()) {
            fail(failures.joinToString("\n"))
        }
    }
}
