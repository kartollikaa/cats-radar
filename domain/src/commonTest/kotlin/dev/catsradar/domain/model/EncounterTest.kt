package dev.catsradar.domain.model

import dev.catsradar.domain.testing.encounterAt
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class EncounterTest {
    @Test
    fun `accepts the widest valid UTC offsets`() {
        encounterAt(BASE, tzOffsetMinutes = 18 * 60)
        encounterAt(BASE, tzOffsetMinutes = -18 * 60)
    }

    @Test
    fun `rejects a tzOffsetMinutes beyond plus-or-minus 18 hours`() {
        assertFailsWith<IllegalArgumentException> { encounterAt(BASE, tzOffsetMinutes = 18 * 60 + 1) }
        assertFailsWith<IllegalArgumentException> { encounterAt(BASE, tzOffsetMinutes = -(18 * 60 + 1)) }
    }

    private companion object {
        val BASE = Instant.parse("2026-09-21T10:00:00Z")
    }
}
