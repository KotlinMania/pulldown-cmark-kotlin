// port-lint: tests src/parse.rs
package io.github.kotlinmania.pulldowncmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParseTest {
    private fun parserWithExtensions(text: String): Parser {
        val opts =
            Options.ENABLE_TABLES +
                Options.ENABLE_FOOTNOTES +
                Options.ENABLE_STRIKETHROUGH +
                Options.ENABLE_TASKLISTS
        return Parser(text, opts)
    }

    private fun Parser.countEvents(): Int = asSequence().count()

    private fun Parser.consume() {
        for (item in this) {
            assertNotNull(item)
        }
    }

    // node_size, body_size omitted: Rust memory layout (size_of::<Node<Item>>()) does not apply to Kotlin objects

    @Test
    fun singleOpenFishBracket() {
        assertEquals(3, Parser("<").countEvents())
    }

    @Test
    fun loneHashtag() {
        assertEquals(2, Parser("#").countEvents())
    }

    @Test
    fun lotsOfBackslashes() {
        Parser("\\\\\r\r").consume()
        Parser("\\\r\r\\.\\\\\r\r\\.\\").consume()
    }

    @Test
    fun issue320() {
        parserWithExtensions(":\r\t> |\r:\r\t> |\r").consume()
    }

    @Test
    fun issue319() {
        parserWithExtensions("|\r-]([^|\r-]([^").consume()
        parserWithExtensions("|\r\r=][^|\r\r=][^car").consume()
    }

    @Test
    fun issue303() {
        parserWithExtensions("[^\r\ra]").consume()
        parserWithExtensions("\r\r]Z[^\u0000\r\r]Z[^\u0000").consume()
    }

    @Test
    fun issue313() {
        parserWithExtensions("*]0[^\r\r*]0[^").consume()
        parserWithExtensions("[^\r> `][^\r> `][^\r> `][").consume()
    }

    @Test
    fun issue311() {
        parserWithExtensions("\\\r-\t\\\r-\t").consume()
    }

    @Test
    fun issue283() {
        // \xf0\x9b\xb2\x9f in UTF-8 is code point U+1BC9F
        val input = "\uD82F\uDC9F<td:^\uD82F\uDC9F"
        parserWithExtensions(input).consume()
    }

    @Test
    fun issue289() {
        parserWithExtensions("> - \\\n> - ").consume()
        parserWithExtensions("- \n\n").consume()
    }

    @Test
    fun issue306() {
        parserWithExtensions("*\r_<__*\r_<__*\r_<__*\r_<__").consume()
    }

    @Test
    fun issue305() {
        parserWithExtensions("_6**6*_*").consume()
    }

    @Test
    fun anotherEmphasisPanic() {
        parserWithExtensions("*__#_#__*").consume()
    }

    @Test
    fun offsetIter() {
        val eventOffsets =
            Parser("*hello* world")
                .intoOffsetIter()
                .asSequence()
                .map { it.range }
                .toList()
        val expectedOffsets =
            listOf(0 until 13, 0 until 7, 1 until 6, 0 until 7, 7 until 13, 0 until 13)
        assertEquals(expectedOffsets, eventOffsets)
    }

    @Test
    fun referenceLinkOffsets() {
        val range =
            Parser("# H1\n[testing][Some reference]\n\n[Some reference]: https://github.com")
                .intoOffsetIter()
                .asSequence()
                .mapNotNull { (ev, range) ->
                    val tag = (ev as? Event.Start)?.tag
                    if (tag is Tag.Link && tag.linkType == LinkType.Reference) {
                        range
                    } else {
                        null
                    }
                }.firstOrNull()
        assertNotNull(range)
        assertEquals(5 until 30, range)
    }

    @Test
    fun footnoteOffsets() {
        val range =
            parserWithExtensions("Testing this[^1] out.\n\n[^1]: Footnote.")
                .intoOffsetIter()
                .asSequence()
                .mapNotNull { (ev, range) ->
                    if (ev is Event.FootnoteReference) range else null
                }.firstOrNull()
        assertNotNull(range)
        assertEquals(12 until 16, range)
    }

    @Test
    fun tableOffset() {
        val markdown = "a\n\nTesting|This|Outtt\n--|:--:|--:\nSome Data|Other data|asdf"
        val eventOffsets =
            parserWithExtensions(markdown)
                .intoOffsetIter()
                .asSequence()
                .map { it.range }
                .toList()
        val expectedOffset = 3 until 59
        assertEquals(expectedOffset, eventOffsets[3])
    }

    @Test
    fun tableCellSpan() {
        val markdown = "a|b|c\n--|--|--\na|  |c"
        val spans =
            parserWithExtensions(markdown)
                .intoOffsetIter()
                .asSequence()
                .mapNotNull { (ev, span) ->
                    if ((ev as? Event.Start)?.tag is Tag.TableCell) span else null
                }.toList()
        val expectedOffsetStart = "a|b|c\n--|--|--\na|".length
        assertEquals(expectedOffsetStart until (expectedOffsetStart + 2), spans[4])
    }

    @Test
    fun offsetIterIssue378() {
        val eventOffsets =
            Parser("a [b](c) d")
                .intoOffsetIter()
                .asSequence()
                .map { it.range }
                .toList()
        val expectedOffsets =
            listOf(0 until 10, 0 until 2, 2 until 8, 3 until 4, 2 until 8, 8 until 10, 0 until 10)
        assertEquals(expectedOffsets, eventOffsets)
    }

    @Test
    fun offsetIterIssue404() {
        val eventOffsets =
            Parser("###\n")
                .intoOffsetIter()
                .asSequence()
                .map { it.range }
                .toList()
        val expectedOffsets = listOf(0 until 4, 0 until 4)
        assertEquals(expectedOffsets, eventOffsets)
    }

    @Test
    fun linkDefAtEof() {
        val testStr = "[My site][world]\n\n[world]: https://vincentprouillet.com"
        val expected = "<p><a href=\"https://vincentprouillet.com\">My site</a></p>\n"
        val buf = StringBuilder()
        pushHtml(buf, Parser(testStr))
        assertEquals(expected, buf.toString())
    }

    @Test
    fun noFootnoteRefsWithoutOption() {
        val testStr = "a [^a]\n\n[^a]: yolo"
        val expected = "<p>a <a href=\"yolo\">^a</a></p>\n"
        val buf = StringBuilder()
        pushHtml(buf, Parser(testStr))
        assertEquals(expected, buf.toString())
    }

    @Test
    fun refDefAtEof() {
        val testStr = "[test]:\\"
        val expected = ""
        val buf = StringBuilder()
        pushHtml(buf, Parser(testStr))
        assertEquals(expected, buf.toString())
    }

    @Test
    fun refDefCrLf() {
        val testStr = "[a]: /u\r\n\n[a]"
        val expected = "<p><a href=\"/u\">a</a></p>\n"
        val buf = StringBuilder()
        pushHtml(buf, Parser(testStr))
        assertEquals(expected, buf.toString())
    }

    @Test
    fun noDestRefdef() {
        val testStr = "[a]:"
        val expected = "<p>[a]:</p>\n"
        val buf = StringBuilder()
        pushHtml(buf, Parser(testStr))
        assertEquals(expected, buf.toString())
    }

    @Test
    fun brokenLinksCalledOnlyOnce() {
        val cases =
            listOf(
                Pair("See also [`g()`][crate::g].", 1),
                Pair("See also [`g()`][crate::g][].", 1),
                Pair("[brokenlink1] some other node [brokenlink2]", 2),
            )
        for ((markdown, expected) in cases) {
            var timesCalled = 0
            val parser =
                Parser(
                    markdown,
                    Options.NONE,
                    BrokenLinkCallback {
                        timesCalled++
                        null
                    },
                )
            for (ev in parser) {
                assertNotNull(ev)
            }
            assertEquals(expected, timesCalled)
        }
    }

    @Test
    fun simpleBrokenLinkCallback() {
        val testStr = "This is a link w/o def: [hello][world]"
        val parser =
            Parser(
                testStr,
                Options.NONE,
                BrokenLinkCallback { brokenLink ->
                    assertEquals("world", brokenLink.reference.value)
                    assertEquals("[hello][world]", testStr.substring(brokenLink.span))
                    BrokenLink(
                        linkType = brokenLink.linkType,
                        reference = brokenLink.reference,
                        destUrl = CowStr.from("YOLO"),
                        title = CowStr.from("SWAG"),
                        span = brokenLink.span,
                    )
                },
            )
        var linkTagCount = 0
        for (event in parser) {
            if (event is Event.Start && event.tag is Tag.Link) {
                val tag = event.tag
                linkTagCount++
                assertEquals(LinkType.ReferenceUnknown, tag.linkType)
                assertEquals("YOLO", tag.destUrl.value)
                assertEquals("SWAG", tag.title.value)
                assertEquals("world", tag.id.value)
            }
        }
        assertTrue(linkTagCount > 0)
    }

    @Test
    fun codeBlockKindCheckFenced() {
        val parser = Parser("hello\n```test\ntadam\n```")
        var found = 0
        for ((ev, _) in parser.intoOffsetIter()) {
            if (ev is Event.Start && ev.tag is Tag.CodeBlock) {
                val kind = ev.tag.kind
                if (kind is CodeBlockKind.Fenced) {
                    assertEquals("test", kind.info.value)
                    found++
                }
            }
        }
        assertEquals(1, found)
    }

    @Test
    fun codeBlockKindCheckIndented() {
        val parser = Parser("hello\n\n    ```test\n    tadam\nhello")
        var found = 0
        for ((ev, _) in parser.intoOffsetIter()) {
            if (ev is Event.Start && ev.tag is Tag.CodeBlock) {
                if (ev.tag.kind is CodeBlockKind.Indented) {
                    found++
                }
            }
        }
        assertEquals(1, found)
    }

    @Test
    fun refDefs() {
        val input =
            "[a B c]: http://example.com\n" +
                "[another]: https://google.com\n\n" +
                "text\n\n" +
                "[final ONE]: http://wikipedia.org\n"
        val parser = Parser(input)
        assertNotNull(parser.referenceDefinitions().get("a b c"))
        assertNull(parser.referenceDefinitions().get("nope"))

        val event = parser.next()
        assertNotNull(event)
        val linkDef = parser.referenceDefinitions().get("final one")
        assertNotNull(linkDef)
        val span = input.substring(linkDef.span)
        assertEquals("[final ONE]: http://wikipedia.org", span)
    }
}
