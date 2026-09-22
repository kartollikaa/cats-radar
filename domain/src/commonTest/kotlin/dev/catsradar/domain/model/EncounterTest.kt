package dev.catsradar.domain.model

import dev.catsradar.domain.testing.encounterAt
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `EncounterKind has exactly two entries in spec order`() {
        assertEquals(listOf(EncounterKind.TALLY, EncounterKind.PHOTO), EncounterKind.entries)
    }

    @Test
    fun `EncounterOrigin has exactly four entries in spec order`() {
        assertEquals(
            listOf(EncounterOrigin.APP, EncounterOrigin.WIDGET, EncounterOrigin.CAMERA, EncounterOrigin.GALLERY),
            EncounterOrigin.entries,
        )
    }

    @Test
    fun `LocationSource has exactly five entries in spec order`() {
        assertEquals(
            listOf(
                LocationSource.EXIF,
                LocationSource.CURRENT_FIX,
                LocationSource.LAST_KNOWN,
                LocationSource.BACKFILLED,
                LocationSource.NONE,
            ),
            LocationSource.entries,
        )
    }

    private companion object {
        val BASE = Instant.parse("2026-09-21T10:00:00Z")
    }
}
