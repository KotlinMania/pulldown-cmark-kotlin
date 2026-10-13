// port-lint: tests src/linklabel.rs
package io.github.kotlinmania.pulldowncmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LinkLabelTest {
    @Test
    fun whitespaceNormalization() {
        val input = "«\t\tBlurry Eyes\t\t»][blurry_eyes]"
        val expectedOutput = "« Blurry Eyes »"

        val result = scanLinkLabelRest(input, { null }, false)
        assertNotNull(result)
        val (_, normalizedLabel) = result
        assertEquals(expectedOutput, normalizedLabel.value)
    }

    @Test
    fun returnCarriageLinefeedOk() {
        val input = "hello\r\nworld\r\n]"
        val result = scanLinkLabelRest(input, { 0 }, false)
        assertNotNull(result)
    }
}
