// port-lint: tests pulldown-cmark/tests/html.rs
package io.github.kotlinmania.pulldowncmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PulldownCmarkTest {
    @Test
    fun testHtml1() {
        val original =
            """
            Little header

            <script type="text/js">
            function some_func() {
            console.log("teeeest");
            }


            function another_func() {
            console.log("fooooo");
            }
            </script>
            """.trimIndent()

        val expected =
            """
            <p>Little header</p>
            <script type="text/js">
            function some_func() {
            console.log("teeeest");
            }


            function another_func() {
            console.log("fooooo");
            }
            </script>
            """.trimIndent()

        val output = renderHtml(original)
        assertEquals(expected, output.trim())
    }

    @Test
    fun testHtml2() {
        val original =
            """
            Little header

            <script
            type="text/js">
            function some_func() {
            console.log("teeeest");
            }


            function another_func() {
            console.log("fooooo");
            }
            </script>
            """.trimIndent()

        val expected =
            """
            <p>Little header</p>
            <script
            type="text/js">
            function some_func() {
            console.log("teeeest");
            }


            function another_func() {
            console.log("fooooo");
            }
            </script>
            """.trimIndent()

        val output = renderHtml(original)
        assertEquals(expected, output.trim())
    }

    @Test
    fun testHtml3() {
        val original =
            """
            Little header

            <?
            <div></div>
            <p>Useless</p>
            ?>
            """.trimIndent()

        val expected =
            """
            <p>Little header</p>
            <?
            <div></div>
            <p>Useless</p>
            ?>
            """.trimIndent()

        val output = renderHtml(original)
        assertEquals(expected, output.trim())
    }

    @Test
    fun testHtml4() {
        val original =
            """
            Little header

            <!--
            <div></div>
            <p>Useless</p>
            -->
            """.trimIndent()

        val expected =
            """
            <p>Little header</p>
            <!--
            <div></div>
            <p>Useless</p>
            -->
            """.trimIndent()

        val output = renderHtml(original)
        assertEquals(expected, output.trim())
    }

    @Test
    fun testHtml5() {
        val original =
            """
            Little header

            <![CDATA[
            <div></div>
            <p>Useless</p>
            ]]>
            """.trimIndent()

        val expected =
            """
            <p>Little header</p>
            <![CDATA[
            <div></div>
            <p>Useless</p>
            ]]>
            """.trimIndent()

        val output = renderHtml(original)
        assertEquals(expected, output.trim())
    }

    @Test
    fun testHtml6() {
        val original =
            """
            Little header

            <!X
            Some things are here...
            >
            """.trimIndent()

        val expected =
            """
            <p>Little header</p>
            <!X
            Some things are here...
            >
            """.trimIndent()

        val output = renderHtml(original)
        assertEquals(expected, output.trim())
    }

    @Test
    fun testHtml7() {
        val original =
            """
            Little header
            -----------

            <script>
            function some_func() {
            console.log("teeeest");
            }


            function another_func() {
            console.log("fooooo");
            }
            </script>
            """.trimIndent()

        val expected =
            """
            <h2>Little header</h2>
            <script>
            function some_func() {
            console.log("teeeest");
            }


            function another_func() {
            console.log("fooooo");
            }
            </script>
            """.trimIndent()

        val output = renderHtml(original)
        assertEquals(expected, output.trim())
    }

    @Test
    fun testHtml11() {
        val original = "hi ~~no~~"
        val expected = "<p>hi ~~no~~</p>\n"

        val output = renderHtml(original)
        assertEquals(expected, output)
    }

    @Test
    fun testNewlineInCode() {
        val originals = listOf("`\n `x", "` \n`x")
        val expected = "<p><code>  </code>x</p>\n"

        for (orig in originals) {
            val output = renderHtml(orig)
            assertEquals(expected, output)
        }
    }

    @Test
    fun testNewlineStartEndOfCode() {
        val original = "`\nx\n`x"
        val expected = "<p><code>x</code>x</p>\n"

        val output = renderHtml(original)
        assertEquals(expected, output)
    }

    @Test
    fun testTrimSpaceAndTabAtEndOfParagraph() {
        val original = "one\ntwo \t"
        val expected = "<p>one\ntwo</p>\n"

        val output = renderHtml(original)
        assertEquals(expected, output)
    }

    @Test
    fun testNewlineWithinCode() {
        val originals = listOf("`\nx \ny\n`x", "`x \ny`x", "`x\n y`x")
        val expected = "<p><code>x  y</code>x</p>\n"

        for (orig in originals) {
            val output = renderHtml(orig)
            assertEquals(expected, output)
        }
    }

    @Test
    fun testTrimSpaceTabNlAtEndOfParagraph() {
        val original = "one\ntwo \t\n"
        val expected = "<p>one\ntwo</p>\n"

        val output = renderHtml(original)
        assertEquals(expected, output)
    }

    @Test
    fun testTrimSpaceNlAtEndOfParagraph() {
        val original = "one\ntwo \n"
        val expected = "<p>one\ntwo</p>\n"

        val output = renderHtml(original)
        assertEquals(expected, output)
    }

    @Test
    fun testTrimSpaceBeforeSoftBreak() {
        val original = "one \ntwo"
        val expected = "<p>one\ntwo</p>\n"

        val output = renderHtml(original)
        assertEquals(expected, output)
    }

    @Test
    fun testIssue819() {
        val originals =
            listOf(
                "# \\",
                "# \\\n",
                "# \\\n\n",
                "# \\\r\n",
                "# \\\r\n\r\n",
                "# \\\n\r\n",
                "# \\\r\n\n",
            )
        val expected = "<h1>\\</h1>"

        for (orig in originals) {
            val output = renderHtml(orig)
            assertEquals(expected, output.trimEnd('\n'))
        }
        for (orig in originals) {
            val output = renderHtml(orig, Options.ENABLE_HEADING_ATTRIBUTES)
            assertEquals(expected, output.trimEnd('\n'))
        }
    }

    @Test
    fun testHtml8Tables() {
        val original = "A | B\n---|---\nfoo | bar"
        val expected = "<table><thead><tr><th>A</th><th>B</th></tr></thead><tbody>\n<tr><td>foo</td><td>bar</td></tr>\n</tbody></table>\n"

        val output = renderHtml(original, Options.ENABLE_TABLES)
        assertEquals(expected, output)
    }

    @Test
    fun testHtml9Hrule() {
        val original = "---"
        val expected = "<hr />\n"

        val output = renderHtml(original)
        assertEquals(expected, output)
    }

    @Test
    fun testHtml10HruleAsterisks() {
        val original = "* * *"
        val expected = "<hr />\n"

        val output = renderHtml(original)
        assertEquals(expected, output)
    }

    @Test
    fun testBrokenCallback() {
        val original =
            """
            [foo],
            [bar],
            [baz],

               [baz]: https://example.org
            """.trimIndent()

        val callback =
            BrokenLinkCallback { broken ->
                if (broken.reference.value == "foo" || broken.reference.value == "baz") {
                    BrokenLink(
                        linkType = broken.linkType,
                        reference = broken.reference,
                        destUrl = CowStr.from("https://replaced.example.org"),
                        title = CowStr.from("some title"),
                    )
                } else {
                    null
                }
            }

        val parser = Parser(original, Options.NONE, callback)
        val sb = StringBuilder()
        pushHtml(sb, parser)

        val output = sb.toString()
        assertTrue(output.contains("https://replaced.example.org"))
        assertTrue(output.contains("https://example.org"))
        assertTrue(output.contains("[bar]"))
    }

    @Test
    fun testListsInsideCodeSpans() {
        val input = "- `\nx\n**\n  *\n  `"
        val parser = Parser(input)
        for (ev in parser) {
            // iterate without errors
        }
    }

    @Test
    fun testFuzzerInputs() {
        val cases =
            listOf(
                ">\n >>><N\n",
                " \u000B\\\r- ",
                "\n # #\r\u001C ",
                "\u0000{\t\u03D0}\n-",
                " \u000C{}\n-\n",
                "*\t[][\n\t<p]>\n\t[]",
                "[][{]}\n-",
                "a\n \u000C{}\n-",
                "a\n \u000C{}\\\n-",
                "[](\\ ",
                "<a",
                "[<!W\n\\\n",
                "><a\n",
                "><a a\n",
            )

        for (case in cases) {
            val parser = Parser(case, Options.ALL)
            for (ev in parser) {
                // Ensure no crash or uncaught exception
            }
        }
    }

    @Test
    fun testEntitiesLookup() {
        assertEquals("&", Entities.getEntity("amp"))
        assertEquals("<", Entities.getEntity("lt"))
        assertEquals(">", Entities.getEntity("gt"))
        assertEquals("\"", Entities.getEntity("quot"))
        assertEquals("©", Entities.getEntity("copy"))
    }

    @Test
    fun testPunctuation() {
        assertTrue(PunctTable.isAsciiPunctuation('!'.code))
        assertTrue(PunctTable.isAsciiPunctuation('#'.code))
        assertTrue(PunctTable.isAsciiPunctuation('.'.code))
        assertTrue(PunctTable.isPunctuation('!'.code))
    }

    @Test
    fun testTreeOperations() {
        val tree = Tree<String>({ "" })
        val a = tree.append("A")
        tree.push()
        val b = tree.append("B")
        val c = tree.append("C")
        assertEquals(1, tree.spineLen())
        tree.pop()
        assertEquals(0, tree.spineLen())

        assertEquals(b, tree[a].child)
        assertEquals(c, tree[b].next)
        assertNull(tree[c].next)
    }

    @Test
    fun testTextMergeStream() {
        val events =
            listOf(
                Event.Text("Hello "),
                Event.Text("World!"),
                Event.SoftBreak,
                Event.Text("Again"),
            )

        val merged = TextMergeStream(events.iterator()).toList()
        assertEquals(3, merged.size)
        assertEquals(Event.Text("Hello World!"), merged[0])
        assertEquals(Event.SoftBreak, merged[1])
        assertEquals(Event.Text("Again"), merged[2])
    }
}
