package dev.catsradar.data.repository

import dev.catsradar.domain.model.PhotoStamp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class EncounterRepositoryImplTest {
    private val dao = FakeEncounterDao()
    private val repository = EncounterRepositoryImpl(dao)

    @Test
    fun observeAllMapsEntitiesToDomain() = runTest {
        dao.observeAllResult = listOf(distinctEncounter().toEntity())

        assertEquals(listOf(distinctEncounter()), repository.observeAll().first())
    }

    @Test
    fun observeByIdDelegatesAndMaps() = runTest {
        dao.observeByIdResult = distinctEncounter().toEntity()

        assertEquals(distinctEncounter(), repository.observeById("encounter-id-1").first())
        assertEquals("encounter-id-1", dao.observeByIdCall)
    }

    @Test
    fun insertMapsDomainToEntity() = runTest {
        repository.insert(distinctEncounter())

        assertEquals(listOf(distinctEncounter().toEntity()), dao.inserted)
    }

    @Test
    fun updateMapsDomainToEntity() = runTest {
        repository.update(distinctEncounter())

        assertEquals(listOf(distinctEncounter().toEntity()), dao.updated)
    }

    @Test
    fun attachLocationForwardsEveryStampFieldToTheDao() = runTest {
        val stamp = distinctLocationStamp()

        repository.attachLocation("id-1", stamp)

        assertEquals(
            AttachLocationCall(
                id = "id-1",
                lat = stamp.lat,
                lon = stamp.lon,
                accuracyMeters = stamp.accuracyMeters,
                locationSource = stamp.locationSource,
                locationFixedAt = stamp.locationFixedAt,
                geohash = stamp.geohash,
                placeCellId = stamp.placeCellId,
                updatedAt = stamp.updatedAt,
            ),
            dao.attachLocationCall,
        )
    }

    @Test
    fun attachPhotoForwardsEveryStampFieldAndReportsWhetherARowWasWritten() = runTest {
        val stamp = PhotoStamp(
            photoPath = "p.jpg",
            thumbPath = "p_thumb.jpg",
            galleryUri = "content://gallery/7",
            sourceDigest = "sha",
            updatedAt = Instant.parse("2026-02-01T00:00:00Z"),
        )

        assertEquals(true, repository.attachPhoto("id-1", stamp))
        assertEquals(
            AttachPhotoCall("id-1", "p.jpg", "p_thumb.jpg", "content://gallery/7", "sha", stamp.updatedAt),
            dao.attachPhotoCall,
        )

        dao.attachPhotoResult = 0
        assertEquals(false, repository.attachPhoto("id-1", stamp))
    }

    @Test
    fun softDeleteDelegatesWithTheSameArguments() = runTest {
        val deletedAt = Instant.parse("2026-02-01T00:00:00Z")

        repository.softDelete("id-1", deletedAt)

        assertEquals("id-1" to deletedAt, dao.softDeleteCall)
    }

    @Test
    fun undoDeleteDelegatesToClearDeletedAt() = runTest {
        repository.undoDelete("id-1")

        assertEquals("id-1", dao.clearDeletedAtCall)
    }

    @Test
    fun undoDeleteAllClearsOnlyRowsDeletedAtTheBatchInstant() = runTest {
        val deletedAt = Instant.parse("2026-02-01T00:00:00Z")

        repository.undoDeleteAll(listOf("id-1", "id-2"), deletedAt)

        assertEquals(listOf("id-1" to deletedAt, "id-2" to deletedAt), dao.clearDeletedAtIfDeletedAtCalls)
        assertEquals(null, dao.clearDeletedAtCall)
    }

    @Test
    fun findBySourceDigestDelegatesAndMaps() = runTest {
        dao.findBySourceDigestResult = distinctEncounter().toEntity()

        assertEquals(distinctEncounter(), repository.findBySourceDigest("digest-1"))
        assertEquals("digest-1", dao.findBySourceDigestCall)
    }

    @Test
    fun purgeDeletedBeforeDelegatesAndReturnsTheCount() = runTest {
        val cutoff = Instant.parse("2026-02-01T00:00:00Z")
        dao.purgeDeletedBeforeResult = 3

        assertEquals(3, repository.purgeDeletedBefore(cutoff))
        assertEquals(cutoff, dao.purgeDeletedBeforeCall)
    }
}
