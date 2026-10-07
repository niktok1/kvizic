package io.ntole.kvizic.share

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A room's link: made from its code, read back, and nothing else read as one. */
class InviteLinkTest {
    @AfterTest
    fun tearDown() {
        InviteLink.code.value?.let(InviteLink::take)
    }

    @Test
    fun `a room's link names its code on the site`() {
        assertEquals("https://kvizic.ntole.com/j/482915", InviteLink.of("482915"))
        assertEquals("482915", InviteLink.codeOf(InviteLink.of("482915")))
        assertEquals("482915", InviteLink.codeOf("https://kvizic.ntole.com/j/482915/"))
    }

    @Test
    fun `nothing but a room's link is read as one`() {
        listOf(
            "https://kvizic.ntole.com/j/48291",
            "https://kvizic.ntole.com/j/4829150",
            "https://kvizic.ntole.com/j/abcdef",
            "https://kvizic.ntole.com/privacy.html",
            "https://kvizic.ntole.com.evil.example/j/482915",
            "https://evil.example/j/482915",
            "https://kvizicXntole.com/j/482915",
        ).forEach { assertNull(InviteLink.codeOf(it), it) }
    }

    @Test
    fun `a link opened is the app's to take once`() {
        InviteLink.opened("https://example.com/")
        assertNull(InviteLink.code.value)
        InviteLink.opened(InviteLink.of("482915"))
        assertEquals("482915", InviteLink.code.value)
        InviteLink.take("111111")
        assertEquals("482915", InviteLink.code.value, "another code's taking leaves it")
        InviteLink.take("482915")
        assertNull(InviteLink.code.value)
    }
}
