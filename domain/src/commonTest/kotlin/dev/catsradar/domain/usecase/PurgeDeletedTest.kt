package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.RecordingPhotoStorage
import dev.catsradar.domain.testing.encounterFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class PurgeDeletedTest {

    private val repository = FakeEncounterRepository()
    private val photos = RecordingPhotoStorage()

    private fun purge() = PurgeDeleted(repository, photos, FakeClock(NOW))

    @Test
    fun `an encounter deleted long enough ago goes, with its photo files`() = runTest {
        repository.insert(
            encounterFixture("old", OCCURRED)
                .copy(
                    deletedAt = NOW - Tuning.PURGE_AFTER - 1.days,
                    photoPath = "old.jpg",
                    thumbPath = "old_thumb.jpg"
                ),
        )

        purge()()

        assertEquals(listOf("old.jpg", "old_thumb.jpg"), photos.deleted)
        assertEquals(listOf(NOW - Tuning.PURGE_AFTER), repository.purgeCalls)
    }

    @Test
    fun `an encounter deleted recently is left alone, files and all`() = runTest {
        repository.insert(encounterFixture("recent", OCCURRED).copy(deletedAt = NOW - 1.days, photoPath = "r.jpg"))

        purge()()

        assertEquals(emptyList(), photos.deleted)
    }

    @Test
    fun `a live encounter is never touched`() = runTest {
        repository.insert(encounterFixture("live", OCCURRED).copy(photoPath = "live.jpg"))

        purge()()

        assertEquals(emptyList(), photos.deleted)
    }

    @Test
    fun `a tally has no files to delete and is purged all the same`() = runTest {
        repository.insert(encounterFixture("tally", OCCURRED).copy(deletedAt = NOW - Tuning.PURGE_AFTER - 1.days))

        purge()()

        assertEquals(emptyList(), photos.deleted)
        assertEquals(1, repository.purgeCalls.size)
    }

    private companion object {
        val NOW = Instant.parse("2026-09-22T12:00:00Z")
        val OCCURRED = Instant.parse("2026-01-01T12:00:00Z")
    }
}
