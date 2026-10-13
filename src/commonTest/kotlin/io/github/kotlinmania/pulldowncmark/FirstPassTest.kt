// port-lint: tests src/firstpass.rs
package io.github.kotlinmania.pulldowncmark

import kotlin.test.Test
import kotlin.test.assertEquals

class FirstPassTest {
    private fun checkExpectedIndices(text: String, expected: List<Int>, skip: Int) {
        val opts =
            Options.ENABLE_TABLES +
                Options.ENABLE_FOOTNOTES +
                Options.ENABLE_STRIKETHROUGH +
                Options.ENABLE_TASKLISTS
        val lut = createLut(opts)
        val indices = mutableListOf<Int>()
        iterateSpecialChars(lut, text, 0, text.length) { ix, _ ->
            indices.add(ix)
            LoopInstruction.ContinueAndSkip(skip)
        }
        assertEquals(expected, indices)
    }

    @Test
    fun simpleNoMatch() {
        checkExpectedIndices("abcdef0123456789", emptyList(), 0)
    }

    @Test
    fun simpleMatch() {
        checkExpectedIndices("*bcd&f0123456789", listOf(0, 4), 0)
    }

    @Test
    fun singleOpenFish() {
        checkExpectedIndices("<", listOf(0), 0)
    }

    @Test
    fun longMatch() {
        checkExpectedIndices("0123456789abcde~*bcd&f0", listOf(15, 16, 20), 0)
    }

    @Test
    fun borderSkip() {
        checkExpectedIndices("0123456789abcde~~~~d&f0", listOf(15, 20), 3)
    }

    @Test
    fun exhaustiveSearch() {
        val chars = charArrayOf('\n', '\r', '*', '_', '~', '|', '&', '\\', '[', ']', '<', '!', '`')
        for (c in chars) {
            for (i in 0..255) {
                val ch = i.toChar()
                if (ch !in chars) {
                    val buf = CharArray(18) { ch }
                    buf[3] = c
                    buf[6] = c
                    checkExpectedIndices(buf.concatToString(), listOf(3, 6), 0)
                }
            }
        }
    }
}
