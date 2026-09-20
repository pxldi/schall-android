package io.github.pxldi.schall.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FormatTest {
    @Test
    fun bytes_are_said_in_the_largest_unit_that_fits() {
        assertEquals("", formatBytes(null))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("42.0 MB", formatBytes(42L * 1024 * 1024))
    }

    @Test
    fun durations_are_minutes_and_seconds_with_hours_only_when_there_are_any() {
        assertEquals("", formatDuration(null))
        assertEquals("3:07", formatDuration(187_000))
        assertEquals("1:00:05", formatDuration(3_605_000))
    }

    @Test
    fun a_moment_is_said_relative_to_now_in_the_coarsest_unit_that_still_says_something() {
        val now = Instant.parse("2026-09-20T12:00:00Z")
        assertEquals("in 5 min", relative("2026-09-20T12:05:00Z", now))
        assertEquals("2 h ago", relative("2026-09-20T10:00:00Z", now))
        assertEquals("in 3 d", relative("2026-09-23T12:00:00Z", now))
        assertEquals("", relative("not a date", now))
    }
}
