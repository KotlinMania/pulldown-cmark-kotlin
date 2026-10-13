// port-lint: tests src/scanners.rs
package io.github.kotlinmania.pulldowncmark

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScannersTest {
    @Test
    fun overflowList() {
        assertNull(
            scanListItem("4444444444444444444444444444444444444444444444444444444444!", 0),
        )
    }

    @Test
    fun overflowByAddition() {
        assertNull(scanListItem("1844674407370955161615!", 0))
    }

    @Test
    fun goodEmails() {
        val emails =
            listOf(
                "<a@b.c>",
                "<a@b>",
                "<a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-@example.com>",
                "<a@sixty-three-letters-in-this-identifier-----------------------63>",
            )
        for (email in emails) {
            assertNotNull(scanEmail(email, 1))
        }
    }

    @Test
    fun badEmails() {
        val emails =
            listOf(
                "<@b.c>",
                "<foo@-example.com>",
                "<foo@example-.com>",
                "<a@notrailingperiod.>",
                "<a(noparens)@example.com>",
                "<\"noquotes\"@example.com>",
                "<a@sixty-four-letters-in-this-identifier-------------------------64>",
            )
        for (email in emails) {
            assertNull(scanEmail(email, 1))
        }
    }
}
