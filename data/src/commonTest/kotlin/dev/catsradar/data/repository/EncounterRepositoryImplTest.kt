package dev.catsradar.data.repository

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.PlaceCellAssignment
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
        dao.observeAllResult = listOf(distinctEncounter().toRelation())

        assertEquals(listOf(distinctEncounter()), repository.observeAll().first())
    }

    @Test
    fun observeByIdDelegatesAndMaps() = runTest {
        dao.observeByIdResult = distinctEncounter().toRelation()

        assertEquals(distinctEncounter(), repository.observeById("encounter-id-1").first())
        assertEquals("encounter-id-1", dao.observeByIdCall)
    }

    @Test
    fun insertWritesTheCatAndEveryPhotoOfIt() = runTest {
        val cover = distinctEncounter().cover!!
        val second = cover.copy(id = "photo-2", photoPath = "photos/b.jpg")
        val cat = distinctEncounter().copy(photos = listOf(cover, second))

        repository.insert(cat)

        assertEquals(listOf(cat.toEntity()), dao.inserted)
        assertEquals(listOf(cover.toEntity(), second.toEntity()), dao.insertedPhotos)
    }

    @Test
    fun updateWritesOnlyTheCatsOwnRow() = runTest {
        repository.update(distinctEncounter())

        assertEquals(listOf(distinctEncounter().toEntity()), dao.updated)
        assertEquals(emptyList(), dao.insertedPhotos + dao.addedPhotos)
    }

    @Test
    fun addPhotosHandsEveryPhotoToTheDao() = runTest {
        val first = distinctEncounter().cover!!
        val second = first.copy(id = "photo-2", encounterId = "encounter-id-2", photoPath = "photos/b.jpg")

        repository.addPhotos(listOf(first, second))

        assertEquals(listOf(first.toEntity(), second.toEntity()), dao.addedPhotos)
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
    fun attachLocationReportsWhetherTheDaoChangedTheRow() = runTest {
        val stamp = distinctLocationStamp()

        assertEquals(true, repository.attachLocation("id-1", stamp))

        dao.attachLocationRows = 0
        assertEquals(false, repository.attachLocation("id-1", stamp))
    }

    @Test
    fun addPhotoHandsThePhotoOverStampedWithItsOwnTimeAndReportsWhetherItWasWritten() = runTest {
        val photo = distinctEncounter().cover!!

        assertEquals(true, repository.addPhoto(photo))
        assertEquals(AddPhotoCall(photo.toEntity(), photo.addedAt), dao.addPhotoCall)

        dao.addPhotoResult = false
        assertEquals(false, repository.addPhoto(photo))
    }

    @Test
    fun setCoatForwardsToTheDao() = runTest {
        val updatedAt = Instant.parse("2026-02-01T00:00:00Z")

        repository.setCoat("id-1", CatCoat.BLACK, updatedAt)

        assertEquals(SetCoatCall("id-1", CatCoat.BLACK, updatedAt), dao.setCoatCall)
    }

    @Test
    fun setPlaceCellsForwardsEveryAssignmentToTheDao() = runTest {
        val assignments = listOf(
            PlaceCellAssignment("id-1", lat = 41.39864, lon = 2.17842, geohash = "sp3e986k", placeCellId = "sp3e98"),
            PlaceCellAssignment("id-2", lat = 48.8584, lon = 2.2945, geohash = "u09tunqu", placeCellId = "u09tun"),
        )

        repository.setPlaceCells(assignments)

        assertEquals(assignments, dao.setPlaceCellCalls)
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
        dao.findBySourceDigestResult = distinctEncounter().toRelation()

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
