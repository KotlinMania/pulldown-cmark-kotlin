// port-lint: tests tests/errors.rs
package io.github.kotlinmania.pulldowncmark

import kotlin.test.Test
import kotlin.test.assertNotNull

class ErrorsTest {
    private fun parse(md: String) {
        val parser = Parser(md)
        for (item in parser) {
            assertNotNull(item)
        }
    }

    private fun parseAllOptions(md: String) {
        val parser = Parser(md, Options.ALL)
        for (item in parser) {
            assertNotNull(item)
        }
    }

    @Test
    fun testListsInsideCodeSpans() {
        parse("- `\nx\n**\n  *\n  `")
    }

    @Test
    fun testFuzzerInput1() {
        parse(">\n >>><N\n")
    }

    @Test
    fun testFuzzerInput2() {
        parse(" \u000B\\\r- ")
    }

    @Test
    fun testFuzzerInput3() {
        parseAllOptions("\n # #\r\u001C ")
    }

    @Test
    fun testFuzzerInput4() {
        parseAllOptions("\u0000{\tϐ}\n-")
    }

    @Test
    fun testFuzzerInput5() {
        parseAllOptions(" \u000C{}\n-\n")
    }

    @Test
    fun testFuzzerInput6() {
        parse("*\t[][\n\t<p]>\n\t[]")
    }

    @Test
    fun testFuzzerInput7() {
        parseAllOptions("[][{]}\n-")
    }

    @Test
    fun testFuzzerInput8() {
        parseAllOptions("a\n \u000C{}\n-")
    }

    @Test
    fun testFuzzerInput9() {
        parseAllOptions("a\n \u000C{}\\\n-")
    }

    @Test
    fun testFuzzerInput10() {
        parseAllOptions(
            "[[    \t\n   \u000C\u000C\u000C\u000C\u000C    {}\n-\r\u000E\u0000\u0000{# }\n" +
                "\u000B\u000B\u000B\u000B\u000B\u000B\u000B\u000B\u000B\u000B\u0000\u0000",
        )
    }

    @Test
    fun testFuzzerInput11() {
        parseAllOptions(
            "[[\u000C\u000C   \t\n   \u000C\u000C\u000C\u000C\u000C\u000C\u000C\u000C\u000C       {}\n-\r\u000E",
        )
    }

    @Test
    fun testFuzzerInput12() {
        parseAllOptions("\u000C-\n\u000C\n-")
    }

    @Test
    fun testFuzzerInput13() {
        // Does not fail with Options::all():
        val parser =
            Parser(
                ".\r> ^](\r\u000C\r\u0000\u0000\r.\r[^\u0000\u0000\\\u0000\u0000\u0000^^^^^]",
                Options.ENABLE_FOOTNOTES,
            )
        assertNotNull(parser)
    }

    @Test
    fun testWrongCodeBlock() {
        parse("```\n * ```\n ")
    }

    @Test
    fun testUnterminatedLink() {
        parse("[](\\")
    }

    @Test
    fun testUnterminatedAutolink() {
        parse("<a")
    }

    @Test
    fun testInfiniteLoop() {
        parse("[<!W\n\\\n")
    }

    @Test
    fun testHtmlTag() {
        parse("<script\uFEFF")
    }

    @Test
    fun testBadSliceA() {
        parse("><a\n")
    }

    @Test
    fun testBadSliceB() {
        parse("><a a\n")
    }

    @Test
    fun testBadSliceUnicode() {
        parse("><a a=\n毿>")
    }
}
