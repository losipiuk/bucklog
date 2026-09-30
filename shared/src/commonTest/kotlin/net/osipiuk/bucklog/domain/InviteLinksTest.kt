package net.osipiuk.bucklog.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InviteLinksTest {
    private val invite = Invite("1ExampleSheetId_abcdefghijklmnopqrstuvwxyz0", "Wydatki & co", "Łukasz")

    @Test
    fun pageLinkKeepsDetailsInTheFragment() {
        val link = InviteLinks.page(invite)
        assertTrue(link.startsWith("https://losipiuk.github.io/bucklog-picker/join.html#sheet="), link)
        assertEquals(invite, InviteLinks.parse(link))
    }

    @Test
    fun appLinkRoundTrips() {
        val link = InviteLinks.app(invite)
        assertTrue(link.startsWith("net.osipiuk.bucklog://join?"), link)
        assertEquals(invite, InviteLinks.parse(link))
    }

    @Test
    fun rejectsLinksWithoutAValidSheet() {
        assertNull(InviteLinks.parse("https://x/join.html#name=a"))
        assertNull(InviteLinks.parse("net.osipiuk.bucklog://join?sheet=../../etc"))
    }
}
