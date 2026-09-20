package io.github.pxldi.schall.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiTest {
    @Test
    fun a_server_typed_without_a_scheme_gets_https_and_loses_its_trailing_slash() {
        assertEquals("https://schall.example", normaliseServer("schall.example/"))
        assertEquals("http://10.0.0.5:8080", normaliseServer(" http://10.0.0.5:8080// "))
        assertEquals("", normaliseServer("  "))
    }

    @Test
    fun a_review_row_parses_with_the_fields_a_screen_reads_and_ignores_the_rest() {
        val item = json.decodeFromString<ReviewItem>(
            """
            {"kind":"copies","target":{"id":"t","artist":"A","title":"T","album":"","durationMs":null,"isrc":null,
             "recordingId":"r","status":"awaiting_review","summary":"","attempts":2,"anchorAttempts":1,"unknownField":true},
             "copies":[{"id":"c","provider":"slskd","username":"u","path":"p","name":"n","verdict":"held","decidedBy":"schall",
             "summary":"s","decidedAt":"2026-09-20T00:00:00Z","evidence":{"name":"n","agrees":["title"],"differs":[],"problems":[],"bitRate":320}}],
             "candidates":[],"ruledOut":3}
            """.trimIndent(),
        )
        assertEquals("T", item.target.title)
        assertNull(item.target.durationMs)
        assertEquals(3, item.ruledOut)
        assertEquals(320, item.copies.single().evidence?.bitRate)
        assertNull(item.copyGroups)
    }

    @Test
    fun the_query_encoding_keeps_a_space_as_percent_twenty() {
        assertEquals("Massive%20Attack", enc("Massive Attack"))
    }
}
