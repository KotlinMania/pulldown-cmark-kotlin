// port-lint: tests src/strings.rs
package io.github.kotlinmania.pulldowncmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StringsTest {
    @Test
    fun inlinestrAscii() {
        val s = InlineStr.fromChar('a')
        assertEquals("a", s.value)
    }

    @Test
    fun inlinestrUnicode() {
        val s = InlineStr.tryFrom("🍔")
        assertNotNull(s)
        assertEquals("🍔", s.value)
    }

    // cowstr_size omitted: Rust memory layout semantic (size_of::<CowStr>()) does not apply to Kotlin objects

    @Test
    fun cowstrCharToString() {
        val c = '藏'
        val smort = CowStr.from(c)
        val owned = smort.toString()
        val expected = "藏"
        assertEquals(expected, owned)
    }

    @Test
    fun inlinestrFitsTwentytwo() {
        val s = "0123456789abcdefghijkl"
        val stackStr = InlineStr.tryFrom(s)
        assertNotNull(stackStr)
        assertEquals(s, stackStr.value)
    }

    @Test
    fun inlinestrNotFitsTwentythree() {
        val s = "0123456789abcdefghijklm"
        val stackStr = InlineStr.tryFrom(s)
        assertNull(stackStr)
    }

    @Test
    fun cowToCowStr() {
        val s = "some text"
        val cowStr = CowStr.borrowed(s)
        assertEquals(s, cowStr.value)

        val boxed = CowStr.boxed(s)
        assertEquals(s, boxed.value)
    }
}
