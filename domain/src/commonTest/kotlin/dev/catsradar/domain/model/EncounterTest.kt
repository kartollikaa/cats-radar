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

    // NOTIFICATION is this project's addition to the spec's four: walking mode logs a cat from the
    // lock screen, which is neither the app nor the widget. Safe to add because the Room converter
    // maps an unrecognised name back to APP, so an older build reading a newer row degrades rather
    // than throwing.
    @Test
    fun `EncounterOrigin has exactly five entries in spec order`() {
        assertEquals(
            listOf(
                EncounterOrigin.APP,
                EncounterOrigin.WIDGET,
                EncounterOrigin.NOTIFICATION,
                EncounterOrigin.CAMERA,
                EncounterOrigin.GALLERY,
            ),
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
