package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.PhotoStamp
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.RecordingAnalytics
import dev.catsradar.domain.testing.encounterFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SetCoatTest {

    private val encounters = FakeEncounterRepository()
    private val setCoat = SetCoat(encounters, FakeClock(NOW), analytics = RecordingAnalytics())

    private val tally: Encounter = encounterFixture(ID, OCCURRED)

    private suspend fun stored(): Encounter = encounters.loadEvery().first { it.id == ID }

    @Test
    fun `setting a coat stamps it and nothing else`() = runTest {
        encounters.insert(tally)

        setCoat(ID, CatCoat.GINGER)

        assertEquals(tally.copy(coat = CatCoat.GINGER, updatedAt = NOW), stored())
    }

    @Test
    fun `setting the coat that is already set writes nothing`() = runTest {
        val alreadyGinger = tally.copy(coat = CatCoat.GINGER)
        encounters.insert(alreadyGinger)

        setCoat(ID, CatCoat.GINGER)

        assertEquals(alreadyGinger, stored())
    }

    @Test
    fun `a soft-deleted cat is not edited and not brought back`() = runTest {
        val deleted = tally.copy(deletedAt = NOW)
        encounters.insert(deleted)

        setCoat(ID, CatCoat.GINGER)

        assertEquals(deleted, stored())
    }

    @Test
    fun `a photo attached between the read and the write survives the coat`() = runTest {
        encounters.insert(tally)
        val stamp = PhotoStamp(
            photoPath = "p.jpg",
            thumbPath = "p_thumb.jpg",
            galleryUri = null,
            sourceMediaUri = null,
            sourceDigest = "sha",
            updatedAt = NOW - 1.minutes,
        )
        encounters.beforeSetCoat = { encounters.attachPhoto(ID, stamp) }

        setCoat(ID, CatCoat.GINGER)

        assertEquals(
            tally.copy(
                coat = CatCoat.GINGER,
                photoPath = stamp.photoPath,
                thumbPath = stamp.thumbPath,
                sourceDigest = stamp.sourceDigest,
                updatedAt = NOW,
            ),
            stored(),
        )
    }

    private companion object {
        const val ID = "cat-1"
        val OCCURRED = Instant.parse("2026-09-21T10:00:00Z")
        val NOW = Instant.parse("2026-09-23T12:00:00Z")
    }
}
