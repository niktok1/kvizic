package io.ntole.kvizic.admin

import kotlin.test.Test
import kotlin.test.assertEquals

/** The moderator's times: how long ago, and the date by hand. */
class AdminTextTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun `how long ago is the largest whole unit`() {
        val now = 1_000 * day
        assertEquals("just now", ago(now - 30_000, now))
        assertEquals("just now", ago(now + 5 * minute, now))
        assertEquals("5 min ago", ago(now - 5 * minute, now))
        assertEquals("3 h ago", ago(now - 3 * hour - 20 * minute, now))
        assertEquals("2 d ago", ago(now - 2 * day, now))
        assertEquals("3 mo ago", ago(now - 100 * day, now))
        assertEquals("2 y ago", ago(now - 800 * day, now))
    }

    @Test
    fun `a time is a utc date and the minute`() {
        assertEquals("1970-01-01 00:00", utc(0))
        assertEquals("2000-02-29 12:34", utc(951_827_640_000))
        assertEquals("2026-10-04 21:07", utc(1_791_148_020_000))
        assertEquals("1969-12-31 23:59", utc(-minute))
    }
}
