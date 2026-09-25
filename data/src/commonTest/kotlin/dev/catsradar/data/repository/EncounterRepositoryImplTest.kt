package dev.catsradar.data.repository

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.EncounterPhoto
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
        dao.loadByIdResult = distinctEncounter().toEntity()

        repository.update(distinctEncounter())

        assertEquals(listOf(distinctEncounter().toEntity()), dao.updated)
    }

    @Test
    fun updateKeepsThePhotoTheRowHasWhateverTheCatCarries() = runTest {
        val here = distinctEncounter().toEntity()
        dao.loadByIdResult = here
        val offered = distinctEncounter().copy(photos = emptyList(), updatedAt = Instant.parse("2026-03-01T00:00:00Z"))

        repository.update(offered)

        assertEquals(
            listOf(
                offered.toEntity().copy(
                    photoPath = here.photoPath,
                    thumbPath = here.thumbPath,
                    galleryUri = here.galleryUri,
                    sourceMediaUri = here.sourceMediaUri,
                    sourceDigest = here.sourceDigest,
                )
            ),
            dao.updated,
        )
    }

    @Test
    fun updateOfARowThatIsGoneWritesNothing() = runTest {
        repository.update(distinctEncounter())

        assertEquals(emptyList(), dao.updated)
    }

    @Test
    fun addPhotosRestoresEachPhotoOntoItsCatWithoutTouchingUpdatedAt() = runTest {
        val first = distinctEncounter().cover!!
        val second = first.copy(id = "encounter-id-2", encounterId = "encounter-id-2", photoPath = "photos/b.jpg")

        repository.addPhotos(listOf(first, second))

        assertEquals(
            listOf(
                RestorePhotoCall(
                    "encounter-id-1",
                    first.photoPath,
                    first.thumbPath,
                    first.galleryUri,
                    first.sourceMediaUri,
                    first.sourceDigest,
                ),
                RestorePhotoCall(
                    "encounter-id-2",
                    "photos/b.jpg",
                    first.thumbPath,
                    first.galleryUri,
                    first.sourceMediaUri,
                    first.sourceDigest,
                ),
            ),
            dao.restorePhotoCalls,
        )
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
    fun addPhotoForwardsEveryPhotoFieldToItsCatAndReportsWhetherARowWasWritten() = runTest {
        val photo = EncounterPhoto(
            id = "id-1",
            encounterId = "id-1",
            photoPath = "p.jpg",
            thumbPath = "p_thumb.jpg",
            galleryUri = "content://gallery/7",
            sourceMediaUri = "content://media/external/images/media/7",
            sourceDigest = "sha",
            deviceId = "device-1",
            addedAt = Instant.parse("2026-02-01T00:00:00Z"),
        )

        assertEquals(true, repository.addPhoto(photo))
        assertEquals(
            AttachPhotoCall(
                "id-1",
                "p.jpg",
                "p_thumb.jpg",
                "content://gallery/7",
                "content://media/external/images/media/7",
                "sha",
                photo.addedAt,
            ),
            dao.attachPhotoCall,
        )

        dao.attachPhotoResult = 0
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
