// port-lint: tests src/puncttable.rs
package io.github.kotlinmania.pulldowncmark

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PunctTableTest {
    @Test
    fun testAscii() {
        assertTrue(isAsciiPunctuation('!'.code))
        assertTrue(isAsciiPunctuation('@'.code))
        assertTrue(isAsciiPunctuation('~'.code))
        assertFalse(isAsciiPunctuation(' '.code))
        assertFalse(isAsciiPunctuation('0'.code))
        assertFalse(isAsciiPunctuation('A'.code))
        assertFalse(isAsciiPunctuation(0xA1))
    }

    @Test
    fun testUnicode() {
        assertTrue(isPunctuation('~'))
        assertFalse(isPunctuation(' '))

        assertTrue(isPunctuation('\u00A1'))
        assertTrue(isPunctuation('\u060C'))
        assertTrue(isPunctuation('\uFF65'))
        assertTrue(isPunctuation(0x1BC9F))
        assertFalse(isPunctuation(0x1BCA0))
    }
}
